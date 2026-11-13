package com.ninpo.scout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class MainActivityTest {

    private static MainActivity launch() {
        return Robolectric.buildActivity(MainActivity.class).setup().get();
    }

    @Test
    public void statusShowsTheCleanedUpItem() {
        MainActivity activity = launch();
        ((EditText) activity.findViewById(R.id.input)).setText("Visit hxxps://evil[.]example[.]com/login now");
        TextView status = activity.findViewById(R.id.status);
        assertEquals("Link: https://evil.example.com/login", status.getText().toString());
    }

    @Test
    public void checkButtonOpensTheReport() {
        ApplicationProvider.getApplicationContext().getSharedPreferences("scout", Context.MODE_PRIVATE)
                .edit().putBoolean("privacy_notice_seen", true).commit();
        MainActivity activity = launch();
        ((EditText) activity.findViewById(R.id.input)).setText("8.8.8.8");
        activity.findViewById(R.id.check_button).performClick();
        Intent next = shadowOf(activity).getNextStartedActivityForResult().intent;
        assertEquals("https://www.virustotal.com/gui/search?query=8.8.8.8", next.getDataString());
    }

    private static ActivityController<MainActivity> launchFromShortcut() {
        Intent shortcut = new Intent(MainActivity.ACTION_CHECK_CLIPBOARD)
                .setClass(ApplicationProvider.getApplicationContext(), MainActivity.class);
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class, shortcut).setup();
        // The clipboard can only be read once the window has focus.
        controller.windowFocusChanged(true);
        ShadowLooper.idleMainLooper();
        return controller;
    }

    private static void copy(ClipData clip) {
        ClipboardManager clipboard = (ClipboardManager) ApplicationProvider.getApplicationContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(clip);
    }

    @Test
    public void shortcutChecksCopiedText() {
        ApplicationProvider.getApplicationContext().getSharedPreferences("scout", Context.MODE_PRIVATE)
                .edit().putBoolean("privacy_notice_seen", true).commit();
        copy(ClipData.newPlainText("copied", "Suspicious sender: billing@paypa1-support.com"));
        MainActivity activity = launchFromShortcut().get();
        assertEquals("Suspicious sender: billing@paypa1-support.com",
                ((EditText) activity.findViewById(R.id.input)).getText().toString());
        Intent next = shadowOf(activity).getNextStartedActivityForResult().intent;
        assertEquals("https://www.virustotal.com/gui/search?query=paypa1-support.com", next.getDataString());
    }

    @Test
    public void shortcutWithNothingCopiedExplains() {
        MainActivity activity = launchFromShortcut().get();
        TextView status = activity.findViewById(R.id.status);
        assertEquals(activity.getString(R.string.clipboard_empty), status.getText().toString());
        assertNull(shadowOf(activity).getNextStartedActivityForResult());
    }

    // Password managers mark copied secrets; those must never reach VirusTotal.
    @Test
    public void textMarkedSensitiveIsNeverPasted() {
        ClipData clip = ClipData.newPlainText("secret", "hunter2.example.com");
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
        clip.getDescription().setExtras(extras);
        copy(clip);
        MainActivity activity = launchFromShortcut().get();
        assertEquals("", ((EditText) activity.findViewById(R.id.input)).getText().toString());
        TextView status = activity.findViewById(R.id.status);
        assertEquals(activity.getString(R.string.clipboard_sensitive), status.getText().toString());
        assertNull(shadowOf(activity).getNextStartedActivityForResult());
    }

    @Test
    public void shortcutIconAndLabelsLoad() {
        Context context = ApplicationProvider.getApplicationContext();
        assertNotNull(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_shortcut_clipboard));
        assertTrue(context.getString(R.string.shortcut_clipboard_short).length() <= 10);
        assertTrue(context.getString(R.string.shortcut_clipboard_long).length() <= 25);
    }

    @Test
    public void normalLaunchDoesNotTouchTheClipboard() {
        copy(ClipData.newPlainText("copied", "8.8.8.8"));
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        controller.windowFocusChanged(true);
        assertEquals("", ((EditText) controller.get().findViewById(R.id.input)).getText().toString());
    }

    @Test
    public void emptyFieldExplainsInsteadOfOpening() {
        MainActivity activity = launch();
        activity.findViewById(R.id.check_button).performClick();
        TextView status = activity.findViewById(R.id.status);
        assertEquals(activity.getString(R.string.check_empty_error), status.getText().toString());
        assertEquals(null, shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void moreDetailsOpensTheSheetWithFormatsPrivacyAndLicenses() {
        MainActivity activity = launch();
        activity.findViewById(R.id.more_details).performClick();
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        Dialog sheet = ShadowDialog.getLatestDialog();
        assertNotNull(sheet);
        assertTrue(sheet.isShowing());

        LinearLayout types = sheet.findViewById(R.id.types);
        // Five rows with four hairlines between them.
        assertEquals(9, types.getChildCount());

        View privacyLink = sheet.findViewById(R.id.privacy_link);
        privacyLink.performClick();
        Intent next = shadowOf(activity).getNextStartedActivity();
        assertEquals(VirusTotal.PRIVACY_NOTICE, next.getDataString());
        assertNotNull(sheet.findViewById(R.id.licenses_link));
    }

    @Test
    public void detailsSheetOpensOnlyOnceOnDoubleTap() {
        MainActivity activity = launch();
        View more = activity.findViewById(R.id.more_details);
        more.performClick();
        ShadowLooper.idleMainLooper();
        more.performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(1, activity.getSupportFragmentManager().getFragments().stream()
                .filter(f -> f instanceof DetailsSheet).count());
    }
}
