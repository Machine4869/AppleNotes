/*
 * Copyright (C) 2022-2026 - Peter Korf <peter@niendo.de>
 * Copyright (C)      2023 - woheller69
 * Copyright (C)         ? - kwhitefoot
 * Copyright (C)      2016 - Martin Carpella
 * Copyright (C) 2014-2015 - c0238
 * Copyright (C) 2014-2015 - nb
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

package de.niendo.ImapNotes3;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.SyncStatusObserver;
import android.content.res.ColorStateList;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;

import android.os.Handler;
import android.os.SystemClock;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.niendo.ImapNotes3.Data.NotesDb;
import de.niendo.ImapNotes3.Data.OneNote;
import de.niendo.ImapNotes3.Miscs.BackupRestore;
import de.niendo.ImapNotes3.Miscs.HtmlNote;
import de.niendo.ImapNotes3.Miscs.SyncThread;
import de.niendo.ImapNotes3.Miscs.UpdateThread;
import de.niendo.ImapNotes3.Miscs.Utilities;
import de.niendo.ImapNotes3.Miscs.ZipUtils;
import de.niendo.ImapNotes3.Sync.SyncUtils;
import eltos.simpledialogfragment.SimpleDialog;
import eltos.simpledialogfragment.list.SimpleListDialog;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ListActivity extends AppCompatActivity implements BackupRestore.INotesRestore, OnItemSelectedListener, Filterable, SimpleDialog.OnDialogResultListener, UpdateThread.FinishListener {
    private static final int SEE_DETAIL = 2;
    public static final int DELETE_BUTTON = 3;
    private static final int NEW_BUTTON = 4;
    private static final int EDIT_ACCOUNT = 5;
    public static final int EDIT_BUTTON = 6;
    private static final int ADD_ACCOUNT = 7;
    private static final int SELECT_ARCHIVE_FOR_RESTORE = 8;
    public static final int EDIT_SETTINGS = 9;

    public static final int ResultCodeSuccess = 1;
    public static final int ResultCodeMakeArchive = 2;
    public static final int ResultCodeRestoreArchive = 3;
    public static final int ResultCodeRemoveAccount = 4;
    public static final int ResultCodeNeutral = 0;
    public static final int ResultCodeError = -1;

    public static final String EDIT_ITEM_NUM_IMAP = "EDIT_ITEM_NUM_IMAP";
    public static final String EDIT_ITEM_COLOR = "EDIT_ITEM_COLOR";
    public static final String EDIT_ITEM_ACCOUNTNAME = "EDIT_ITEM_ACCOUNTNAME";
    public static final String SYNCINTERVAL = "SYNCINTERVAL";
    public static final String CHANGED = "CHANGED";
    public static final String SYNCED = "SYNCED";
    public static final String REFRESH_TAGS = "REFRESH_TAGS";
    public static final String SYNCED_ERR_MSG = "SYNCED_ERR_MSG";
    private static final String DELETE_ITEM_NUM_IMAP = "DELETE_ITEM_NUM_IMAP";
    private static final String ACCOUNTSPINNER_POS = "ACCOUNTSPINNER_POS";
    private static final String SORT_BY_DATE = "SORT_BY_DATE";
    private static final String SORT_BY_TITLE = "SORT_BY_TITLE";
    private static final String SORT_BY_COLOR = "SORT_BY_COLOR";
    private static final String DLG_FILTER_HASHTAG = "DLG_FILTER_HASHTAG";

    private Intent intentActionSend;
    private ArrayList<OneNote> noteList;
    private NotesListAdapter listToView;
    private ArrayAdapter<String> spinnerList;
    private static final String AUTHORITY = Utilities.PackageName + ".provider";
    private Spinner accountSpinner;
    private TextView accountLabel;
    private TextView accountAvatar;
    private TextView syncStatus;
    private View syncDot;
    private EditText searchInput;
    private View searchBar;
    private ImageButton searchClear;
    private final SyncStatusObserver syncStatusObserver =
            which -> runOnUiThread(this::onSyncStatusChanged);
    private static AccountManager accountManager;
    @Nullable
    private static NotesDb storedNotes = null;
    private static List<String> accountList;
    private static Menu actionMenu;
    private static CharSequence mFilterString = "";
    static String[] hashFilter;
    private static ArrayList<String> hashFilterSelected = new ArrayList<>();
    private ContentObserver mObserver;
    private ListSwipeRefreshLayout swipeLayout;
    private boolean manualSyncInProgress;
    private long manualSyncStartedAt;
    private final Handler syncHandler = new Handler(Looper.getMainLooper());
    private final Runnable syncTimeout = () -> {
        manualSyncInProgress = false;
        if (swipeLayout != null) swipeLayout.setRefreshing(false);
        updateSyncDot();
    };
    // Ensure that we never have to check for null by initializing reference.
    @NonNull
    private static Account[] accounts = new Account[0];
    private final OnClickListener clickListenerEditAccount = v -> {
        String accountName = getSelectedAccountName();
        if (accountName.isEmpty()) {
            ImapNotes3.ShowMessage(R.string.select_one_account, accountSpinner, 3);
            return;
        }

        Intent res = new Intent(ListActivity.this, AccountConfigurationActivity.class);
        res.putExtra(AccountConfigurationActivity.ACTION, AccountConfigurationActivity.Actions.EDIT_ACCOUNT);
        res.putExtra(AccountConfigurationActivity.ACCOUNTNAME, accountName);
        startActivityForResult(res, ListActivity.EDIT_ACCOUNT);
    };
    private static final String TAG = "IN_Listactivity";
    //@Nullable
    private ListView listview;
    private AsyncTask updateThread;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && searchInput != null
                && searchInput.hasFocus() && searchBar != null) {
            Rect rect = new Rect();
            searchBar.getGlobalVisibleRect(rect);
            if (!rect.contains((int) event.getRawX(), (int) event.getRawY())) {
                int[] listLocation = new int[2];
                listview.getLocationOnScreen(listLocation);
                int position = listview.pointToPosition(
                        (int) event.getRawX() - listLocation[0],
                        (int) event.getRawY() - listLocation[1]);
                if (position == AdapterView.INVALID_POSITION) {
                    // Tap on empty space: cancel search.
                    hideSearch();
                    return true;
                }
                // Tap on a note: close the keyboard and let the ListView handle
                // the click. The query is cleared in onItemClick after the note
                // object has been captured.
                searchInput.clearFocus();
                hideKeyboard();
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (searchInput != null && (searchInput.hasFocus() || searchInput.length() > 0)) {
            hideSearch();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        syncHandler.removeCallbacks(syncTimeout);
        getContentResolver().unregisterContentObserver(mObserver);
        getContentResolver().removeStatusChangeListener(syncStatusObserver);
    }

    public static ArrayList<String> getAccountList() {
        ArrayList<String> accounts = new ArrayList<>();
        for (Account mAccount : ListActivity.accounts) {
            accounts.add(mAccount.name);
        }
        return accounts;
    }

    public static List<String> searchHTMLTags(@NonNull File nameDir, @NonNull String uid, String searchTerm, boolean useRegex) {
        // Compile the regular expression pattern if necessary
        Pattern pattern = null;
        List<String> retVal = new ArrayList<String>();
        if (useRegex) {
            try {
                pattern = Pattern.compile(searchTerm);
            } catch (PatternSyntaxException e) {
                Log.e(TAG, "searchHTMLTags compile failed:", e);
                return retVal;
            }
        } else {
            pattern = Pattern.compile(Pattern.quote(searchTerm), Pattern.CASE_INSENSITIVE);
        }

        String html = Jsoup.parse(HtmlNote.GetNoteFromMessage(SyncUtils.ReadMailFromNoteFile(nameDir, uid)).text).text();

        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            retVal.add(matcher.group());
        }
        return retVal;
    }


    @Override
    public boolean onResult(@NonNull String dialogTag, int which, @NonNull Bundle extras) {
        if (which == BUTTON_NEGATIVE) return false;
        switch (dialogTag) {
            case DLG_FILTER_HASHTAG:
                if (which == BUTTON_POSITIVE) {
                    hashFilterSelected = extras.getStringArrayList(SimpleListDialog.SELECTED_LABELS);
                    if (hashFilterSelected.isEmpty())
                        hashFilter = null;
                    else {
                        hashFilter = new String[hashFilterSelected.size()];
                        hashFilterSelected.toArray(hashFilter);
                    }
                }
                if (which == BUTTON_NEUTRAL) {
                    hashFilter = null;
                    hashFilterSelected.clear();
                }
                RefreshList();
                return true;
        }
        return false;
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent");
        setIntent(intent);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.main);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setHomeButtonEnabled(false);
            getSupportActionBar().setElevation(0);
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getColor(R.color.ActionBgColor)));
        }
        ImapNotes3.styleActionBar(getSupportActionBar(), this);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setBackgroundDrawable(new ColorDrawable(getColor(R.color.main_bg)));
            ImapNotes3.stylePage(this, getColor(R.color.main_bg));
            View crumb = LayoutInflater.from(this).inflate(R.layout.actionbar_main, null);
            actionBar.setCustomView(crumb, new ActionBar.LayoutParams(
                    ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.MATCH_PARENT));
            this.accountLabel = crumb.findViewById(R.id.accountLabel);
            crumb.findViewById(R.id.settingsButton).setOnClickListener(v ->
                    startActivity(new Intent(ListActivity.this, SettingsActivity.class)));
        }

        this.syncDot = findViewById(R.id.syncDot);
        this.syncStatus = findViewById(R.id.syncStatus);
        this.accountSpinner = findViewById(R.id.accountSpinner);
        this.searchInput = findViewById(R.id.searchInput);
        this.searchClear = findViewById(R.id.searchClear);
        this.searchBar = findViewById(R.id.searchBar);
        setupSearch();
        ListActivity.accountList = new ArrayList<>();

        this.accountSpinner.setOnItemSelectedListener(this);
        ImapNotes3.setContent(findViewById(android.R.id.content));

        ListActivity.accountManager = AccountManager.get(getApplicationContext());
        ListActivity.accountManager.addOnAccountsUpdatedListener(
                new AccountsUpdateListener(), null, true);
        getContentResolver().addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE | ContentResolver.SYNC_OBSERVER_TYPE_PENDING,
                syncStatusObserver);

        spinnerList = new ArrayAdapter<>
                (this, R.layout.account_spinner_item, ListActivity.accountList);
        accountSpinner.setAdapter(spinnerList);

        this.noteList = new ArrayList<>();

        this.listToView = new NotesListAdapter(
                this,
                this.noteList,
                new String[]{OneNote.TITLE, OneNote.DATE, OneNote.PREVIEW},
                new int[]{R.id.noteTitle, R.id.noteLastChange, R.id.notePreview},
                OneNote.BGCOLOR);

        listview = findViewById(R.id.notesList);
        listview.setAdapter(this.listToView);
        listview.setEmptyView(findViewById(R.id.emptyView));

        listview.setTextFilterEnabled(true);

        storedNotes = NotesDb.getInstance(getApplicationContext());

        // When item is clicked, we go to PlainNoteActivity
        listview.setOnItemClickListener((parent, widget, selectedNote, rowId) -> {
            Log.d(TAG, "onItemClick");
            Intent toDetail;

            String saveState = storedNotes.GetSaveState(noteList.get(selectedNote).GetUid(), noteList.get(selectedNote).GetAccount());

            if (saveState.equals(OneNote.SAVE_STATE_SAVING)) {
                ImapNotes3.ShowMessage(R.string.save_wait_necessary, listview, 3);
                return;
            } else if (saveState.equals(OneNote.SAVE_STATE_SYNCING)) {
                ImapNotes3.ShowMessage(R.string.sync_wait_necessary, listview, 3);
                return;
            } else if (saveState.equals(OneNote.SAVE_STATE_FAILED)) {
                ImapNotes3.ShowMessage(R.string.save_note_failed, listview, 3);
                Log.i(TAG, "message was not correctly saved");
                return;
            }
            if (intentActionSend != null)
                // FIXME StrictMode policy violation: android.os.strictmode.UnsafeIntentLaunchViolation: Launch of unsafe intent: Intent
                toDetail = intentActionSend;
            else
                toDetail = new Intent(widget.getContext(), PlainNoteActivity.class);
            toDetail.putExtra(PlainNoteActivity.selectedNote, (OneNote) parent.getItemAtPosition(selectedNote));
            toDetail.putExtra(PlainNoteActivity.ActivityType, PlainNoteActivity.ActivityTypeEdit);
            boolean wasSearching = searchInput != null
                    && (searchInput.hasFocus() || searchInput.length() > 0);
            startActivityForResult(toDetail, SEE_DETAIL);
            if (wasSearching) {
                // Reset the filtered list only after the activity transition has
                // covered the list, otherwise the old list flashes for a moment.
                searchInput.postDelayed(this::hideSearch, 500);
            }
            setIntentAsProcessed();
            Log.d(TAG, "onItemClick, back from detail.");
        });

        // Getting SwipeContainerLayout
        swipeLayout = findViewById(R.id.swipeContainer);
        swipeLayout.setListView(listview);
        listview.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(android.widget.AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
                boolean atTop = firstVisibleItem == 0
                        && (view.getChildCount() == 0 || view.getChildAt(0).getTop() >= 0);
                swipeLayout.setEnabled(atTop);
            }
        });
        // Adding Listener
        swipeLayout.setOnRefreshListener(new ListSwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                swipeLayout.setRefreshing(false);
                TriggerSync(true);
            }
        });


        mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            public void onChange(boolean selfChange) {
                Log.d(TAG, "ContentObserver.OnChange");
                swipeLayout.setRefreshing(false);
                manualSyncInProgress = false;
                syncHandler.removeCallbacks(syncTimeout);
                updateSyncDot();
                if (selfChange || ImapNotes3.intent == null) return;
                String accountName = ImapNotes3.intent.getStringExtra(EDIT_ITEM_ACCOUNTNAME);
                boolean isChanged = ImapNotes3.intent.getBooleanExtra(CHANGED, false);
                // boolean isSynced = ImapNotes3.intent.getBooleanExtra(SYNCED, false);
                String errorMessage = ImapNotes3.intent.getStringExtra(SYNCED_ERR_MSG);
                // SyncInterval syncInterval = SyncInterval.from(ImapNotes3.intent.getStringExtra(SYNCINTERVAL));
                if (accountName.equals(getSelectedAccountName())) {
                    /*
                    if (isSynced) {
                        Date date = new Date();
                        String sdate;
                        try {
                            sdate = DateFormat.getDateTimeInstance().format(date);
                        } catch (Exception e) {
                            Log.e(TAG, "getDateTimeInstance failed: " + date, e);
                            sdate = "";
                        }
                        //ImapNotes3.ShowMessage(getText(R.string.Last_sync) + sdate + " (" + getText(syncInterval.textID) + ")", accountSpinner, 2);
                    }
                     */
                    if ((errorMessage != null) && !errorMessage.isEmpty()) {
                        ImapNotes3.ShowMessage(errorMessage, accountSpinner, 5);
                    }
                }
                if (isChanged) RefreshList();
            }
        };
        getContentResolver().registerContentObserver(Uri.parse("content://" + BuildConfig.APPLICATION_ID + "/"), false, mObserver);

    }

    void handleSendMultipleImages(Intent intent) {
        Log.d(TAG, "handleSendMultipleImages");
        ArrayList<Uri> messageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        String accountName = getSelectedAccountName();
        if (accountName.isEmpty()) {
            ImapNotes3.ShowMessage(R.string.select_one_account, accountSpinner, 3);
            return;
        }
        if (messageUris != null) {
            ImapNotes3.ShowMessage(R.string.importing, accountSpinner, 2);
            UpdateList(messageUris, accountName);
        }
    }

    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        setPreferences();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSyncDot();
        if (listToView != null) RefreshList(getSelectedAccountName());
        Log.d(TAG, "onResume");
        Check_Action_Send();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        savePreferences();
        if (!(updateThread == null)) {
            // for some reason this helps...
            synchronized (ImapNotes3.MainLock) {
                if (updateThread.getStatus() == AsyncTask.Status.RUNNING) {
                    Log.d(TAG, "onPause RUNNING");
                }
            }
        }
    }

    @Override
    public void onPostCreate(final Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        Log.d(TAG, "onPostCreate");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        Log.d(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
        savePreferences();
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(TAG, "onRestoreInstanceState");
    }

    private void savePreferences() {
        SharedPreferences.Editor preferences = getApplicationContext().getSharedPreferences(SettingsActivity.MAIN_PREFERENCE_NAME, MODE_PRIVATE).edit();
        preferences.putLong(ACCOUNTSPINNER_POS, accountSpinner.getSelectedItemId());
        if (actionMenu == null) return;
        MenuItem sortDate = actionMenu.findItem(R.id.sort_date);
        MenuItem sortTitle = actionMenu.findItem(R.id.sort_title);
        MenuItem sortColor = actionMenu.findItem(R.id.sort_color);
        if (sortDate != null) preferences.putBoolean(SORT_BY_DATE, sortDate.isChecked());
        if (sortTitle != null) preferences.putBoolean(SORT_BY_TITLE, sortTitle.isChecked());
        if (sortColor != null) preferences.putBoolean(SORT_BY_COLOR, sortColor.isChecked());
        preferences.apply();
    }

    private void setPreferences() {
        Log.d(TAG, "setPreferences:");
        SharedPreferences preferences = getApplicationContext().getSharedPreferences(SettingsActivity.MAIN_PREFERENCE_NAME, MODE_PRIVATE);
        accountSpinner.setSelection((int) preferences.getLong(ACCOUNTSPINNER_POS, 0));
    }


    private void RefreshList() {
        RefreshList(getSelectedAccountName());
    }
    private void RefreshList(String accountName) {
        if(actionMenu == null)
            return;
        Log.d(TAG, "RefreshList: ");
        listToView.setSortOrder(getSortOrder());
        synchronized (ImapNotes3.MainLock) {
            new SyncThread(
                    accountName,
                    noteList,
                    listToView,
                    R.string.refreshing_notes_list,
                    getSortOrder(),
                    hashFilter,
                    mFilterString,
                    // FIXME: this. ?
                    getApplicationContext(),
                    this::updateAccountLabel).execute();
        }
    }

    private void UpdateList(
            String suid,
            String noteBody,
            String bgColor,
            String accountName,
            UpdateThread.Action action) {
        synchronized (ImapNotes3.MainLock) {
                updateThread = new UpdateThread(accountName,
                        this,
                        noteList,
                        listToView,
                        R.string.updating_notes_list,
                        suid,
                        noteBody,
                        bgColor,
                        // FIXME: this. ?
                        getApplicationContext(),
                        action).execute();
            }
    }

    private void UpdateList(
            ArrayList<Uri> uris,
            String accountName) {
        synchronized (ImapNotes3.MainLock) {
            updateThread = new UpdateThread(accountName,
                    this,
                    noteList,
                    listToView,
                    R.string.updating_notes_list,
                    uris,
                    getApplicationContext()).execute();
        }
    }

    @Override
    public void onFinishPerformed(Boolean result, String ErrText) {
        if (result) {
            TriggerSync(false);
        }
        if (!ErrText.isEmpty()) {
            ImapNotes3.ShowMessage(ErrText, listview, 30);
        }
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString();
                searchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                applySearch(query);
            }
        });
        searchClear.setOnClickListener(v -> searchInput.setText(""));
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void applySearch(String query) {
        mFilterString = query;
        if (query == null || query.isEmpty()) {
            listToView.ResetFilterData(noteList);
            RefreshList();
        } else {
            listToView.getFilter().filter(query);
        }
    }

    private void hideSearch() {
        searchInput.setText("");
        searchInput.clearFocus();
        hideKeyboard();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    @SuppressLint("RestrictedApi")
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        super.onCreateOptionsMenu(menu);
        actionMenu = menu;
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> newNote());
        return true;
    }

    private String getSortOrder() {
        SharedPreferences preferences = getApplicationContext()
                .getSharedPreferences(SettingsActivity.MAIN_PREFERENCE_NAME, MODE_PRIVATE);
        if (preferences.getBoolean(SORT_BY_TITLE, false))
            return "UPPER(" + OneNote.TITLE + ") ASC";
        if (preferences.getBoolean(SORT_BY_COLOR, false))
            return OneNote.BGCOLOR + " ASC";
        return OneNote.DATE + " DESC";
    }

    private void Check_Action_Send() {
        Log.d(TAG, "Check_Action_Send");
        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        if (action == null) {
            action = "";
        }
        Log.d(TAG, "Check_Action_Send:" + action);

        intentActionSend = null;
        if (intent.getBooleanExtra(PlainNoteActivity.ActivityTypeProcessed,false)) {
            Log.d(TAG, "Check_Action_Send: Intent already processed");
            return;
        }

        if (action.equals(Intent.ACTION_SEND)) {
            intentActionSend = (Intent) intent.clone();
            intentActionSend.setClass(this, PlainNoteActivity.class);
            intentActionSend.setFlags(0);
            intentActionSend.putExtra(PlainNoteActivity.ActivityType, PlainNoteActivity.ActivityTypeAddShare);

            ImapNotes3.ShowAction(listview, R.string.insert_as_new_note, R.string.ok, 0,
                    () -> {
                        intentActionSend.putExtra(ListActivity.EDIT_ITEM_ACCOUNTNAME, getSelectedAccountName());
                        startActivityForResult(intentActionSend, ListActivity.NEW_BUTTON);
                        setIntentAsProcessed();
                    });
        } else if (action.equals(Intent.ACTION_SEND_MULTIPLE)) {
            if (intent.getType().equals("message/rfc822")) {
                Intent finalIntent = intent;
                ImapNotes3.ShowAction(listview, R.string.insert_as_new_note, R.string.ok, 0,
                        () -> {
                            try {
                                handleSendMultipleImages(finalIntent);
                            } catch (Exception e) {
                                Log.e(TAG, "handleSendMultipleImages faled", e);
                            }
                        });
            }
        }
    }

    private void updateAccountLabel() {
        if (accountLabel == null) {
            return;
        }
        int position = accountSpinner.getSelectedItemPosition();
        String name;
        if (position > 0 && position < accountList.size()) {
            name = accountList.get(position);
        } else if (position == 0 && !accountList.isEmpty()) {
            name = getString(R.string.all_accounts);
        } else {
            name = getString(R.string.no_account);
        }
        accountLabel.setText(name);
        if (accountAvatar != null) {
            accountAvatar.setText(name.isEmpty() ? "?" : name.substring(0, 1));
        }
        updateSyncDot();
    }

    private void onSyncStatusChanged() {
        if (manualSyncInProgress
                && SystemClock.elapsedRealtime() - manualSyncStartedAt > 800) {
            Account account = getSelectedAccount();
            if (account == null || !ContentResolver.isSyncActive(account, AUTHORITY)) {
                manualSyncInProgress = false;
                syncHandler.removeCallbacks(syncTimeout);
            }
        }
        updateSyncDot();
    }

    private void updateSyncDot() {
        if (syncDot == null || syncStatus == null) {
            return;
        }
        Account account = getSelectedAccount();
        // Only a manual refresh shows the syncing state. System sync pending/active
        // flags can stay stuck on some ROMs and made the header show "同步中" forever.
        boolean syncing = manualSyncInProgress;
        boolean online = isNetworkAvailable();
        int color;
        String status;
        if (account == null) {
            color = R.color.sync_gray;
            status = getString(R.string.no_account);
        } else if (syncing) {
            color = R.color.sync_orange;
            status = getString(R.string.status_syncing);
        } else if (online) {
            color = R.color.sync_green;
            status = getString(R.string.status_synced);
        } else {
            color = R.color.sync_gray;
            status = getString(R.string.status_offline);
        }
        syncDot.setBackgroundTintList(ColorStateList.valueOf(getColor(color)));
        int count = noteList == null ? 0 : noteList.size();
        syncStatus.setText(getString(R.string.sync_status_count, status, count));
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void showAccountSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.sheet_account, null);
        LinearLayout container = sheet.findViewById(R.id.sheetAccountList);
        int selectedPosition = accountSpinner.getSelectedItemPosition();
        int firstAccount = 1;
        if (accountList.size() <= 1) {
            firstAccount = accountList.size();
        }
        for (int i = firstAccount; i < accountList.size(); i++) {
            View row = LayoutInflater.from(this).inflate(R.layout.sheet_account_row, container, false);
            TextView name = row.findViewById(R.id.sheetAccountName);
            TextView avatar = row.findViewById(R.id.sheetAccountAvatar);
            ImageView check = row.findViewById(R.id.sheetAccountCheck);
            String accountName = accountList.get(i);
            name.setText(accountName);
            avatar.setText(accountName.isEmpty() ? "?" : accountName.substring(0, 1));
            check.setVisibility(i == selectedPosition ? View.VISIBLE : View.INVISIBLE);
            final int position = i;
            row.setOnClickListener(v -> {
                accountSpinner.setSelection(position);
                dialog.dismiss();
            });
            container.addView(row);
        }
        if (container.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_account);
            empty.setTextColor(getColor(R.color.ink_secondary));
            empty.setPadding(0, 12, 0, 12);
            container.addView(empty);
        }
        sheet.findViewById(R.id.sheetManage).setOnClickListener(v -> {
            dialog.dismiss();
            clickListenerEditAccount.onClick(null);
        });
        sheet.findViewById(R.id.sheetSync).setOnClickListener(v -> {
            dialog.dismiss();
            TriggerSync(true);
        });
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showSwitchAccountDialog() {
        if (accountList.size() <= 2) {
            ImapNotes3.ShowMessage(R.string.select_one_account, accountLabel, 3);
            return;
        }
        String[] names = new String[accountList.size() - 1];
        for (int i = 1; i < accountList.size(); i++) {
            names[i - 1] = accountList.get(i);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.switch_account)
                .setItems(names, (dialog, which) -> accountSpinner.setSelection(which + 1))
                .show();
    }

    synchronized private void TriggerSync(boolean refreshTags) {
        Log.d(TAG, "TriggerSync");
        Account mAccount = getSelectedAccount();
        if (mAccount == null) {
            Log.w(TAG, "TriggerSync: Account==null");
            return;
        }
        swipeLayout.setRefreshing(true);
        manualSyncInProgress = true;
        manualSyncStartedAt = SystemClock.elapsedRealtime();
        updateSyncDot();
        syncHandler.removeCallbacks(syncTimeout);
        syncHandler.postDelayed(syncTimeout, 15000);
        Bundle settingsBundle = new Bundle();
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        settingsBundle.putBoolean(REFRESH_TAGS, refreshTags);
        ContentResolver.cancelSync(mAccount, AUTHORITY);
        ContentResolver.requestSync(mAccount, AUTHORITY, settingsBundle);
    }

    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.newaccount: {
                Intent res = new Intent(ListActivity.this, AccountConfigurationActivity.class);
                res.putExtra(AccountConfigurationActivity.ACTION, AccountConfigurationActivity.Actions.CREATE_ACCOUNT);
                startActivityForResult(res, ListActivity.ADD_ACCOUNT);
                return true;
            }
            case R.id.refresh:
                if (getSelectedAccountName().isEmpty())
                    ImapNotes3.ShowMessage(R.string.select_one_account, accountSpinner, 3);
                else
                    TriggerSync(true);
                return true;
            case R.id.newnote:
                newNote();
                return true;
            case R.id.sort_date:
            case R.id.sort_title:
            case R.id.sort_color: {
                item.setChecked(true);
                RefreshList();
                return true;
            }
            case R.id.filter_by_hash: {
                NotesDb storedNotes = NotesDb.getInstance(getApplicationContext());
                List<String> tags = storedNotes.GetTags("", getSelectedAccountName());
                List<Integer> positions = new ArrayList<>();
                for (String tag : tags) {
                    if (hashFilterSelected.contains(tag))
                        positions.add(tags.indexOf(tag));
                }
                String[] tagArray = new String[tags.size()];
                tags.toArray(tagArray);
                SimpleListDialog.build()
                        .title(R.string.filter_by_hash)
                        .choiceMode(SimpleListDialog.MULTI_CHOICE)
                        .filterable(true)
                        .choicePreset(positions)
                        .items(tagArray)
                        .filterable(true)
                        .neg(R.string.cancel)
                        .neut(R.string.reset_filter)
                        .show(this, DLG_FILTER_HASHTAG);
                return true;
            }
            case R.id.switch_account:
                showSwitchAccountDialog();
                return true;
            case R.id.manage_account:
                clickListenerEditAccount.onClick(null);
                return true;
            case R.id.settings: {
                Intent res = new Intent(ListActivity.this, SettingsActivity.class);
                startActivityForResult(res, ListActivity.EDIT_SETTINGS);
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    public void openFileSelector() {
        Context context = ImapNotes3.getAppContext();

        if (!ZipUtils.checkPermissionStorage(context)) {
            ZipUtils.requestPermission(this);
        } else {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/zip");
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            try {
                startActivityForResult(
                        Intent.createChooser(intent, "Select a ZIP file"),
                        SELECT_ARCHIVE_FOR_RESTORE);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "ActivityNotFoundException: ", e);
                ImapNotes3.ShowMessage("Please install a file manager.", listview, 3000);
            }
        }
    }

    private Integer getSpinnerPos(String accountName) {
        int n = accountSpinner.getCount();
        for (int i = 1; i < n; i++) {
            if (accountName.equals(accountSpinner.getAdapter().getItem(i).toString())) {
                return (i);
            }
        }
        return 0;
    }

    // Spinner item selected listener
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        Log.d(TAG, "onItemSelected");
        String accountName = getSelectedAccountName();
        updateAccountLabel();
        listToView.setAccountName(accountName);
        RefreshList(accountName);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // TODO Auto-generated method stub
    }

    // Hack: if the Spinner isDisabled Search is active->
    //all accounts are selected
    private Account getSelectedAccount() {
        long pos = accountSpinner.getSelectedItemId();
        if (pos <= 0) {
            if (accounts.length == 1) {
                return accounts[0];
            }
        } else {
            try {
                return accounts[(int) pos - 1];
            } catch (Exception e) {
                Log.e(TAG, "getSelectedAccount wrong pos: " + pos, e);
            }
        }
        return null;
    }

    private String getSelectedAccountName() {
        Account account = getSelectedAccount();
        if (account == null)
            return "";
        return account.name;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: " + requestCode + " " + resultCode);
        switch (requestCode) {
            case ListActivity.SEE_DETAIL:
                // Returning from PlainNoteActivity
                if (resultCode == ListActivity.DELETE_BUTTON) {
                    // Delete Message asked for
                    // String suid will contain the Message Imap UID to delete
                    String suid = data.getStringExtra(DELETE_ITEM_NUM_IMAP);
                    String accountName = data.getStringExtra(EDIT_ITEM_ACCOUNTNAME);
                    UpdateList(suid, null, null, accountName, UpdateThread.Action.Delete);
                }
                if (resultCode == ListActivity.EDIT_BUTTON) {
                    String txt = ImapNotes3.AvoidLargeBundle;  //data.getStringExtra(EDIT_ITEM_TXT);
                    String suid = data.getStringExtra(EDIT_ITEM_NUM_IMAP);
                    String bgcolor = data.getStringExtra(EDIT_ITEM_COLOR);
                    String accountName = data.getStringExtra(EDIT_ITEM_ACCOUNTNAME);
                    UpdateList(suid, txt, bgcolor, accountName, UpdateThread.Action.Update);
                }
                break;
            case ListActivity.NEW_BUTTON:
                // Returning from NewNoteActivity
                if (resultCode == ListActivity.EDIT_BUTTON) {
                    String txt = ImapNotes3.AvoidLargeBundle; //data.getStringExtra(EDIT_ITEM_TXT);
                    String bgcolor = data.getStringExtra(EDIT_ITEM_COLOR);
                    String accountName = data.getStringExtra(EDIT_ITEM_ACCOUNTNAME);
                    UpdateList("", txt, bgcolor, accountName, UpdateThread.Action.Insert);
                }
                break;
            case ListActivity.ADD_ACCOUNT:
            case ListActivity.EDIT_ACCOUNT:
                // Returning from ADD
                // Cancel, when no Account exists
                if ((ListActivity.accountList.size() <= 1) && (resultCode != ResultCodeSuccess) ) {
                    noAccountExists();
                 }
                if (resultCode == ResultCodeRemoveAccount) {
                    accountSpinner.setSelection(1);
                    if(accounts.length >= 1) RefreshList(accounts[1].name);
                }
                if (resultCode == ResultCodeSuccess ) {
                    ListActivity.accountManager.addOnAccountsUpdatedListener(
                            new AccountsUpdateListener(), null, true);
                    if (data != null) {
                        Integer pos = getSpinnerPos(data.getStringExtra(EDIT_ITEM_ACCOUNTNAME));
                        if (pos > 0) {
                            accountSpinner.setSelection(pos);
                        }
                    }
                    TriggerSync(true);
                }
                break;
            case ListActivity.SELECT_ARCHIVE_FOR_RESTORE:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        Uri uri = data.getData();
                        if (uri != null) {
                            BackupRestore backupRestore = new BackupRestore(uri, new ArrayList<>(accountList));
                            backupRestore.show(getSupportFragmentManager(), "restore_dialog");
                        }
                    }
                }
                break;
            case ListActivity.EDIT_SETTINGS:
                switch (resultCode) {
                    case ResultCodeMakeArchive:
                        BackupRestore.CreateArchive(this, getSelectedAccountName());
                        break;
                    case ResultCodeRestoreArchive:
                        openFileSelector();
                        break;
                    default:
                        RefreshList();
                }
                break;
            default:
                Log.e(TAG, "onActivityResult: unknown result code");
        }
    }

    ;

    public Account getAccountFromName(String accountName) {
        for (Account account : ListActivity.accounts
        ) {
            if (account.name.equals(accountName))
                return account;
        }
        Log.e(TAG, "getAccountFromName: no account found: " + accountName);
        return null;
    }

    private void noAccountExists() {
        Log.d(TAG, "noAccountExists");
        Intent res = new Intent(ListActivity.this, AccountConfigurationActivity.class);
        startActivityForResult(res, ListActivity.ADD_ACCOUNT);
    }

    private void newNote() {
        Intent toNew;

        if (intentActionSend != null)
            toNew = intentActionSend;
        else
            toNew = new Intent(this, PlainNoteActivity.class);
        toNew.putExtra(PlainNoteActivity.ActivityType, PlainNoteActivity.ActivityTypeAdd);
        toNew.putExtra(ListActivity.EDIT_ITEM_ACCOUNTNAME, getSelectedAccountName());
        startActivityForResult(toNew, ListActivity.NEW_BUTTON);
    }

    public void setIntentAsProcessed() {
        Intent intent = getIntent();
        intent.putExtra(PlainNoteActivity.ActivityTypeProcessed, true);
        if (intentActionSend != null)
           intentActionSend.putExtra(PlainNoteActivity.ActivityTypeProcessed, true);
    }

    private void updateAccountSpinner() {
        Log.d(TAG, "updateAccountSpinner");
        this.accountSpinner.setEnabled(true);
        this.spinnerList.notifyDataSetChanged();
        setPreferences();
        long id = this.accountSpinner.getSelectedItemId();
        // only one account active..disable selection
        if (ListActivity.accountList.size() == 2) {
            this.accountSpinner.setEnabled(false);
            this.accountSpinner.setSelection(1);
            id = 1;
        }
        if ((id == android.widget.AdapterView.INVALID_ROW_ID) || (id >= ListActivity.accountList.size())) {
            this.accountSpinner.setSelection(1);
        }
        if (actionMenu != null) {
            MenuItem switchItem = actionMenu.findItem(R.id.switch_account);
            if (switchItem != null) switchItem.setVisible(false);
        }
        updateAccountLabel();
    }

    @Nullable
    @Override
    public Filter getFilter() {
        return null;
    }

    @Override
    public void onSelectedData(ArrayList<Uri> messageUris, String accountName) {
        UpdateList(messageUris, accountName);
    }

    private class AccountsUpdateListener implements OnAccountsUpdateListener {

        @Override
        public void onAccountsUpdated(@NonNull Account[] myAccounts) {
            Log.d(TAG, "onAccountsUpdated");
            List<String> newList;

            //invoked when the AccountManager starts up and whenever the account set changes
            ArrayList<Account> newAccounts = new ArrayList<>();
            for (final Account account : myAccounts) {
                if (account.type.equals(Utilities.PackageName)) {
                    newAccounts.add(account);
                }
            }
            Account[] tempAccounts = new Account[newAccounts.size()];
            newList = new ArrayList<>();
            newList.add(getString(R.string.all_accounts));
            int i = 0;
            for (final Account account : newAccounts) {
                tempAccounts[i++] = account;
                newList.add(account.name);
            }
            accounts = tempAccounts;
            boolean equalLists = true;
            ListIterator<String> iter = ListActivity.accountList.listIterator();
            // skip first entry (All)
            if (iter.hasNext()) iter.next();
            while (iter.hasNext()) {
                String s = iter.next();
                if (!(newList.contains(s))) {
                    iter.remove();
                    // Why try here?
                    try {
                        FileUtils.deleteDirectory(ImapNotes3.GetAccountDir(s));
                    } catch (IOException e) {
                        Log.e(TAG, "deleteDirectory failed:", e);
                    }
                    equalLists = false;
                }
            }
            boolean first = true;
            for (String accountName : newList) {
                if (!(accountList.contains(accountName))) {
                    accountList.add(accountName);
                    equalLists = false;
                    // skip first entry (All)
                    if (!first)
                        SyncUtils.CreateLocalDirectories(ImapNotes3.GetAccountDir(accountName));
                }
                first = false;
            }
            if (!equalLists) {
                updateAccountSpinner();
            }
            if (accountList.size() <= 1) {
                noAccountExists();
            }
        }
    }
}
