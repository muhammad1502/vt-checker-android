package com.ninpo.vtchecker;

import static org.junit.Assume.assumeTrue;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Renders the screens to PNG files for design review, in light and dark, at large
 * text and at narrow and tablet widths. Skipped unless asked for:
 * <pre>./gradlew testDebugUnitTest -Pscreenshots --tests '*ScreenshotTest'</pre>
 * Output goes to app/build/screenshots/.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
public class ScreenshotTest {

    @Before
    public void onlyWhenAsked() {
        assumeTrue(Boolean.getBoolean("vt.screenshots"));
    }

    @Test
    public void homeLight() throws IOException {
        captureHome("home-light", null);
    }

    @Test
    @Config(qualifiers = "+night")
    public void homeDark() throws IOException {
        captureHome("home-dark", null);
    }

    @Test
    public void homeWithLink() throws IOException {
        captureHome("home-typed-link", "Visit hxxps://evil[.]example[.]com/login now");
    }

    @Test
    @Config(qualifiers = "+night")
    public void homeWithSeveralItemsDark() throws IOException {
        captureHome("home-typed-several-dark", "203.0.113.9 and evil.net");
    }

    @Test
    public void homeLargeText() throws IOException {
        RuntimeEnvironment.setFontScale(2f);
        captureHome("home-font-200", null);
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    public void homeNarrow() throws IOException {
        captureHome("home-320dp", null);
    }

    @Test
    @Config(qualifiers = "w840dp-h1200dp-xhdpi")
    public void homeTablet() throws IOException {
        captureHome("home-tablet", null);
    }

    @Test
    public void privacyNotice() throws IOException {
        captureCheck("dialog-notice-light", "8.8.8.8", false);
    }

    @Test
    @Config(qualifiers = "+night")
    public void pickerDark() throws IOException {
        captureCheck("dialog-picker-dark", "Blocked hxxp://bad[.]example.com/x from 203.0.113.9, payload "
                + "44d88612fea8a8f36de82e1278abb02f", true);
    }

    @Test
    public void pickerLight() throws IOException {
        captureCheck("dialog-picker-light", "203.0.113.9 and evil.net", true);
    }

    @Test
    public void nothingFound() throws IOException {
        captureCheck("dialog-nothing-light", "see you at lunch", true);
    }

    private static void captureHome(String name, String typed) throws IOException {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        if (typed != null) ((EditText) activity.findViewById(R.id.input)).setText(typed);
        ShadowLooper.idleMainLooper();
        View root = activity.getWindow().getDecorView();
        int bg = ContextCompat.getColor(activity, R.color.bg);
        save(root, name + "-screen", bg);

        // The whole scrolling page, with every card in its settled position.
        ViewGroup content = activity.findViewById(R.id.content);
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            child.setTranslationY(0);
            child.setScaleX(1);
            child.setScaleY(1);
        }
        save((View) content.getParent(), name + "-page", bg);
    }

    private static void captureCheck(String name, String text, boolean noticeSeen) throws IOException {
        Context app = ApplicationProvider.getApplicationContext();
        app.getSharedPreferences("vt_checker", Context.MODE_PRIVATE)
                .edit().putBoolean("privacy_notice_seen", noticeSeen).commit();
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_PROCESS_TEXT)
                .setClass(app, CheckActivity.class)
                .setType("text/plain")
                .putExtra(android.content.Intent.EXTRA_PROCESS_TEXT, text);
        CheckActivity activity = Robolectric.buildActivity(CheckActivity.class, intent).setup().get();
        ShadowLooper.idleMainLooper();
        Dialog dialog = ShadowDialog.getLatestDialog();
        View decor = dialog.getWindow().getDecorView();
        // Show the dialog over a mid-grey "app" so its edges are visible.
        save(decor, name, 0xFF8E8E93);
        activity.finish();
    }

    private static void save(View view, String name, int background) throws IOException {
        if (view.getWidth() == 0 || view.getHeight() == 0) {
            view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.AT_MOST));
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        }
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(background);
        view.draw(canvas);
        File dir = new File(System.getProperty("vt.screenshotDir", "build/screenshots"));
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("can't create " + dir);
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
