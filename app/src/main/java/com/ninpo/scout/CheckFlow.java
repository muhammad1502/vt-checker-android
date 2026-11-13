package com.ninpo.scout;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
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
 * privacy notice before the very first check, then hand the report to the screen.
 * Shared by the home screen and the text-selection / Share handler.
 */
final class CheckFlow {

    private static final String PREFS = "scout";
    private static final String KEY_NOTICE_SEEN = "privacy_notice_seen";

    /** Report sheet height: tall enough to read a report, with the app still visible above. */
    private static final float REPORT_HEIGHT = 0.8f;
    /** Chrome's maximum for EXTRA_TOOLBAR_CORNER_RADIUS_DP; close to the 18dp card radius. */
    private static final int REPORT_CORNER_RADIUS_DP = 16;

    private CheckFlow() {}

    /** The screen running the flow. */
    interface Host {
        /** The user chose an item and, the first time, accepted the notice. */
        void openReport(@NonNull String url);

        /** The user backed out of the list or the notice. */
        void cancelled();
    }

    /**
     * Per-activity state: the dialog on screen, so it can be closed quietly in
     * onDestroy, and the browser warm-up that runs while the user decides.
     */
    static final class Session {
        @Nullable private AlertDialog dialog;
        private final BrowserWarmup warmup = new BrowserWarmup();

        void warmUp(@NonNull Context context) {
            warmup.start(context);
        }

        void coolDown() {
            warmup.stop();
        }

        boolean isWarmingUp() {
            return warmup.isStarted();
        }

        void end() {
            warmup.stop();
            if (dialog == null) return;
            dialog.setOnDismissListener(null);
            if (dialog.isShowing()) dialog.dismiss();
            dialog = null;
        }
    }

    static void start(@NonNull AppCompatActivity activity,
                      @NonNull List<Indicator> found,
                      @NonNull Session session,
                      @NonNull Host host) {
        if (found.size() == 1) {
            confirmThenOpen(activity, found.get(0), session, host);
        } else {
            choose(activity, found, session, host);
        }
    }

    private static void choose(AppCompatActivity activity, List<Indicator> found,
                               Session session, Host host) {
        boolean[] picked = {false};
        session.warmUp(activity);
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
                    confirmThenOpen(activity, found.get(which), session, host);
                })
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener(dialog -> {
                    if (!picked[0]) host.cancelled();
                })
                .show();
    }

    private static void confirmThenOpen(AppCompatActivity activity, Indicator indicator,
                                        Session session, Host host) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_NOTICE_SEEN, false)) {
            host.openReport(VirusTotal.searchUrl(indicator));
            return;
        }
        boolean[] accepted = {false};
        session.warmUp(activity);
        session.dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.notice_title)
                .setMessage(R.string.notice_body)
                .setPositiveButton(R.string.notice_continue, (dialog, which) -> {
                    accepted[0] = true;
                    prefs.edit().putBoolean(KEY_NOTICE_SEEN, true).apply();
                    host.openReport(VirusTotal.searchUrl(indicator));
                })
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener(dialog -> {
                    if (!accepted[0]) host.cancelled();
                })
                .show();
    }

    /**
     * A VirusTotal report as a sheet over the current screen (a partial Custom Tab,
     * Chrome 107 and later), resizable by dragging. Browsers without partial Custom
     * Tabs ignore the height and show a full-screen tab. Chrome requires these to be
     * started with startActivityForResult (or a service session), so callers launch
     * this intent through an ActivityResultLauncher.
     */
    @NonNull
    static Intent reportIntent(@NonNull Context context, @NonNull String url) {
        int height = Math.round(context.getResources().getDisplayMetrics().heightPixels * REPORT_HEIGHT);
        CustomTabsIntent tab = new CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .setInitialActivityHeightPx(height, CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE)
                .setToolbarCornerRadiusDp(REPORT_CORNER_RADIUS_DP)
                .build();
        tab.intent.setData(Uri.parse(url));
        return tab.intent;
    }

    /**
     * Opens any other page (VirusTotal's privacy notice) in a full-screen Custom Tab:
     * the user's own browser, over the app, so closing it returns here.
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
            noBrowser(context);
        }
    }

    static void noBrowser(@NonNull Context context) {
        Toast.makeText(context.getApplicationContext(), R.string.no_browser, Toast.LENGTH_LONG).show();
    }
}
