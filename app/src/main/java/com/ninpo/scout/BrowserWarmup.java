package com.ninpo.scout;

import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsServiceConnection;

/**
 * Starts the user's browser in the background while they are about to check
 * something, so the report opens sooner. Chrome documents warmup() as saving up to
 * 700 ms when the link is opened.
 *
 * Only used when there is a pause: the "choose what to check" list, the first-check
 * notice, or typing in the home screen field. It deliberately never calls
 * mayLaunchUrl(): that pre-fetches the page, which would send the item to VirusTotal
 * (where it gets scanned and shared) before the user has tapped anything.
 */
final class BrowserWarmup {

    @Nullable private Context boundTo;
    @Nullable private CustomTabsServiceConnection connection;

    /** Safe to call repeatedly; only the first call while started does any work. */
    void start(@NonNull Context context) {
        if (connection != null) return;
        String browser = CustomTabsClient.getPackageName(context, null);
        if (browser == null) return;

        CustomTabsServiceConnection c = new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(@NonNull ComponentName name,
                                                     @NonNull CustomTabsClient client) {
                try {
                    client.warmup(0);
                } catch (RuntimeException e) {
                    // Warm-up is only a speed-up; a misbehaving browser mustn't crash the app.
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // The browser went away; the next start() binds again.
                connection = null;
                boundTo = null;
            }
        };
        try {
            if (CustomTabsClient.bindCustomTabsService(context, browser, c)) {
                connection = c;
                boundTo = context;
            }
        } catch (SecurityException e) {
            // A browser that refuses the binding just doesn't get warmed up.
        }
    }

    void stop() {
        if (connection == null || boundTo == null) return;
        try {
            boundTo.unbindService(connection);
        } catch (IllegalArgumentException e) {
            // Already unbound by the system.
        }
        connection = null;
        boundTo = null;
    }

    boolean isStarted() {
        return connection != null;
    }
}
