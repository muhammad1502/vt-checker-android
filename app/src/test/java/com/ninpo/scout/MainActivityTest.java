package com.ninpo.scout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
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
        Intent next = shadowOf(activity).getNextStartedActivity();
        assertEquals("https://www.virustotal.com/gui/search?query=8.8.8.8", next.getDataString());
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
