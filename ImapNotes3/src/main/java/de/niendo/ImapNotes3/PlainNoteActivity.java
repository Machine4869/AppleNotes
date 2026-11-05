package de.niendo.ImapNotes3;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import javax.mail.Message;

import de.niendo.ImapNotes3.Data.OneNote;
import de.niendo.ImapNotes3.Miscs.HtmlNote;
import de.niendo.ImapNotes3.Sync.SyncUtils;
import de.niendo.ImapNotes3.Miscs.Utilities;

/** Plain-text note editor. No WebView and no rich-text formatting. */
public class PlainNoteActivity extends AppCompatActivity {
    public static final String selectedNote = "selectedNote";
    public static final String ActivityType = "ActivityType";
    public static final String ActivityTypeEdit = "ActivityTypeEdit";
    public static final String ActivityTypeAdd = "ActivityTypeAdd";
    public static final String ActivityTypeAddShare = "ActivityTypeAddShare";
    public static final String ActivityTypeProcessed = "ActivityTypeProcessed";

    private EditText titleView;
    private EditText bodyView;
    private TextView metaView;
    private String suid = "";
    private String accountName = "";
    private String noteTitle = "";
    private String noteDate = "";
    private boolean settingText;
    private boolean dirty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_plain);
        titleView = findViewById(R.id.titleView);
        bodyView = findViewById(R.id.bodyView);
        metaView = findViewById(R.id.metaView);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayShowTitleEnabled(false);
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setElevation(0);
        }
        ImapNotes3.stylePage(this, android.graphics.Color.WHITE);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });

        titleView.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (!settingText) {
                    dirty = true;
                    updateMeta();
                }
            }
        });
        bodyView.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (!settingText) {
                    dirty = true;
                    updateMeta();
                }
            }
        });

        Intent intent = getIntent();
        String type = intent.getStringExtra(ActivityType);
        if (ActivityTypeEdit.equals(type)) {
            HashMap hm = (HashMap) intent.getSerializableExtra(selectedNote);
            if (hm == null) {
                finish();
                return;
            }
            suid = String.valueOf(hm.get(OneNote.UID));
            accountName = String.valueOf(hm.get(OneNote.ACCOUNT));
            noteTitle = String.valueOf(hm.get(OneNote.TITLE));
            noteDate = String.valueOf(hm.get(OneNote.DATE));
            loadNote();
        } else {
            accountName = intent.getStringExtra(ListActivity.EDIT_ITEM_ACCOUNTNAME);
            noteDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
            settingText = true;
            titleView.setText("");
            bodyView.setText("");
            settingText = false;
            updateMeta();
            titleView.requestFocus();
        }
    }

    private void loadNote() {
        File root = ImapNotes3.GetAccountDir(accountName);
        Message message = SyncUtils.ReadMailFromFileRootAndNew(suid, root);
        String plain = "";
        if (message != null) {
            try {
                String html = HtmlNote.GetNoteFromMessage(message).text;
                plain = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString();
            } catch (Exception ignored) {
            }
        }
        String body = plain.replace("\r", "");
        if (!noteTitle.isEmpty() && body.startsWith(noteTitle)) {
            body = body.substring(noteTitle.length());
        }
        body = body.replaceFirst("^[\\s\\n]+", "");
        settingText = true;
        titleView.setText(noteTitle);
        bodyView.setText(body);
        settingText = false;
        updateMeta();
    }

    private void updateMeta() {
        int count = (titleView.getText().toString() + bodyView.getText().toString())
                .replaceAll("\\s+", "").length();
        String shown = noteDate == null ? "" : noteDate;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})[ T](\\d{1,2}):(\\d{2})")
                .matcher(shown);
        if (m.find()) {
            shown = Integer.parseInt(m.group(2)) + "月" + Integer.parseInt(m.group(3))
                    + "日 " + m.group(4) + ":" + m.group(5);
        }
        metaView.setText(shown + " | " + count + " 字");
    }

    private String escape(String value) {
        return TextUtils.htmlEncode(value == null ? "" : value).replace("\n", "<br>");
    }

    private void confirmExit() {
        if (!dirty) {
            finish();
            return;
        }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_save);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout((int) (280 * getResources().getDisplayMetrics().density + 0.5f),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.findViewById(R.id.saveAction).setOnClickListener(v -> {
            dialog.dismiss();
            save();
        });
        dialog.findViewById(R.id.discardAction).setOnClickListener(v -> {
            dialog.dismiss();
            dirty = false;
            finish();
        });
        dialog.findViewById(R.id.cancelAction).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void save() {
        String title = titleView.getText().toString().trim();
        String body = bodyView.getText().toString();
        String html = (title.isEmpty() ? "" : "<div><b>" + escape(title) + "</b></div>")
                + "<div>" + escape(body) + "</div>";
        ImapNotes3.AvoidLargeBundle = html;
        Intent result = new Intent();
        result.putExtra(ListActivity.EDIT_ITEM_NUM_IMAP, suid);
        result.putExtra(ListActivity.EDIT_ITEM_ACCOUNTNAME, accountName);
        result.putExtra(ListActivity.EDIT_ITEM_COLOR, "none");
        dirty = false;
        setResult(ListActivity.EDIT_BUTTON, result);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.plain_note, menu);
        MenuItem undo = menu.findItem(R.id.itemUndo);
        MenuItem redo = menu.findItem(R.id.itemRedo);
        MenuItem save = menu.findItem(R.id.save);
        if (undo != null && undo.getIcon() != null) undo.getIcon().setTint(getColor(R.color.ink));
        if (redo != null && redo.getIcon() != null) redo.getIcon().setTint(getColor(R.color.ink));
        if (save != null && save.getIcon() != null) save.getIcon().setTint(getColor(R.color.ink));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            confirmExit();
            return true;
        }
        if (item.getItemId() == R.id.save) {
            save();
            return true;
        }
        if (item.getItemId() == R.id.itemUndo || item.getItemId() == R.id.itemRedo) {
            EditText target = bodyView.hasFocus() ? bodyView : titleView;
            target.onTextContextMenuItem(item.getItemId() == R.id.itemUndo
                    ? android.R.id.undo : android.R.id.redo);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
