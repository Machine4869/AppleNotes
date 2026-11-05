/*
 * Copyright (C) 2022-2025 - Peter Korf <peter@niendo.de>
 * Copyright (C)           - kwhitefoot
 * and Contributors.
 *
 * This file is part of ImapNotes3.
 *
 * ImapNotes3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.niendo.ImapNotes3.Miscs;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.SyncRequest;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.net.TrafficStats;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;

import androidx.annotation.NonNull;

import de.niendo.ImapNotes3.AccountConfigurationActivity;
import de.niendo.ImapNotes3.Data.ConfigurationFieldNames;
import de.niendo.ImapNotes3.Data.ImapNotesAccount;
import de.niendo.ImapNotes3.R;
import de.niendo.ImapNotes3.Data.NotesDb;
import de.niendo.ImapNotes3.ImapNotes3;
import de.niendo.ImapNotes3.Sync.SyncUtils;

public class LoginThread extends AsyncTask<Void, Void, Result<String>> {
    private static final String TAG = "LoginThread";
    private static final int THREAD_ID = 0xF00D;
    private final de.niendo.ImapNotes3.Data.ImapNotesAccount ImapNotesAccount;

    private final AccountConfigurationActivity accountConfigurationActivity;

    private final AccountConfigurationActivity.Actions action;
    private final FinishListener listener;

    public LoginThread(ImapNotesAccount ImapNotesAccount,
                       AccountConfigurationActivity accountConfigurationActivity,
                       AccountConfigurationActivity.Actions action) {
        this.ImapNotesAccount = ImapNotesAccount;
        this.listener = accountConfigurationActivity;
        this.accountConfigurationActivity = accountConfigurationActivity;
        this.action = action;
    }

    @NonNull
    protected Result<String> doInBackground(Void... none) {
        Log.d(TAG, "doInBackground");
        boolean newFolder = false;
        try {
            SyncUtils syncUtils = new SyncUtils();
            ImapNotesResult res = syncUtils.ConnectToRemote(
                    ImapNotesAccount.username,
                    ImapNotesAccount.password,
                    ImapNotesAccount.server,
                    ImapNotesAccount.portnum,
                    ImapNotesAccount.security,
                    ImapNotesAccount.GetImapFolder(),
                    ImapNotesAccount.GetCopyImapFolderName(),
                    THREAD_ID
            );
            if (res.returnCode == ImapNotesResult.ResultCodeImapFolderCreated) {
                newFolder = true;
            } else if (res.returnCode != ImapNotesResult.ResultCodeSuccess) {
                Log.d(TAG, "doInBackground IMAP Failed");
                return new Result<>(res.errorMessage, false);
            }

            final AccountManager am = AccountManager.get(accountConfigurationActivity);
            String newName = ImapNotesAccount.accountName;
            String oldName = accountConfigurationActivity.getOriginalAccountName();
            if (action == AccountConfigurationActivity.Actions.EDIT_ACCOUNT
                    && oldName != null && !oldName.isEmpty() && !oldName.equals(newName)) {
                final Account oldAccount = new Account(oldName, Utilities.PackageName);
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                am.renameAccount(oldAccount, newName,
                        future -> latch.countDown(), new Handler(Looper.getMainLooper()));
                try {
                    if (!latch.await(6, java.util.concurrent.TimeUnit.SECONDS)) {
                        return new Result<>("账户重命名超时", false);
                    }
                } catch (InterruptedException e) {
                    return new Result<>("账户重命名失败", false);
                }
                File oldDir = ImapNotes3.GetAccountDir(oldName);
                File newDir = ImapNotes3.GetAccountDir(newName);
                if (oldDir.exists() && !newDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    oldDir.renameTo(newDir);
                }
                NotesDb.getInstance(accountConfigurationActivity).RenameAccount(oldName, newName);
                File oldPrefs = new File(ImapNotes3.GetSharedPrefsDir(),
                        ImapNotes3.RemoveReservedChars(oldName) + ".xml");
                File newPrefs = new File(ImapNotes3.GetSharedPrefsDir(),
                        ImapNotes3.RemoveReservedChars(newName) + ".xml");
                if (oldPrefs.exists() && !newPrefs.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    oldPrefs.renameTo(newPrefs);
                }
            }
            final Account account = new Account(newName, Utilities.PackageName);
            accountConfigurationActivity.setResult(AccountConfigurationActivity.TO_REFRESH);
            if (newFolder) {
                // Database and folder not valid..get Data from new directory
                SyncUtils.SetUIDValidity(account, -1L, accountConfigurationActivity);
            }
            int resultTxtId;
            if (action == AccountConfigurationActivity.Actions.EDIT_ACCOUNT) {
                resultTxtId = R.string.account_modified;
            } else {
                if (!am.addAccountExplicitly(account, ImapNotesAccount.password, null)) {
                    return new Result<>(accountConfigurationActivity.getString(R.string.account_already_exists_or_is_null), false);
                }
                resultTxtId = R.string.account_added;
            }

            final Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
            accountConfigurationActivity.setAccountAuthenticatorResult(result);
            setUserData(am, account);
            // Run the Sync Adapter Periodically
            ContentResolver.setIsSyncable(account, AccountConfigurationActivity.AUTHORITY, 1);
            ContentResolver.setSyncAutomatically(account, AccountConfigurationActivity.AUTHORITY,
                    ImapNotesAccount.syncInterval.time > 0);
            // Always remove the previous periodic schedule first, otherwise changing
            // 6 hours -> manual leaves the old 6-hour job running.
            ContentResolver.removePeriodicSync(account, AccountConfigurationActivity.AUTHORITY,
                    Bundle.EMPTY);
            if (ImapNotesAccount.syncInterval.time > 0) {
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(ImapNotesAccount.syncInterval.time * 60L,
                                ImapNotesAccount.syncInterval.time * 60L)
                        .setSyncAdapter(account, AccountConfigurationActivity.AUTHORITY)
                        .setExtras(new Bundle())
                        .build();
                ContentResolver.requestSync(request);
            }
            return new Result<>(accountConfigurationActivity.getString(resultTxtId), true);
        } catch (Exception e) {
            Log.e(TAG, "doInBackground failed", e);
            return new Result<>("Unexpected exception: " + e.getLocalizedMessage(), false);
        }
    }

    private void setUserData(@NonNull AccountManager am,
                             @NonNull Account account) {
        am.setUserData(account, ConfigurationFieldNames.UserName, ImapNotesAccount.username);
        am.setUserData(account, ConfigurationFieldNames.Server, ImapNotesAccount.server);
        am.setUserData(account, ConfigurationFieldNames.PortNumber, ImapNotesAccount.portnum);
        am.setUserData(account, ConfigurationFieldNames.SyncInterval, ImapNotesAccount.syncInterval.name());
        am.setUserData(account, ConfigurationFieldNames.Security, ImapNotesAccount.security.name());
        am.setUserData(account, ConfigurationFieldNames.ImapFolder, ImapNotesAccount.GetImapFolder());
        am.setUserData(account, ConfigurationFieldNames.copyImapFolderName, ImapNotesAccount.copyImapFolderName);
        am.setUserData(account, ConfigurationFieldNames.copyImapFolder, ImapNotesAccount.copyImapFolder);
    }

    protected void onPostExecute(@NonNull Result<String> result) {
        TrafficStats.clearThreadStatsTag();
        listener.onFinishPerformed(result);
    }

    public interface FinishListener {
        void onFinishPerformed(@NonNull Result<String> result);
    }

}

