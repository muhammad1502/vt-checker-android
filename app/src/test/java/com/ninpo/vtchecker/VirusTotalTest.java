package com.ninpo.vtchecker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Expected links were checked against virustotal.com in November 2026. */
public class VirusTotalTest {

    private static String url(Indicator.Type type, String value) {
        return VirusTotal.searchUrl(new Indicator(type, value));
    }

    @Test
    public void usesTheQueryParameterFormLikeVirusTotalsSearchBox() {
        assertEquals("https://www.virustotal.com/gui/search?query=8.8.8.8",
                url(Indicator.Type.IP_ADDRESS, "8.8.8.8"));
        assertEquals("https://www.virustotal.com/gui/search?query=google.com",
                url(Indicator.Type.DOMAIN, "google.com"));
    }

    @Test
    public void encodesLinksLikeEncodeUriComponent() {
        assertEquals("https://www.virustotal.com/gui/search?query=https%3A%2F%2Fwww.google.com%2Fsearch%3Fq%3Dvirus%26hl%3Den",
                url(Indicator.Type.URL, "https://www.google.com/search?q=virus&hl=en"));
        assertEquals("https://www.virustotal.com/gui/search?query=2001%3A4860%3A4860%3A%3A8888",
                url(Indicator.Type.IP_ADDRESS, "2001:4860:4860::8888"));
    }

    @Test
    public void spacesAndUnicodeArePercentEncoded() {
        assertEquals("a%20b%2Bc", VirusTotal.encode("a b+c"));
        assertEquals("%C3%A9", VirusTotal.encode("é"));
        assertEquals("%23frag%25", VirusTotal.encode("#frag%"));
    }
}
