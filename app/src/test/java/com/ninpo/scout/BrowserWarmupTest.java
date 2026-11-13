package com.ninpo.scout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.browser.customtabs.CustomTabsService;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
public class BrowserWarmupTest {

    private static final String BROWSER = "com.example.browser";

    private Application app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
    }

    /** Registers a default browser that offers a Custom Tabs service. */
    private void installBrowser() throws Exception {
        ShadowPackageManager pm = shadowOf(app.getPackageManager());
        ResolveInfo view = new ResolveInfo();
        view.activityInfo = new ActivityInfo();
        view.activityInfo.packageName = BROWSER;
        view.activityInfo.name = BROWSER + ".Main";
        pm.addResolveInfoForIntent(new Intent(Intent.ACTION_VIEW, Uri.parse("http://")), view);
        ComponentName service = new ComponentName(BROWSER, BROWSER + ".TabsService");
        pm.addServiceIfNotPresent(service);
        pm.addIntentFilterForService(service, new IntentFilter(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION));
    }

    private int bindings() {
        return shadowOf(app).getBoundServiceConnections().size();
    }

    @Test
    public void withoutACustomTabsBrowserNothingIsBound() {
        BrowserWarmup warmup = new BrowserWarmup();
        warmup.start(app);
        assertFalse(warmup.isStarted());
        assertEquals(0, bindings());
    }

    @Test
    public void startBindsOnceAndStopReleases() throws Exception {
        installBrowser();
        BrowserWarmup warmup = new BrowserWarmup();
        warmup.start(app);
        warmup.start(app);
        ShadowLooper.idleMainLooper();
        assertTrue(warmup.isStarted());
        assertEquals(1, bindings());
        warmup.stop();
        assertFalse(warmup.isStarted());
        assertEquals(0, bindings());
    }

    @Test
    public void oneItemOpensStraightAwayWithoutWarmingUp() throws Exception {
        installBrowser();
        app.getSharedPreferences("scout", Context.MODE_PRIVATE)
                .edit().putBoolean("privacy_notice_seen", true).commit();
        launchCheck("8.8.8.8");
        assertEquals(0, bindings());
    }

    @Test
    public void pickerWarmsUpAndClosingReleases() throws Exception {
        installBrowser();
        app.getSharedPreferences("scout", Context.MODE_PRIVATE)
                .edit().putBoolean("privacy_notice_seen", true).commit();
        ActivityController<CheckActivity> controller = launchCheck("203.0.113.9 and evil.net");
        assertEquals(1, bindings());

        ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        ShadowLooper.idleMainLooper();
        controller.pause().stop().destroy();
        assertEquals(0, bindings());
    }

    @Test
    public void homeFieldWarmsUpOnlyOnceSomethingCheckableIsTyped() throws Exception {
        installBrowser();
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        EditText input = controller.get().findViewById(R.id.input);

        input.setText("see you at lunch");
        assertEquals(0, bindings());

        input.setText("8.8.8.8");
        assertEquals(1, bindings());

        controller.pause().stop();
        assertEquals("released in the background", 0, bindings());
        controller.start();
        assertEquals("warmed up again on return", 1, bindings());
        controller.resume().pause().stop().destroy();
        assertEquals(0, bindings());
    }

    private ActivityController<CheckActivity> launchCheck(String text) {
        Intent intent = new Intent(Intent.ACTION_PROCESS_TEXT)
                .setClass(app, CheckActivity.class)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, text);
        ActivityController<CheckActivity> controller = Robolectric.buildActivity(CheckActivity.class, intent).setup();
        ShadowLooper.idleMainLooper();
        return controller;
    }
}
