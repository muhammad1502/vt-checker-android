package com.ninpo.scout;

import androidx.annotation.NonNull;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** Links into VirusTotal's website. The app never calls VirusTotal's API. */
public final class VirusTotal {

    /**
     * The address VirusTotal's own search box opens. It resolves links, domains, IP
     * addresses and hashes to their reports. The older /gui/search/&lt;text&gt; form
     * breaks for full links: VirusTotal shows "page does not exist" for
     * /gui/search/https%3A%2F%2F... and blocks the double-encoded form.
     */
    static final String SEARCH = "https://www.virustotal.com/gui/search?query=";

    /** Redirects to the privacy notice VirusTotal currently publishes. */
    public static final String PRIVACY_NOTICE = "https://docs.virustotal.com/docs/privacy-policy";

    private VirusTotal() {}

    @NonNull
    public static String searchUrl(@NonNull Indicator indicator) {
        return SEARCH + encode(indicator.value);
    }

    /** Percent-encodes a query value the way JavaScript's encodeURIComponent does for these inputs. */
    @NonNull
    static String encode(@NonNull String value) {
        try {
            // URLEncoder is form encoding, which writes spaces as "+".
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 is always supported", e);
        }
    }
}
