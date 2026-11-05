package de.niendo.ImapNotes3;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

import de.niendo.ImapNotes3.Data.NotesDb;
import de.niendo.ImapNotes3.Data.OneNote;
import de.niendo.ImapNotes3.Miscs.AboutDialogFragment;
import de.niendo.ImapNotes3.Miscs.Utilities;

public class SettingsActivity extends AppCompatActivity {
    public static final String MAIN_PREFERENCE_NAME = Utilities.PackageName + "_preferences";

    private TextView accountName;
    private TextView accountSub;
    private TextView sortValue;
    private boolean observingSync;
    private final SyncStatusObserver syncObserver = which -> runOnUiThread(this::updateAccountCard);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        ImapNotes3.styleActionBar(actionBar, this);
        ImapNotes3.stylePage(this, getColor(R.color.settings_bg));
        setResult(ListActivity.ResultCodeSuccess);

        accountName = findViewById(R.id.settingsAccountName);
        accountSub = findViewById(R.id.settingsAccountSub);
        sortValue = findViewById(R.id.sortValue);
        TextView footer = findViewById(R.id.versionFooter);
        footer.setText("AppleNotes " + BuildConfig.VERSION_NAME);

        findViewById(R.id.settingsAccountCard).setOnClickListener(v -> openAccount(false));
        findViewById(R.id.rowAddAccount).setOnClickListener(v -> openAccount(true));
        findViewById(R.id.rowSwitchAccount).setOnClickListener(v -> showChoiceSheet(
                "账户", accountNames(), currentAccountIndex(), index -> {
                    getSharedPreferences(MAIN_PREFERENCE_NAME, MODE_PRIVATE).edit()
                            .putLong("ACCOUNTSPINNER_POS", index + 1).apply();
                    finish();
                }));
        findViewById(R.id.rowSort).setOnClickListener(v -> showSortSheet());
        findViewById(R.id.rowSyncNow).setOnClickListener(v -> syncNow());
        findViewById(R.id.rowAbout).setOnClickListener(v ->
                new AboutDialogFragment().show(getSupportFragmentManager(), "about"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccountCard();
        updateSortValue();
    }

    private int currentAccountIndex() {
        long pos = getSharedPreferences(MAIN_PREFERENCE_NAME, MODE_PRIVATE)
                .getLong("ACCOUNTSPINNER_POS", 1);
        return (int) Math.max(0, pos - 1);
    }

    private List<String> accountNames() {
        Account[] accounts = AccountManager.get(this).getAccountsByType(Utilities.PackageName);
        List<String> names = new ArrayList<>();
        for (Account account : accounts) names.add(account.name);
        return names;
    }

    private void updateAccountCard() {
        Account[] accounts = AccountManager.get(this).getAccountsByType(Utilities.PackageName);
        if (accounts.length == 0) {
            accountName.setText("未添加账户");
            accountSub.setText("点此添加 163 账户");
            return;
        }
        String name = accounts[0].name;
        accountName.setText(name);
        ArrayList<OneNote> notes = new ArrayList<>();
        NotesDb.getInstance(this).GetStoredNotes(notes, name, OneNote.DATE + " DESC", null);
        // isSyncPending can stay true forever on some ROMs after a finished sync.
        // Only an actually active sync should show "同步中".
        boolean syncing = ContentResolver.isSyncActive(accounts[0], Utilities.PackageName + ".provider");
        String status = syncing ? "同步中" : (isNetworkAvailable() ? "已同步" : "离线");
        accountSub.setText("163 邮箱 · " + status + " · " + notes.size() + " 条");
    }

    private void updateSortValue() {
        android.content.SharedPreferences p = getSharedPreferences(MAIN_PREFERENCE_NAME, MODE_PRIVATE);
        if (p.getBoolean("SORT_BY_TITLE", false)) sortValue.setText("按标题");
        else if (p.getBoolean("SORT_BY_COLOR", false)) sortValue.setText("按颜色");
        else sortValue.setText("按编辑日期");
    }

    private void openAccount(boolean create) {
        Account[] accounts = AccountManager.get(this).getAccountsByType(Utilities.PackageName);
        Intent intent = new Intent(this, AccountConfigurationActivity.class);
        if (!create && accounts.length > 0) {
            intent.putExtra(AccountConfigurationActivity.ACTION, AccountConfigurationActivity.Actions.EDIT_ACCOUNT);
            intent.putExtra(AccountConfigurationActivity.ACCOUNTNAME, accounts[0].name);
        } else {
            intent.putExtra(AccountConfigurationActivity.ACTION, AccountConfigurationActivity.Actions.CREATE_ACCOUNT);
        }
        startActivity(intent);
    }

    private void showSortSheet() {
        String[] items = {"按编辑日期", "按标题", "按颜色"};
        android.content.SharedPreferences p = getSharedPreferences(MAIN_PREFERENCE_NAME, MODE_PRIVATE);
        int selected = p.getBoolean("SORT_BY_TITLE", false) ? 1
                : p.getBoolean("SORT_BY_COLOR", false) ? 2 : 0;
        showChoiceSheet("排序方式", java.util.Arrays.asList(items), selected, index -> {
            android.content.SharedPreferences.Editor e =
                    getSharedPreferences(MAIN_PREFERENCE_NAME, MODE_PRIVATE).edit();
            e.putBoolean("SORT_BY_DATE", index == 0);
            e.putBoolean("SORT_BY_TITLE", index == 1);
            e.putBoolean("SORT_BY_COLOR", index == 2);
            e.apply();
            finish();
        });
    }

    private void showChoiceSheet(String title, List<String> options, int selected,
                                 java.util.function.IntConsumer onSelect) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.sheet_choice, null);
        ((TextView) content.findViewById(R.id.choiceTitle)).setText(title);
        LinearLayout list = content.findViewById(R.id.choiceList);
        for (int i = 0; i < options.size(); i++) {
            View row = getLayoutInflater().inflate(R.layout.sheet_choice_row, list, false);
            ((TextView) row.findViewById(R.id.choiceName)).setText(options.get(i));
            row.findViewById(R.id.choiceCheck).setVisibility(i == selected ? View.VISIBLE : View.INVISIBLE);
            int index = i;
            row.setOnClickListener(v -> {
                onSelect.accept(index);
                dialog.dismiss();
            });
            list.addView(row);
        }
        View cancel = content.findViewById(R.id.choiceCancel);
        if (cancel != null) cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(content);
        dialog.show();
    }

    private void syncNow() {
        Account[] accounts = AccountManager.get(this).getAccountsByType(Utilities.PackageName);
        if (accounts.length == 0) {
            Toast.makeText(this, "还没有账户", Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        ContentResolver.cancelSync(accounts[0], Utilities.PackageName + ".provider");
        ContentResolver.requestSync(accounts[0], Utilities.PackageName + ".provider", extras);
        Toast.makeText(this, "已开始同步", Toast.LENGTH_SHORT).show();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
