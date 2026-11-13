package com.ninpo.scout;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * Handles "Check on VirusTotal" from the text-selection menu (ACTION_PROCESS_TEXT)
 * and from the Share sheet (ACTION_SEND). It has no screen of its own: it opens the
 * report straight away, or shows a small dialog when there is a choice to make.
 */
public class CheckActivity extends AppCompatActivity {

    private final CheckFlow.Session session = new CheckFlow.Session();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CharSequence text = selectedText(getIntent());
        if (TextUtils.isEmpty(text) || TextUtils.getTrimmedLength(text) == 0) {
            explain(R.string.nothing_empty_body);
            return;
        }
        List<Indicator> found = IndicatorParser.parse(text);
        if (found.isEmpty()) {
            explain(R.string.nothing_body);
            return;
        }
        CheckFlow.start(this, found, session, this::finish);
    }

    @Override
    protected void onDestroy() {
        session.end();
        super.onDestroy();
    }

    private void explain(@StringRes int message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nothing_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .setOnDismissListener(d -> finish())
                .show();
    }

    /** The text this activity was asked to check, or null. Never throws. */
    @VisibleForTesting
    @Nullable
    static CharSequence selectedText(@Nullable Intent intent) {
        if (intent == null) return null;
        try {
            if (Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
                return intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            }
            if (Intent.ACTION_SEND.equals(intent.getAction())) {
                // Apps often share "Page title https://link". The parser picks the link out.
                return intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            }
        } catch (RuntimeException e) {
            // This activity is exported, so any app can send it a malformed extra.
            return null;
        }
        return null;
    }
}
