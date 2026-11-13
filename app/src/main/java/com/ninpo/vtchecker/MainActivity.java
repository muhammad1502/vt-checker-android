package com.ninpo.vtchecker;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The home screen: a field to check something right away, how-to, and privacy notes. */
public class MainActivity extends AppCompatActivity {

    private static final int[][] STEPS = {
            {R.string.step_1_title, R.string.step_1_body},
            {R.string.step_2_title, R.string.step_2_body},
            {R.string.step_3_title, R.string.step_3_body},
    };

    private static final int[][] TYPES = {
            {R.string.types_links, R.string.types_links_meta},
            {R.string.types_domains, R.string.types_domains_meta},
            {R.string.types_ips, R.string.types_ips_meta},
            {R.string.types_hashes, R.string.types_hashes_meta},
            {R.string.types_defanged, R.string.types_defanged_meta},
    };

    private final CheckFlow.Session session = new CheckFlow.Session();
    private final List<View> cards = new ArrayList<>();

    private NestedScrollView scroll;
    private View content;
    private TextInputLayout inputLayout;
    private TextInputEditText input;
    private TextView status;
    private boolean showingError;
    private float settleDistance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scroll = findViewById(R.id.scroll);
        content = findViewById(R.id.content);
        inputLayout = findViewById(R.id.input_layout);
        input = findViewById(R.id.input);
        status = findViewById(R.id.status);
        settleDistance = getResources().getDimension(R.dimen.settle_distance);

        applyInsets();
        addRows(findViewById(R.id.steps), R.layout.item_step, STEPS, true);
        addRows(findViewById(R.id.types), R.layout.item_row, TYPES, false);
        cards.add(findViewById(R.id.check_card));
        cards.add(findViewById(R.id.howto_card));
        cards.add(findViewById(R.id.types_card));
        cards.add(findViewById(R.id.privacy_card));

        // Wraps long links over up to four lines while keeping the keyboard's Go key.
        input.setHorizontallyScrolling(false);
        input.setMaxLines(4);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                showingError = false;
                updateStatus();
                updateEndIcon();
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                check();
                return true;
            }
            return false;
        });
        updateEndIcon();

        findViewById(R.id.check_button).setOnClickListener(v -> {
            v.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                    ? HapticFeedbackConstants.CONFIRM
                    : HapticFeedbackConstants.VIRTUAL_KEY);
            check();
        });

        TextView privacyLink = findViewById(R.id.privacy_link);
        privacyLink.setContentDescription(getString(R.string.opens_in_browser, getString(R.string.privacy_link)));
        privacyLink.setOnClickListener(v -> CheckFlow.open(this, VirusTotal.PRIVACY_NOTICE));
        findViewById(R.id.licenses_link).setOnClickListener(v -> showLicenses());

        TextView version = findViewById(R.id.footer_version);
        version.setText(getString(R.string.footer_made_by, BuildConfig.VERSION_NAME));

        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, x, y, oldX, oldY) -> settle());
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> settle());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        updateStatus();
        updateEndIcon();
    }

    @Override
    protected void onDestroy() {
        session.end();
        super.onDestroy();
    }

    private void check() {
        CharSequence text = input.getText();
        if (text == null || TextUtils.getTrimmedLength(text) == 0) {
            showError(R.string.check_empty_error);
            input.requestFocus();
            return;
        }
        List<Indicator> found = IndicatorParser.parse(text);
        if (found.isEmpty()) {
            showError(R.string.check_none_error);
            return;
        }
        hideKeyboard();
        CheckFlow.start(this, found, session, null);
    }

    /**
     * Shows what will be checked while people type, cleaned up ("Link: https://..."),
     * so they can see a defanged or messy selection was understood. It changes on
     * every keystroke, so TalkBack only announces errors, not these updates.
     */
    private void updateStatus() {
        if (showingError) return;
        CharSequence text = input.getText();
        status.setTextColor(ContextCompat.getColor(this, R.color.text_2));
        ViewCompat.setAccessibilityLiveRegion(status, ViewCompat.ACCESSIBILITY_LIVE_REGION_NONE);
        status.setMaxLines(Integer.MAX_VALUE);
        if (text == null || TextUtils.getTrimmedLength(text) == 0) {
            status.setText(R.string.check_helper);
            return;
        }
        List<Indicator> found = IndicatorParser.parse(text);
        if (found.isEmpty()) {
            status.setText(R.string.check_found_none);
        } else if (found.size() == 1) {
            Indicator only = found.get(0);
            status.setMaxLines(3);
            status.setText(getString(R.string.check_found_one, getString(only.label()), only.value));
        } else {
            status.setText(getResources().getQuantityString(R.plurals.check_found_many, found.size(), found.size()));
        }
    }

    private void showError(@StringRes int message) {
        showingError = true;
        status.setMaxLines(Integer.MAX_VALUE);
        status.setTextColor(ContextCompat.getColor(this, R.color.error));
        ViewCompat.setAccessibilityLiveRegion(status, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setText(message);
    }

    /** Paste when the field is empty, clear once it has text. */
    private void updateEndIcon() {
        boolean empty = input.getText() == null || input.getText().length() == 0;
        inputLayout.setEndIconDrawable(empty ? R.drawable.ic_paste : R.drawable.ic_clear);
        inputLayout.setEndIconContentDescription(empty ? R.string.paste : R.string.clear_text);
        inputLayout.setEndIconOnClickListener(v -> {
            if (empty) {
                paste();
            } else {
                input.setText(null);
                input.requestFocus();
            }
        });
    }

    private void paste() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (TextUtils.isEmpty(text)) return;
        input.setText(text);
        input.setSelection(input.length());
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }

    private void addRows(LinearLayout parent, int layout, int[][] rows, boolean numbered) {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                View rule = new View(this);
                rule.setBackgroundResource(R.drawable.divider);
                parent.addView(rule, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        getResources().getDimensionPixelSize(R.dimen.hairline)));
            }
            View row = inflater.inflate(layout, parent, false);
            if (numbered) {
                ((TextView) row.findViewById(R.id.step_number)).setText(String.valueOf(i + 1));
                ((TextView) row.findViewById(R.id.step_title)).setText(rows[i][0]);
                ((TextView) row.findViewById(R.id.step_body)).setText(rows[i][1]);
            } else {
                ((TextView) row.findViewById(R.id.row_title)).setText(rows[i][0]);
                ((TextView) row.findViewById(R.id.row_meta)).setText(rows[i][1]);
            }
            if (i == rows.length - 1) row.setPadding(row.getPaddingLeft(), row.getPaddingTop(), row.getPaddingRight(), 0);
            parent.addView(row);
        }
    }

    private void applyInsets() {
        View scrim = findViewById(R.id.status_bar_scrim);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            ViewGroup.LayoutParams lp = scrim.getLayoutParams();
            if (lp.height != bars.top) {
                lp.height = bars.top;
                scrim.setLayoutParams(lp);
            }
            // Keyboard height counts as bottom padding, so the field can scroll above it.
            scroll.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            if (ime.bottom > 0 && input.hasFocus()) {
                scroll.post(() -> inputLayout.requestRectangleOnScreen(
                        new android.graphics.Rect(0, 0, inputLayout.getWidth(), inputLayout.getHeight() + status.getHeight())));
            }
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * The "settle" effect from the web design system: cards rise 32dp and grow from 96%
     * as they move through the bottom 30% of the screen. Driven only by scroll position,
     * so everything is visible and in place without scrolling, and it switches off when
     * the system's "Remove animations" setting is on.
     */
    private void settle() {
        int height = scroll.getHeight();
        if (height == 0) return;
        boolean enabled = ValueAnimator.areAnimatorsEnabled();
        float zone = height * 0.3f;
        for (View card : cards) {
            float s = 1f;
            if (enabled) {
                float top = content.getTop() + card.getTop() - scroll.getScrollY() + scroll.getPaddingTop();
                s = Math.max(0f, Math.min(1f, (height - top) / zone));
            }
            card.setTranslationY((1f - s) * settleDistance);
            card.setScaleX(0.96f + 0.04f * s);
            card.setScaleY(0.96f + 0.04f * s);
        }
    }

    private void showLicenses() {
        String text;
        try (InputStream in = getResources().openRawResource(R.raw.licenses)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
            text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.footer_licenses)
                .setMessage(text)
                .setPositiveButton(R.string.close, null)
                .show();
    }
}
