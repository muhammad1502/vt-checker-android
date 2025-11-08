package com.example.vtchecker;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class ProcessTextActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Get the text that the user selected
        // We use .toString() to handle CharSequence
        String selectedText = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString();

        if (selectedText != null && !selectedText.isEmpty()) {
            try {
                // 2. Build the VirusTotal URL
                // This is the key to making it work for IPs, URLs, and domains.
                // We MUST URL-encode the selected text, otherwise a URL like
                // "https://google.com" will break the search path.

                String encodedQuery = URLEncoder.encode(selectedText, "UTF-8");
                String virusTotalUrl = "https://www.virustotal.com/gui/search/" + encodedQuery;

                // 3. Create a browser Intent
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(virusTotalUrl));

                // 4. Launch the browser
                startActivity(browserIntent);

            } catch (UnsupportedEncodingException e) {
                // This should never happen with UTF-8
                e.printStackTrace();
                Toast.makeText(this, "Error encoding text", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show();
        }

        // 5. Close this invisible activity
        // The user should just see the browser open and this app "disappear".
        finish();
    }
}