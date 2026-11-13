package com.ninpo.scout;

import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/** The home screen: a field to check something right away, how-to, and privacy notes. */
public class MainActivity extends AppCompatActivity implements CheckFlow.Host {

    /**
     * ClipDescription.EXTRA_IS_SENSITIVE, written out because the constant is API 33+
     * while apps were asked to set the same key on older versions too.
     */
    private static final String EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE";


    private static final int[][] STEPS = {
            {R.string.step_1_title, R.string.step_1_body},
            {R.string.step_2_title, R.string.step_2_body},
            {R.string.step_3_title, R.string.step_3_body},
    };

    /** Sent by the "Check copied text" launcher shortcut (res/xml/shortcuts.xml). */
    static final String ACTION_CHECK_CLIPBOARD = "com.ninpo.scout.action.CHECK_CLIPBOARD";

    private final CheckFlow.Session session = new CheckFlow.Session();
    private final ActivityResultLauncher<Intent> report = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> { });
    private final List<View> cards = new ArrayList<>();

    private NestedScrollView scroll;
    private View content;
    private TextInputLayout inputLayout;
    private TextInputEditText input;
    private TextView status;
    private boolean showingError;
    private boolean hasItem;
    private boolean checkClipboardOnFocus;
    private float settleDistance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // After super.onCreate: AppCompat applies a chosen Appearance there, and enabling
        // edge-to-edge first builds the window early, leaving a light page under a dark screen.
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        scroll = findViewById(R.id.scroll);
        content = findViewById(R.id.content);
        inputLayout = findViewById(R.id.input_layout);
        input = findViewById(R.id.input);
        status = findViewById(R.id.status);
        settleDistance = getResources().getDimension(R.dimen.settle_distance);

        applyInsets();
        Rows.addSteps(findViewById(R.id.steps), STEPS);
        cards.add(findViewById(R.id.check_card));
        cards.add(findViewById(R.id.howto_card));
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

        findViewById(R.id.more_details).setOnClickListener(v -> {
            if (getSupportFragmentManager().findFragmentByTag(DetailsSheet.TAG) == null) {
                new DetailsSheet().show(getSupportFragmentManager(), DetailsSheet.TAG);
            }
        });

        ImageButton appearance = findViewById(R.id.appearance);
        TooltipCompat.setTooltipText(appearance, getString(R.string.appearance));
        appearance.setOnClickListener(this::showAppearanceMenu);
        updateAppearanceButton();
        ThemeFade.play(findViewById(R.id.root));

        TextView version = findViewById(R.id.footer_version);
        version.setText(getString(R.string.footer_made_by, BuildConfig.VERSION_NAME));

        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, x, y, oldX, oldY) -> settle());
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> settle());

        checkClipboardOnFocus = savedInstanceState == null
                && ACTION_CHECK_CLIPBOARD.equals(getIntent().getAction());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (ACTION_CHECK_CLIPBOARD.equals(intent.getAction())) {
            checkClipboardOnFocus = true;
            if (hasWindowFocus()) onWindowFocusChanged(true);
        }
    }

    /**
     * Android 10 and later only let the app in focus read the clipboard, so the
     * shortcut's check waits until the window has focus.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || !checkClipboardOnFocus) return;
        checkClipboardOnFocus = false;
        if (paste()) check();
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        updateStatus();
        updateEndIcon();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (hasItem) session.warmUp(this);
    }

    @Override
    protected void onStop() {
        // Don't hold the browser awake while the app is in the background.
        session.coolDown();
        super.onStop();
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
        CheckFlow.start(this, found, session, this);
    }

    private void showAppearanceMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.END);
        menu.inflate(R.menu.appearance);
        menu.getMenu().findItem(Appearance.menuItem(Appearance.saved(this))).setChecked(true);
        menu.setOnMenuItemClickListener(item -> {
            chooseAppearance(Appearance.modeFor(item.getItemId()));
            return true;
        });
        menu.show();
    }

    @VisibleForTesting
    void chooseAppearance(int mode) {
        if (mode == Appearance.saved(this)) return;
        boolean darkNow = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        boolean recreates = Appearance.isDark(mode) != darkNow;
        // The screen is rebuilt in the new colours; fade from a picture of the old one.
        if (recreates) ThemeFade.capture(findViewById(R.id.root));
        Appearance.choose(this, mode);
        // Same colours (say, Dark to Match device on a dark phone): nothing is rebuilt.
        if (!recreates) updateAppearanceButton();
    }

    private void updateAppearanceButton() {
        int mode = Appearance.saved(this);
        ImageButton button = findViewById(R.id.appearance);
        button.setImageResource(Appearance.icon(mode));
        button.setContentDescription(getString(R.string.appearance_description, getString(Appearance.label(mode))));
    }

    @Override
    public void openReport(@NonNull String url) {
        session.coolDown();
        try {
            report.launch(CheckFlow.reportIntent(this, url));
        } catch (ActivityNotFoundException e) {
            CheckFlow.noBrowser(this);
        }
    }

    @Override
    public void cancelled() {
        // Nothing to undo: the text stays in the field.
    }

    /**
     * Shows what will be checked while people type, cleaned up ("Link: https://..."),
     * so they can see a defanged or messy selection was understood. It changes on
     * every keystroke, so TalkBack only announces errors, not these updates.
     */
    private void updateStatus() {
        CharSequence text = input.getText();
        List<Indicator> found = IndicatorParser.parse(text);
        hasItem = !found.isEmpty();
        // Something checkable is in the field, so a tap on Check is likely: start the
        // browser now. Nothing is sent to VirusTotal until the user taps.
        if (hasItem && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            session.warmUp(this);
        }
        if (showingError) return;
        status.setTextColor(ContextCompat.getColor(this, R.color.text_2));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        status.setMaxLines(Integer.MAX_VALUE);
        if (text == null || TextUtils.getTrimmedLength(text) == 0) {
            status.setText(R.string.check_helper);
            return;
        }
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
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
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

    /** Puts the clipboard text in the field. Returns false, with a message, if it couldn't. */
    private boolean paste() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            showError(R.string.clipboard_empty);
            return false;
        }
        // Password managers and keyboards flag copied secrets. Never paste those:
        // whatever is checked gets scanned and shared by VirusTotal.
        ClipDescription description = clip.getDescription();
        if (description != null && description.getExtras() != null
                && description.getExtras().getBoolean(EXTRA_IS_SENSITIVE)) {
            showError(R.string.clipboard_sensitive);
            return false;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (TextUtils.getTrimmedLength(text) == 0) {
            showError(R.string.clipboard_empty);
            return false;
        }
        input.setText(text);
        input.setSelection(input.length());
        return true;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
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
     * The "settle" effect: cards rise 32dp and grow from 96%
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
                float top = content.getTop() + card.getTop() - scroll.getScrollY();
                s = Math.max(0f, Math.min(1f, (height - top) / zone));
            }
            card.setTranslationY((1f - s) * settleDistance);
            card.setScaleX(0.96f + 0.04f * s);
            card.setScaleY(0.96f + 0.04f * s);
        }
    }

}
