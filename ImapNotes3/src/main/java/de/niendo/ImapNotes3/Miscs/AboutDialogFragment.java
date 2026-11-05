package de.niendo.ImapNotes3.Miscs;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import de.niendo.ImapNotes3.BuildConfig;
import de.niendo.ImapNotes3.R;

public class AboutDialogFragment extends BottomSheetDialogFragment {
    private static final String GITHUB = "https://github.com/Machine4869/AppleNotes";
    private static final String UPSTREAM = "https://github.com/niendo1/ImapNotes3";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.about_dialog, container, false);
        TextView version = view.findViewById(R.id.aboutVersion);
        version.setText("v" + BuildConfig.VERSION_NAME.replaceFirst("^v", ""));
        view.findViewById(R.id.aboutGithub).setOnClickListener(v -> open(GITHUB));
        view.findViewById(R.id.aboutUpstream).setOnClickListener(v -> open(UPSTREAM));
        view.findViewById(R.id.aboutSource).setOnClickListener(v -> open(GITHUB));
        view.findViewById(R.id.aboutEmail).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:machine4869@163.com"));
            startActivity(intent);
        });
        view.findViewById(R.id.aboutClose).setOnClickListener(v -> dismiss());
        return view;
    }

    private void open(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
