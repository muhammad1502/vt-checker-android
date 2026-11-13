package com.ninpo.scout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;
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
        app.getSharedPreferences("scout", Context.MODE_PRIVATE)
                .edit().putBoolean("privacy_notice_seen", true).commit();
    }

    private static Intent processText(CharSequence text) {
        return new Intent(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, text);
    }

    private CheckActivity launch(Intent intent) {
        return controller(intent).get();
    }

    private ActivityController<CheckActivity> controller(Intent intent) {
        intent.setClass(app, CheckActivity.class);
        ActivityController<CheckActivity> controller = Robolectric.buildActivity(CheckActivity.class, intent);
        controller.setup();
        ShadowLooper.idleMainLooper();
        return controller;
    }

    /** The report intent the activity started for a result, as Chrome requires for a sheet. */
    private static ShadowActivity.IntentForResult report(CheckActivity activity) {
        ShadowActivity.IntentForResult next = shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull("no report was opened", next);
        assertEquals(Intent.ACTION_VIEW, next.intent.getAction());
        return next;
    }

    private static String openedUrl(CheckActivity activity) {
        return report(activity).intent.getDataString();
    }

    private static void assertNothingOpened(CheckActivity activity) {
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertNull(shadowOf(activity).getNextStartedActivityForResult());
    }

    /** Closing the report sheet returns a result; the invisible activity then closes. */
    private static void closeReport(CheckActivity activity, ShadowActivity.IntentForResult opened) {
        shadowOf(activity).receiveResult(opened.intent, Activity.RESULT_CANCELED, null);
        ShadowLooper.idleMainLooper();
    }

    private static AlertDialog latestDialog() {
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }

    @Test
    public void selectedLinkOpensItsReportAndClosesWithIt() {
        noticeAlreadySeen();
        CheckActivity activity = launch(processText("Visit https://www.google.com/search?q=virus now."));
        ShadowActivity.IntentForResult opened = report(activity);
        assertEquals("https://www.virustotal.com/gui/search?query=https%3A%2F%2Fwww.google.com%2Fsearch%3Fq%3Dvirus",
                opened.intent.getDataString());
        // Stays (invisible) under the report sheet so the sheet has something to return to.
        assertFalse(activity.isFinishing());
        closeReport(activity, opened);
        assertTrue(activity.isFinishing());
    }

    @Test
    public void reportOpensAsAResizableSheetWithRoundedCorners() {
        noticeAlreadySeen();
        CheckActivity activity = launch(processText("8.8.8.8"));
        Intent intent = report(activity).intent;
        int screen = activity.getResources().getDisplayMetrics().heightPixels;
        int height = CustomTabsIntent.getInitialActivityHeightPx(intent);
        assertTrue("sheet height " + height + " of " + screen, height >= screen / 2 && height < screen);
        assertEquals(CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE, CustomTabsIntent.getActivityResizeBehavior(intent));
        assertEquals(16, CustomTabsIntent.getToolbarCornerRadiusDp(intent));
    }

    @Test
    public void restartWhileTheReportIsOpenDoesNotOpenItAgain() {
        noticeAlreadySeen();
        Intent intent = processText("8.8.8.8").setClass(app, CheckActivity.class);
        ActivityController<CheckActivity> first = controller(intent);
        report(first.get());
        android.os.Bundle saved = new android.os.Bundle();
        first.saveInstanceState(saved);

        ActivityController<CheckActivity> second = Robolectric.buildActivity(CheckActivity.class, intent).setup(saved);
        ShadowLooper.idleMainLooper();
        assertNull(shadowOf(second.get()).getNextStartedActivityForResult());
        assertFalse(second.get().isFinishing());
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
        assertNothingOpened(activity);
        AlertDialog notice = latestDialog();
        assertTrue(notice.isShowing());

        notice.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        ShadowActivity.IntentForResult opened = report(activity);
        assertEquals("https://www.virustotal.com/gui/search?query=8.8.8.8", opened.intent.getDataString());
        assertFalse(activity.isFinishing());
        closeReport(activity, opened);
        assertTrue(activity.isFinishing());
        assertTrue(app.getSharedPreferences("scout", Context.MODE_PRIVATE)
                .getBoolean("privacy_notice_seen", false));
    }

    @Test
    public void cancellingTheNoticeOpensNothing() {
        CheckActivity activity = launch(processText("8.8.8.8"));
        latestDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertNull(shadowOf(activity).getNextStartedActivityForResult());
        assertTrue(activity.isFinishing());
        assertFalse(app.getSharedPreferences("scout", Context.MODE_PRIVATE)
                .getBoolean("privacy_notice_seen", false));
    }

    @Test
    public void severalItemsAskWhichOne() {
        noticeAlreadySeen();
        CheckActivity activity = launch(processText("from 203.0.113.9 via evil.net"));
        AlertDialog picker = latestDialog();
        ListView list = picker.getListView();
        assertEquals(2, list.getAdapter().getCount());
        assertNothingOpened(activity);

        shadowOf(list).performItemClick(1);
        ShadowLooper.idleMainLooper();
        ShadowActivity.IntentForResult opened = report(activity);
        assertEquals("https://www.virustotal.com/gui/search?query=evil.net", opened.intent.getDataString());
        closeReport(activity, opened);
        assertTrue(activity.isFinishing());
    }

    @Test
    public void cancellingThePickerCloses() {
        noticeAlreadySeen();
        CheckActivity activity = launch(processText("from 203.0.113.9 via evil.net"));
        latestDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertNothingOpened(activity);
        assertTrue(activity.isFinishing());
    }

    @Test
    public void textWithNothingToCheckExplainsAndOpensNothing() {
        noticeAlreadySeen();
        CheckActivity activity = launch(processText("see you at lunch"));
        AlertDialog dialog = latestDialog();
        TextView message = dialog.findViewById(android.R.id.message);
        assertEquals(app.getString(R.string.nothing_body), message.getText().toString());
        assertNothingOpened(activity);
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
        assertNothingOpened(activity);
    }

    @Test
    public void selectedTextIgnoresOtherActions() {
        assertNull(CheckActivity.selectedText(null));
        assertNull(CheckActivity.selectedText(new Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_TEXT, "8.8.8.8")));
        assertEquals("8.8.8.8", CheckActivity.selectedText(processText("8.8.8.8")).toString());
    }
}
