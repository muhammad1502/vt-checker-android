package com.ninpo.scout;

import static com.ninpo.scout.Indicator.Type.DOMAIN;
import static com.ninpo.scout.Indicator.Type.FILE_HASH;
import static com.ninpo.scout.Indicator.Type.IP_ADDRESS;
import static com.ninpo.scout.Indicator.Type.URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class IndicatorParserTest {

    private static List<Indicator> parse(String text) {
        return IndicatorParser.parse(text);
    }

    private static List<Indicator> list(Indicator... items) {
        return Arrays.asList(items);
    }

    private static Indicator i(Indicator.Type type, String value) {
        return new Indicator(type, value);
    }

    @Test
    public void emptyAndBlankFindNothing() {
        assertEquals(Collections.emptyList(), parse(null));
        assertEquals(Collections.emptyList(), parse(""));
        assertEquals(Collections.emptyList(), parse("   \n\t "));
    }

    @Test
    public void singleItems() {
        assertEquals(list(i(IP_ADDRESS, "8.8.8.8")), parse("8.8.8.8"));
        assertEquals(list(i(DOMAIN, "google.com")), parse("google.com"));
        assertEquals(list(i(URL, "https://www.google.com/")), parse("https://www.google.com/"));
        assertEquals(list(i(IP_ADDRESS, "2001:4860:4860::8888")), parse("2001:4860:4860::8888"));
    }

    @Test
    public void hashesAreLowercasedAndSized() {
        String sha256 = "275A021BBFB6489E54D471899F7DB9D1663FC695EC2FE2A2C4538AABF651FD0F";
        assertEquals(list(i(FILE_HASH, sha256.toLowerCase())), parse(sha256));
        assertEquals(list(i(FILE_HASH, "44d88612fea8a8f36de82e1278abb02f")), parse("44d88612fea8a8f36de82e1278abb02f"));
        assertEquals(list(i(FILE_HASH, "3395856ce81f2b7382dee72602f798b642f14140")),
                parse("3395856ce81f2b7382dee72602f798b642f14140"));
        assertEquals(R.string.type_md5, parse("44d88612fea8a8f36de82e1278abb02f").get(0).label());
        assertEquals(R.string.type_sha1, parse("3395856ce81f2b7382dee72602f798b642f14140").get(0).label());
        assertEquals(R.string.type_sha256, parse(sha256).get(0).label());
    }

    @Test
    public void hexOfOtherLengthsIsNotAHash() {
        assertTrue(parse("44d88612fea8a8f36de82e1278abb02fab").isEmpty());
        // SHA-512: VirusTotal search doesn't take it.
        assertTrue(parse("cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce"
                + "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e").isEmpty());
    }

    // Each of these, sent to VirusTotal as-is, lands on an empty results page.
    @Test
    public void punctuationAroundTheSelectionIsDropped() {
        assertEquals(list(i(IP_ADDRESS, "8.8.8.8")), parse("(8.8.8.8)"));
        assertEquals(list(i(URL, "https://www.google.com/")), parse("\"https://www.google.com/\""));
        assertEquals(list(i(URL, "https://www.google.com/")), parse("<https://www.google.com/>"));
        assertEquals(list(i(URL, "https://www.google.com/")), parse("Visit https://www.google.com/ now."));
        assertEquals(list(i(DOMAIN, "google.com")), parse("google.com."));
        assertEquals(list(i(IP_ADDRESS, "8.8.8.8")), parse("  8.8.8.8\n"));
    }

    @Test
    public void urlsKeepBalancedBracketsAndQueryStrings() {
        assertEquals(list(i(URL, "https://en.wikipedia.org/wiki/Mercury_(planet)")),
                parse("(see https://en.wikipedia.org/wiki/Mercury_(planet))"));
        assertEquals(list(i(URL, "https://www.google.com/search?q=virus&hl=en")),
                parse("Search: https://www.google.com/search?q=virus&hl=en."));
    }

    @Test
    public void defangedTextIsRefanged() {
        assertEquals(list(i(URL, "https://www.google.com/")), parse("hxxps://www[.]google[.]com/"));
        assertEquals(list(i(URL, "http://evil.example.com/a")), parse("hXXp[://]evil(.)example{.}com/a"));
        assertEquals(list(i(DOMAIN, "google.com")), parse("google[dot]com"));
        assertEquals(list(i(IP_ADDRESS, "1.2.3.4")), parse("1[.]2[.]3[.]4"));
        assertEquals(list(i(DOMAIN, "evil.com")), parse("billing[at]evil[.]com"));
    }

    @Test
    public void linksWithoutASchemeGetHttpLikeVirusTotalsOwnScanner() {
        assertEquals(list(i(URL, "http://google.com/maps")), parse("google.com/maps"));
        assertEquals(list(i(URL, "http://www.google.com/search?q=virus")), parse("www.google.com/search?q=virus"));
        assertEquals(list(i(URL, "http://example.com:8080/admin")), parse("example.com:8080/admin"));
        // A bare trailing slash or port is still just the domain.
        assertEquals(list(i(DOMAIN, "www.google.com")), parse("www.google.com/"));
        assertEquals(list(i(DOMAIN, "google.com")), parse("google.com:443"));
    }

    @Test
    public void domainOfAnEmailAddressButNotTheAddress() {
        assertEquals(list(i(DOMAIN, "example.com")), parse("someone@example.com"));
        assertEquals(list(i(DOMAIN, "paypa1-support.com")), parse("From: Billing <billing@paypa1-support.com>"));
    }

    @Test
    public void domainsAreLowercased() {
        assertEquals(list(i(DOMAIN, "www.example.com")), parse("WWW.Example.COM"));
    }

    @Test
    public void fileNamesAndProseAreNotDomains() {
        assertTrue(parse("hello world").isEmpty());
        assertTrue(parse("Open config.yaml and report.pdf").isEmpty());
        assertTrue(parse("e.g. i.e. a.m. U.S.").isEmpty());
        assertTrue(parse("invoice.exe").isEmpty());
    }

    @Test
    public void realTldsThatLookLikeExtensionsStillCount() {
        // .zip, .mov and .ai are delegated TLDs, so these are real domain names.
        assertEquals(list(i(DOMAIN, "invoice.zip")), parse("invoice.zip"));
        assertEquals(list(i(DOMAIN, "example.ai")), parse("example.ai"));
    }

    @Test
    public void ipv4Details() {
        assertEquals(list(i(IP_ADDRESS, "8.8.8.8")), parse("8.8.8.8:53"));
        assertEquals(list(i(IP_ADDRESS, "10.0.0.0")), parse("10.0.0.0/8"));
        assertEquals(list(i(IP_ADDRESS, "10.0.0.1")), parse("src=10.0.0.1"));
        assertTrue(parse("1.2.3.4.5").isEmpty());
        assertTrue(parse("256.1.1.1").isEmpty());
        assertTrue(parse("01.2.3.4").isEmpty());
        // A hostname that embeds an IP is a domain.
        assertEquals(list(i(DOMAIN, "1.2.3.4.nip.io")), parse("1.2.3.4.nip.io"));
    }

    @Test
    public void ipv6Details() {
        assertTrue(IndicatorParser.isIpv6("::1"));
        assertTrue(IndicatorParser.isIpv6("2001:db8::"));
        assertTrue(IndicatorParser.isIpv6("1:2:3:4:5:6:7:8"));
        assertTrue(IndicatorParser.isIpv6("::ffff:192.0.2.1"));
        assertFalse(IndicatorParser.isIpv6("::"));
        assertFalse(IndicatorParser.isIpv6("12:30:45"));
        assertFalse(IndicatorParser.isIpv6("00:1a:2b:3c:4d:5e"));
        assertFalse(IndicatorParser.isIpv6("1::2::3"));
        assertFalse(IndicatorParser.isIpv6("12345::1"));
        assertEquals(list(i(IP_ADDRESS, "fe80::1")), parse("fe80::1%eth0"));
        assertTrue(parse("Meeting at 12:30:45").isEmpty());
    }

    @Test
    public void severalItemsKeepTheirOrderWithoutDuplicates() {
        String text = "Blocked hxxp://bad[.]example.com/x from 203.0.113.9, payload "
                + "44d88612fea8a8f36de82e1278abb02f. Same IP again: 203.0.113.9. Also see evil.net";
        assertEquals(list(
                i(URL, "http://bad.example.com/x"),
                i(IP_ADDRESS, "203.0.113.9"),
                i(FILE_HASH, "44d88612fea8a8f36de82e1278abb02f"),
                i(DOMAIN, "evil.net")), parse(text));
    }

    @Test
    public void partsOfALinkAreNotListedSeparately() {
        assertEquals(list(i(URL, "https://1.2.3.4/download/44d88612fea8a8f36de82e1278abb02f")),
                parse("https://1.2.3.4/download/44d88612fea8a8f36de82e1278abb02f"));
        assertEquals(list(i(URL, "http://example.com/go?to=evil.com")), parse("example.com/go?to=evil.com"));
    }

    @Test
    public void invisibleCharactersFromCopiedTextAreIgnored() {
        assertEquals(list(i(DOMAIN, "example.com")), parse("exam​ple.com"));
        assertEquals(list(i(IP_ADDRESS, "8.8.8.8")), parse(" 8.8.8.8 "));
    }

    @Test
    public void resultsAreCapped() {
        StringBuilder text = new StringBuilder();
        for (int n = 1; n <= 40; n++) text.append("10.0.0.").append(n).append(' ');
        assertEquals(IndicatorParser.MAX_RESULTS, parse(text.toString()).size());
    }

    @Test
    public void hugeInputIsHandledQuickly() {
        StringBuilder text = new StringBuilder();
        while (text.length() < 200_000) text.append("a.").append("b-c.");
        text.append("1");
        long start = System.nanoTime();
        parse(text.toString());
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue("took " + millis + " ms", millis < 2_000);
    }

    @Test
    public void tldListIsLoaded() {
        assertTrue(Tlds.contains("com"));
        assertTrue(Tlds.contains("XN--P1AI"));
        assertFalse(Tlds.contains("yaml"));
        assertTrue(Tlds.ALL.size() > 1_000);
    }
}
