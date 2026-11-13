package com.ninpo.vtchecker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
public class CheckActivityTest {

    private Application app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
    }

    private void noticeAlreadySeen() {
        app.getSharedPreferences("vt_checker", Context.MODE_PRIVATE)
                .edit().putBoolean("privacy_notice_seen", true).commit();
    }

    private static Intent processText(CharSequence text) {
        return new Intent(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, text);
    }

    private CheckActivity launch(Intent intent) {
        intent.setClass(app, CheckActivity.class);
        ActivityController<CheckActivity> controller = Robolectric.buildActivity(CheckActivity.class, intent);
        controller.setup();
        ShadowLooper.idleMainLooper();
        return controller.get();
    }

    private static String openedUrl(CheckActivity activity) {
        Intent next = shadowOf(activity).getNextStartedActivity();
        assertNotNull("no browser intent was started", next);
        assertEquals(Intent.ACTION_VIEW, next.getAction());
        return next.getDataString();
    }

    private static AlertDialog latestDialog() {
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }

    @Test
    public void selectedLinkOpensItsReportAndCloses() {
        noticeAlreadySeen();
        CheckActivity activity = launch(processText("Visit https://www.google.com/search?q=virus now."));
        assertEquals("https://www.virustotal.com/gui/search?query=https%3A%2F%2Fwww.google.com%2Fsearch%3Fq%3Dvirus",
                openedUrl(activity));
        assertTrue(activity.isFinishing());
    }

    @Test
    public void sharedTextWorksToo() {
        noticeAlreadySeen();
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Phishing page https://evil.example.com/login");
        CheckActivity activity = launch(share);
        assertEquals("https://www.virustotal.com/gui/search?query=https%3A%2F%2Fevil.example.com%2Flogin",
                openedUrl(activity));
    }

    @Test
    public void firstCheckShowsThePrivacyNoticeOnce() {
        CheckActivity activity = launch(processText("8.8.8.8"));
        assertNull(shadowOf(activity).getNextStartedActivity());
        AlertDialog notice = latestDialog();
        assertTrue(notice.isShowing());

        notice.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("https://www.virustotal.com/gui/search?query=8.8.8.8", openedUrl(activity));
        assertTrue(activity.isFinishing());
        assertTrue(app.getSharedPreferences("vt_checker", Context.MODE_PRIVATE)
                .getBoolean("privacy_notice_seen", false));
    }

    @Test
    public void cancellingTheNoticeOpensNothing() {
        CheckActivity activity = launch(processText("8.8.8.8"));
        latestDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertTrue(activity.isFinishing());
        assertFalse(app.getSharedPreferences("vt_checker", Context.MODE_PRIVATE)
                .getBoolean("privacy_notice_seen", false));
    }

    @Test
    public void severalItemsAskWhichOne() {
        noticeAlreadySeen();
        CheckActivity activity = launch(processText("from 203.0.113.9 via evil.net"));
        AlertDialog picker = latestDialog();
        ListView list = picker.getListView();
        assertEquals(2, list.getAdapter().getCount());
        assertNull(shadowOf(activity).getNextStartedActivity());

        shadowOf(list).performItemClick(1);
        ShadowLooper.idleMainLooper();
        assertEquals("https://www.virustotal.com/gui/search?query=evil.net", openedUrl(activity));
        assertTrue(activity.isFinishing());
    }

    @Test
    public void cancellingThePickerCloses() {
        noticeAlreadySeen();
        CheckActivity activity = launch(processText("from 203.0.113.9 via evil.net"));
        latestDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertTrue(activity.isFinishing());
    }

    @Test
    public void textWithNothingToCheckExplainsAndOpensNothing() {
        noticeAlreadySeen();
        CheckActivity activity = launch(processText("see you at lunch"));
        AlertDialog dialog = latestDialog();
        TextView message = dialog.findViewById(android.R.id.message);
        assertEquals(app.getString(R.string.nothing_body), message.getText().toString());
        assertNull(shadowOf(activity).getNextStartedActivity());
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertTrue(activity.isFinishing());
    }

    // The original version crashed here with a NullPointerException.
    @Test
    public void missingExtraDoesNotCrash() {
        CheckActivity activity = launch(new Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"));
        TextView message = latestDialog().findViewById(android.R.id.message);
        assertEquals(app.getString(R.string.nothing_empty_body), message.getText().toString());
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void selectedTextIgnoresOtherActions() {
        assertNull(CheckActivity.selectedText(null));
        assertNull(CheckActivity.selectedText(new Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_TEXT, "8.8.8.8")));
        assertEquals("8.8.8.8", CheckActivity.selectedText(processText("8.8.8.8")).toString());
    }
}
