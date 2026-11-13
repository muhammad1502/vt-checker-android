#!/usr/bin/env python3
"""Regenerate app/src/main/java/com/ninpo/scout/Tlds.java from IANA's TLD list.

Run from the repository root:  python3 scripts/update_tlds.py

The file is rewritten only when the set of top-level domains changes. IANA bumps the
list's version line almost daily, so comparing whole files would report a change
every time.
"""
import pathlib
import re
import sys
import urllib.request

SOURCE = "https://data.iana.org/TLD/tlds-alpha-by-domain.txt"
TARGET = pathlib.Path("app/src/main/java/com/ninpo/scout/Tlds.java")

TEMPLATE = '''package com.ninpo.scout;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Top-level domains from IANA's root zone list, used to tell a real domain in a
 * sentence ("see example.com") from a file name ("see config.yaml").
 *
 * Source: https://data.iana.org/TLD/tlds-alpha-by-domain.txt
 * {version}
 *
 * Regenerate with scripts/update_tlds.py (a monthly GitHub Actions job does this).
 */
final class Tlds {{

    private Tlds() {{}}

    static final Set<String> ALL = Collections.unmodifiableSet(new HashSet<>(Arrays.asList((""
{body}
            ).trim().split(" "))));

    static boolean contains(String label) {{
        return ALL.contains(label.toLowerCase(java.util.Locale.ROOT));
    }}
}}
'''


def current_set(java: str) -> set:
    words = " ".join(re.findall(r'\+ "([^"]*)"', java)).split()
    return set(words)


def render(version: str, tlds: list) -> str:
    lines, line = [], ""
    for tld in tlds:
        if len(line) + len(tld) + 1 > 100:
            lines.append(line)
            line = ""
        line += tld + " "
    lines.append(line)
    body = "\n".join('            + "%s"' % chunk for chunk in lines)
    return TEMPLATE.format(version=version, body=body)


def main() -> int:
    with urllib.request.urlopen(SOURCE, timeout=30) as response:
        text = response.read().decode("ascii")
    rows = text.splitlines()
    if not rows or not rows[0].startswith("#"):
        print("Unexpected format from " + SOURCE, file=sys.stderr)
        return 1
    version = rows[0].lstrip("# ").strip()
    tlds = sorted(row.strip().lower() for row in rows[1:] if row.strip())
    if len(tlds) < 1000:
        print("Only %d TLDs downloaded; refusing to overwrite." % len(tlds), file=sys.stderr)
        return 1

    old = current_set(TARGET.read_text(encoding="utf-8"))
    new = set(tlds)
    if old == new:
        print("No change: %d top-level domains." % len(new))
        return 0
    added, removed = sorted(new - old), sorted(old - new)
    TARGET.write_text(render(version, tlds), encoding="utf-8")
    print("Updated: %d added %s, %d removed %s" % (len(added), added, len(removed), removed))
    return 0


if __name__ == "__main__":
    sys.exit(main())
