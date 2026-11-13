package com.ninpo.scout;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * "More details" from the home screen: what can be checked, the full privacy notes,
 * VirusTotal's privacy notice and the open-source licenses. Kept out of the home
 * screen so it stays short (progressive disclosure). A fragment, so it survives
 * rotation.
 */
public class DetailsSheet extends BottomSheetDialogFragment {

    static final String TAG = "details";

    private static final int[][] TYPES = {
            {R.string.types_links, R.string.types_links_meta},
            {R.string.types_domains, R.string.types_domains_meta},
            {R.string.types_ips, R.string.types_ips_meta},
            {R.string.types_hashes, R.string.types_hashes_meta},
            {R.string.types_defanged, R.string.types_defanged_meta},
    };

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        // Open fully: the sheet is about one screen tall, and a half-open first state
        // would hide the privacy part people opened it for.
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.sheet_details, container, false);
        Rows.addRows(view.findViewById(R.id.types), TYPES);

        TextView privacyLink = view.findViewById(R.id.privacy_link);
        privacyLink.setContentDescription(getString(R.string.opens_in_browser, getString(R.string.privacy_link)));
        privacyLink.setOnClickListener(v -> CheckFlow.open(requireContext(), VirusTotal.PRIVACY_NOTICE));
        view.findViewById(R.id.licenses_link).setOnClickListener(v -> showLicenses());
        return view;
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
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.footer_licenses)
                .setMessage(text)
                .setPositiveButton(R.string.close, null)
                .show();
    }
}
