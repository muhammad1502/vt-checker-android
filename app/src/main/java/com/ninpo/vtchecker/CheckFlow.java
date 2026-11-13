package com.ninpo.vtchecker;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * What happens after text is found: pick one item if there are several, show the
 * privacy notice before the very first check, then open the VirusTotal report.
 * Shared by the home screen and the text-selection / Share handler.
 */
final class CheckFlow {

    private static final String PREFS = "vt_checker";
    private static final String KEY_NOTICE_SEEN = "privacy_notice_seen";

    private CheckFlow() {}

    /** The dialog currently on screen, so an activity can close it quietly in onDestroy. */
    static final class Session {
        @Nullable private AlertDialog dialog;

        void end() {
            if (dialog == null) return;
            dialog.setOnDismissListener(null);
            if (dialog.isShowing()) dialog.dismiss();
            dialog = null;
        }
    }

    /**
     * Starts the flow. {@code onFinished} runs once the user has opened a report or
     * backed out, so the invisible handler activity knows when to close.
     */
    static void start(@NonNull AppCompatActivity activity,
                      @NonNull List<Indicator> found,
                      @NonNull Session session,
                      @Nullable Runnable onFinished) {
        if (found.size() == 1) {
            confirmThenOpen(activity, found.get(0), session, onFinished);
        } else {
            choose(activity, found, session, onFinished);
        }
    }

    private static void choose(AppCompatActivity activity, List<Indicator> found,
                               Session session, @Nullable Runnable onFinished) {
        boolean[] picked = {false};
        ArrayAdapter<Indicator> adapter = new ArrayAdapter<Indicator>(activity, R.layout.item_choice, found) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View row = convertView != null ? convertView
                        : LayoutInflater.from(getContext()).inflate(R.layout.item_choice, parent, false);
                Indicator indicator = found.get(position);
                ((TextView) row.findViewById(R.id.choice_value)).setText(indicator.value);
                ((TextView) row.findViewById(R.id.choice_type)).setText(indicator.label());
                return row;
            }
        };
        session.dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.picker_title)
                .setAdapter(adapter, (dialog, which) -> {
                    // Item clicks arrive before the dialog's own dismiss, so the
                    // dismiss listener below can tell a pick from a cancel.
                    picked[0] = true;
                    confirmThenOpen(activity, found.get(which), session, onFinished);
                })
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener(dialog -> {
                    if (!picked[0]) finished(onFinished);
                })
                .show();
    }

    private static void confirmThenOpen(AppCompatActivity activity, Indicator indicator,
                                        Session session, @Nullable Runnable onFinished) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_NOTICE_SEEN, false)) {
            open(activity, VirusTotal.searchUrl(indicator));
            finished(onFinished);
            return;
        }
        session.dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.notice_title)
                .setMessage(R.string.notice_body)
                .setPositiveButton(R.string.notice_continue, (dialog, which) -> {
                    prefs.edit().putBoolean(KEY_NOTICE_SEEN, true).apply();
                    open(activity, VirusTotal.searchUrl(indicator));
                })
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener(dialog -> finished(onFinished))
                .show();
    }

    /**
     * Opens a page in a Custom Tab: the user's own browser, shown over the current
     * app, so closing it returns to where they were. Browsers without Custom Tabs
     * support open the page normally.
     */
    static void open(@NonNull Context context, @NonNull String url) {
        CustomTabsIntent tab = new CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build();
        try {
            tab.launchUrl(context, Uri.parse(url));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context.getApplicationContext(), R.string.no_browser, Toast.LENGTH_LONG).show();
        }
    }

    private static void finished(@Nullable Runnable onFinished) {
        if (onFinished != null) onFinished.run();
    }
}
