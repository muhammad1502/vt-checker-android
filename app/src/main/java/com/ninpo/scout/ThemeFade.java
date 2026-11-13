package com.ninpo.scout;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Switching Appearance recreates the screen, which on its own is a hard cut. This
 * keeps a picture of the old screen and fades it out over the new one: a colour
 * crossfade with no movement.
 */
final class ThemeFade {

    private static final long DURATION_MS = 500;
    /** The recreated screen appears well within this; anything older is stale. */
    private static final long MAX_AGE_MS = 2_000;

    @Nullable private static Bitmap snapshot;
    private static long capturedAt;

    private ThemeFade() {}

    /** Call just before the theme changes. */
    static void capture(@NonNull View root) {
        if (root.getWidth() == 0 || root.getHeight() == 0) return;
        try {
            Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            // The page colour comes from the window, not the root view, so paint it first.
            canvas.drawColor(ContextCompat.getColor(root.getContext(), R.color.bg));
            root.draw(canvas);
            snapshot = bitmap;
            capturedAt = SystemClock.elapsedRealtime();
        } catch (OutOfMemoryError e) {
            // A plain cut is fine if there isn't memory for the picture.
            snapshot = null;
        }
    }

    /** Call from the recreated screen's onCreate, after setContentView. */
    static void play(@NonNull FrameLayout root) {
        Bitmap bitmap = snapshot;
        snapshot = null;
        if (bitmap == null || SystemClock.elapsedRealtime() - capturedAt > MAX_AGE_MS) return;
        ImageView cover = new ImageView(root.getContext());
        cover.setImageBitmap(bitmap);
        cover.setScaleType(ImageView.ScaleType.MATRIX);
        cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cover.animate()
                .alpha(0f)
                .setDuration(DURATION_MS)
                // cubic-bezier(.25,.1,.25,1): an even fade with a soft start and end.
                .setInterpolator(new PathInterpolator(0.25f, 0.1f, 0.25f, 1f))
                .withEndAction(() -> root.removeView(cover))
                .start();
    }
}
