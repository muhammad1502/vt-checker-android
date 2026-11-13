package com.ninpo.scout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the links, domains, IP addresses and file hashes in a piece of selected text.
 *
 * Only these are ever sent to VirusTotal. Its web search scans and shares whatever it
 * is asked about, so passing the rest of a selection along (names, email addresses,
 * message text) would leak it for no benefit. It also returns nothing useful: a
 * selection like "(8.8.8.8)" or "Visit https://example.com now." lands on an empty
 * results page.
 */
public final class IndicatorParser {

    static final int MAX_INPUT_CHARS = 20_000;
    static final int MAX_TOKEN_CHARS = 2_048;
    static final int MAX_RESULTS = 20;

    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200D\\u2060\\uFEFF]");
    private static final Pattern ODD_SPACE = Pattern.compile("[\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]");

    // Defanging styles used in threat reports and mail filters: hxxp, [.], (dot), [:] and so on.
    private static final Pattern FANG_SCHEME = Pattern.compile("(?i)\\bhxxp(s?)(?=\\[?:)");
    private static final Pattern FANG_FTP = Pattern.compile("(?i)\\bfxp(?=\\[?:)");
    private static final Pattern FANG_SLASHES = Pattern.compile("\\[\\s*:\\s*//\\s*]");
    private static final Pattern FANG_COLON = Pattern.compile("\\[\\s*:\\s*]");
    private static final Pattern FANG_SLASH = Pattern.compile("\\[\\s*/\\s*]");
    private static final Pattern FANG_DOT = Pattern.compile("(?i) ?[\\[({]\\s*(?:\\.|dot)\\s*[\\])}] ?");
    private static final Pattern FANG_AT = Pattern.compile("(?i)\\[\\s*(?:@|at)\\s*]");

    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?|ftp)://[^\\s<>\"'`{}|\\\\^]+");
    // Characters that can appear inside a bare domain, IP address, hash or scheme-less link.
    // Everything else (spaces, brackets, quotes, commas, @) separates candidates.
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9._:/?=&#%~+\\-]+");
    private static final Pattern DOMAIN = Pattern.compile(
            "(?<![A-Za-z0-9_\\-])(?:[A-Za-z0-9\\-]{1,63}\\.)+(?:[A-Za-z]{2,63}|[Xx][Nn]--[A-Za-z0-9\\-]{1,59})(?![A-Za-z0-9_\\-])");
    private static final Pattern PORT = Pattern.compile(":[0-9]{1,5}");
    private static final Pattern HASH = Pattern.compile(
            "(?<![A-Za-z0-9])(?:[A-Fa-f0-9]{64}|[A-Fa-f0-9]{40}|[A-Fa-f0-9]{32})(?![A-Za-z0-9])");
    private static final Pattern HEX_RUN = Pattern.compile("[0-9A-Fa-f:.]+");
    private static final String OCTET = "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![0-9.])" + OCTET + "(?:\\." + OCTET + "){3}(?![0-9]|\\.[0-9])");
    private static final Pattern IPV4_EXACT = Pattern.compile(OCTET + "(?:\\." + OCTET + "){3}");

    private IndicatorParser() {}

    /** Every indicator in {@code raw}, in the order they appear, without duplicates. */
    @NonNull
    public static List<Indicator> parse(@Nullable CharSequence raw) {
        if (raw == null) return Collections.emptyList();
        String text = raw.length() > MAX_INPUT_CHARS
                ? raw.subSequence(0, MAX_INPUT_CHARS).toString()
                : raw.toString();
        text = refang(normalize(text));

        List<Found> found = new ArrayList<>();
        char[] rest = text.toCharArray();

        Matcher m = URL.matcher(text);
        while (m.find()) {
            String url = trimEnd(m.group());
            if (hasHost(url)) found.add(new Found(m.start(), new Indicator(Indicator.Type.URL, url)));
            blank(rest, m.start(), m.end());
        }

        Matcher tokens = TOKEN.matcher(new String(rest));
        while (tokens.find()) {
            if (tokens.end() - tokens.start() <= MAX_TOKEN_CHARS) {
                scanToken(tokens.group(), tokens.start(), found);
            }
        }

        // Stable sort, so two finds at the same spot keep their discovery order.
        Collections.sort(found, (a, b) -> Integer.compare(a.position, b.position));
        LinkedHashSet<Indicator> unique = new LinkedHashSet<>();
        for (Found f : found) {
            unique.add(f.indicator);
            if (unique.size() == MAX_RESULTS) break;
        }
        return new ArrayList<>(unique);
    }

    // Domains go first because a name like 1.2.3.4.nip.io also contains an IPv4 address.
    // Hashes, IPv6 and IPv4 can never match the domain pattern (its last label is letters).
    private static void scanToken(String token, int offset, List<Found> found) {
        char[] chars = token.toCharArray();

        Matcher m = DOMAIN.matcher(token);
        int consumed = 0;
        while (m.find()) {
            if (m.start() < consumed) continue;
            String host = m.group();
            if (!isHostname(host)) continue;

            int end = m.end();
            String port = "";
            Matcher p = PORT.matcher(token).region(end, token.length());
            if (p.lookingAt()) {
                port = p.group();
                end = p.end();
            }
            String path = "";
            if (end < token.length() && "/?#".indexOf(token.charAt(end)) >= 0) {
                path = trimEnd(token.substring(end));
            }

            Indicator indicator;
            if (path.isEmpty() || path.equals("/")) {
                indicator = new Indicator(Indicator.Type.DOMAIN, host.toLowerCase(Locale.ROOT));
            } else {
                // VirusTotal's own URL scanner assumes http:// for links typed without a
                // scheme (google.com/maps gets the report for http://google.com/maps), and
                // its search box finds nothing for them, so add it here the same way.
                indicator = new Indicator(Indicator.Type.URL, "http://" + host + port + path);
            }
            found.add(new Found(offset + m.start(), indicator));
            consumed = end + path.length();
            blank(chars, m.start(), consumed);
        }

        String s = new String(chars);
        m = HASH.matcher(s);
        while (m.find()) {
            found.add(new Found(offset + m.start(),
                    new Indicator(Indicator.Type.FILE_HASH, m.group().toLowerCase(Locale.ROOT))));
            blank(chars, m.start(), m.end());
        }

        s = new String(chars);
        m = HEX_RUN.matcher(s);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (isAlnumAt(s, start - 1) || isAlnumAt(s, end)) continue;
            while (end > start && s.charAt(end - 1) == '.') end--;
            if (end - start > 1 && s.charAt(end - 1) == ':' && s.charAt(end - 2) != ':') end--;
            String candidate = s.substring(start, end);
            if (isIpv6(candidate)) {
                found.add(new Found(offset + start,
                        new Indicator(Indicator.Type.IP_ADDRESS, candidate.toLowerCase(Locale.ROOT))));
                blank(chars, start, end);
            }
        }

        s = new String(chars);
        m = IPV4.matcher(s);
        while (m.find()) {
            found.add(new Found(offset + m.start(), new Indicator(Indicator.Type.IP_ADDRESS, m.group())));
        }
    }

    static String normalize(String text) {
        return ODD_SPACE.matcher(INVISIBLE.matcher(text).replaceAll("")).replaceAll(" ");
    }

    static String refang(String text) {
        text = FANG_SCHEME.matcher(text).replaceAll("http$1");
        text = FANG_FTP.matcher(text).replaceAll("ftp");
        text = FANG_SLASHES.matcher(text).replaceAll("://");
        text = FANG_COLON.matcher(text).replaceAll(":");
        text = FANG_SLASH.matcher(text).replaceAll("/");
        text = FANG_DOT.matcher(text).replaceAll(".");
        return FANG_AT.matcher(text).replaceAll("@");
    }

    /** Drops sentence punctuation and unbalanced closing brackets from the end of a link. */
    static String trimEnd(String link) {
        int end = link.length();
        while (end > 0) {
            char c = link.charAt(end - 1);
            if (".,;:!?*".indexOf(c) >= 0
                    || (c == ')' && count(link, end, '(') < count(link, end, ')'))
                    || (c == ']' && count(link, end, '[') < count(link, end, ']'))) {
                end--;
            } else {
                break;
            }
        }
        return link.substring(0, end);
    }

    private static boolean hasHost(String url) {
        int i = url.indexOf("://") + 3;
        return i < url.length() && (Character.isLetterOrDigit(url.charAt(i)) || url.charAt(i) == '[');
    }

    static boolean isHostname(String host) {
        if (host.length() > 253) return false;
        String[] labels = host.split("\\.");
        if (labels.length < 2) return false;
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
                return false;
            }
        }
        return Tlds.contains(labels[labels.length - 1]);
    }

    static boolean isIpv6(String s) {
        if (s.length() < 2 || s.length() > 45) return false;
        int lastColon = s.lastIndexOf(':');
        if (lastColon < 0) return false;
        String head = s;
        String tail = s.substring(lastColon + 1);
        if (tail.indexOf('.') >= 0) {
            // An embedded IPv4 address (::ffff:192.0.2.1) stands in for the last two groups.
            if (!IPV4_EXACT.matcher(tail).matches()) return false;
            head = s.substring(0, lastColon + 1) + "0:0";
        }
        if (head.indexOf('.') >= 0) return false;

        int gap = head.indexOf("::");
        if (gap < 0) return groups(head) == 8;
        if (head.indexOf("::", gap + 1) >= 0) return false;
        int left = groups(head.substring(0, gap));
        int right = groups(head.substring(gap + 2));
        if (left < 0 || right < 0) return false;
        // A bare "::" is the unspecified address; nothing to look up.
        int total = left + right;
        return total >= 1 && total <= 7;
    }

    /** Number of 1 to 4 digit hex groups in a colon-separated run, or -1 if malformed. */
    private static int groups(String part) {
        if (part.isEmpty()) return 0;
        String[] groups = part.split(":", -1);
        for (String g : groups) {
            if (g.isEmpty() || g.length() > 4) return -1;
            for (int i = 0; i < g.length(); i++) {
                if (Character.digit(g.charAt(i), 16) < 0) return -1;
            }
        }
        return groups.length;
    }

    private static boolean isAlnumAt(String s, int i) {
        if (i < 0 || i >= s.length()) return false;
        char c = s.charAt(i);
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private static int count(String s, int end, char c) {
        int n = 0;
        for (int i = 0; i < end; i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private static void blank(char[] chars, int start, int end) {
        for (int i = start; i < end && i < chars.length; i++) chars[i] = ' ';
    }

    private static final class Found {
        final int position;
        final Indicator indicator;

        Found(int position, Indicator indicator) {
            this.position = position;
            this.indicator = indicator;
        }
    }
}
