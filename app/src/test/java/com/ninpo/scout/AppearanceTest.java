package com.ninpo.scout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
public class AppearanceTest {

    private Application app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
    }

    @After
    public void reset() {
        // AppCompat keeps the mode in a static field that outlives each test.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    @Test
    public void matchesTheDeviceByDefault() {
        assertEquals(Appearance.AUTO, Appearance.saved(app));
        assertEquals(CustomTabsIntent.COLOR_SCHEME_SYSTEM, Appearance.customTabsColorScheme(app));
    }

    @Test
    public void choiceIsSavedAndReappliedOnTheNextLaunch() {
        Appearance.choose(app, Appearance.DARK);
        assertEquals(Appearance.DARK, Appearance.saved(app));
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());

        // A new process starts with AppCompat's default; ScoutApp re-applies the saved choice.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        Appearance.apply(app);
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void unknownSavedValueFallsBackToMatchDevice() {
        app.getSharedPreferences("scout", Context.MODE_PRIVATE).edit().putInt("appearance", 99).commit();
        assertEquals(Appearance.AUTO, Appearance.saved(app));
    }

    @Test
    public void menuItemsAndModesMatch() {
        for (int mode : new int[] {Appearance.AUTO, Appearance.LIGHT, Appearance.DARK}) {
            assertEquals(mode, Appearance.modeFor(Appearance.menuItem(mode)));
        }
    }

    @Test
    public void reportSheetFollowsTheChoice() {
        Appearance.choose(app, Appearance.DARK);
        assertEquals(CustomTabsIntent.COLOR_SCHEME_DARK, CheckFlow.reportIntent(app, "https://example.com/")
                .getIntExtra(CustomTabsIntent.EXTRA_COLOR_SCHEME, -1));
        Appearance.choose(app, Appearance.LIGHT);
        assertEquals(CustomTabsIntent.COLOR_SCHEME_LIGHT, CheckFlow.reportIntent(app, "https://example.com/")
                .getIntExtra(CustomTabsIntent.EXTRA_COLOR_SCHEME, -1));
    }

    @Test
    public void choosingDarkRebuildsTheScreenInDarkColours() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertFalse(isNight(activity));
                assertEquals("Appearance: Match device", description(activity));
                activity.chooseAppearance(Appearance.DARK);
            });
            ShadowLooper.idleMainLooper();
            scenario.onActivity(activity -> {
                assertTrue(isNight(activity));
                assertEquals("Appearance: Dark", description(activity));
            });
        }
    }

    @Test
    public void choiceWithTheSameColoursJustUpdatesTheButton() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<MainActivity> before = new AtomicReference<>();
            scenario.onActivity(activity -> {
                before.set(activity);
                // The test device is in light mode, so Light changes nothing visible.
                activity.chooseAppearance(Appearance.LIGHT);
            });
            ShadowLooper.idleMainLooper();
            scenario.onActivity(activity -> {
                assertTrue("not rebuilt", activity == before.get());
                assertEquals("Appearance: Light", description(activity));
            });
        }
    }

    private static boolean isNight(MainActivity activity) {
        return (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private static String description(MainActivity activity) {
        View button = activity.findViewById(R.id.appearance);
        return String.valueOf(button.getContentDescription());
    }
}
