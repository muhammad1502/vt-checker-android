package com.ninpo.scout;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.browser.customtabs.CustomTabsIntent;

/**
 * The Appearance setting: match the device (the default), always light, or always
 * dark. AppCompat applies the mode but forgets it when the app closes, so the
 * choice is saved here and re-applied by {@link ScoutApp} on every launch.
 */
final class Appearance {

    private static final String PREFS = "scout";
    private static final String KEY = "appearance";

    static final int AUTO = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    static final int LIGHT = AppCompatDelegate.MODE_NIGHT_NO;
    static final int DARK = AppCompatDelegate.MODE_NIGHT_YES;

    private Appearance() {}

    static int saved(@NonNull Context context) {
        int mode = prefs(context).getInt(KEY, AUTO);
        return mode == LIGHT || mode == DARK ? mode : AUTO;
    }

    static void apply(@NonNull Context context) {
        AppCompatDelegate.setDefaultNightMode(saved(context));
    }

    /** Saves and applies a choice. Open screens are recreated if the colours change. */
    static void choose(@NonNull Context context, int mode) {
        prefs(context).edit().putInt(KEY, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    /** Whether a mode would show dark colours on this device right now. */
    static boolean isDark(int mode) {
        if (mode == DARK) return true;
        if (mode == LIGHT) return false;
        int system = Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return system == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Keeps the report sheet's browser toolbar in step with the app. */
    static int customTabsColorScheme(@NonNull Context context) {
        switch (saved(context)) {
            case LIGHT:
                return CustomTabsIntent.COLOR_SCHEME_LIGHT;
            case DARK:
                return CustomTabsIntent.COLOR_SCHEME_DARK;
            default:
                return CustomTabsIntent.COLOR_SCHEME_SYSTEM;
        }
    }

    @DrawableRes
    static int icon(int mode) {
        if (mode == LIGHT) return R.drawable.ic_theme_light;
        if (mode == DARK) return R.drawable.ic_theme_dark;
        return R.drawable.ic_theme_auto;
    }

    @StringRes
    static int label(int mode) {
        if (mode == LIGHT) return R.string.appearance_light;
        if (mode == DARK) return R.string.appearance_dark;
        return R.string.appearance_auto;
    }

    @IdRes
    static int menuItem(int mode) {
        if (mode == LIGHT) return R.id.appearance_light;
        if (mode == DARK) return R.id.appearance_dark;
        return R.id.appearance_auto;
    }

    static int modeFor(@IdRes int menuItem) {
        if (menuItem == R.id.appearance_light) return LIGHT;
        if (menuItem == R.id.appearance_dark) return DARK;
        return AUTO;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
