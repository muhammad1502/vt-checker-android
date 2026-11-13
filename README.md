# Ninpo Scout

An Android app by Ninpo Inc. that adds **Check on VirusTotal** to the text-selection menu and the Share sheet. Select a link, domain, IP address or file hash in any app, choose Check on VirusTotal, and its VirusTotal report opens over the app you were in.

<p>
  <img src="docs/screenshots/home-light.png" width="240" alt="Home screen in light mode">
  <img src="docs/screenshots/home-dark.png" width="240" alt="Home screen in dark mode">
  <img src="docs/screenshots/home-defanged-link.png" width="240" alt="A defanged link typed into the Check field, shown cleaned up below it">
</p>

## What it does

- **Works from any app.** Long-press text, open the selection menu (⋮ on many phones), tap Check on VirusTotal. Sharing a link to the app works too.
- **Finds what to check in messy text.** A sentence, a log line or an email header is fine: the app picks out links, domains, IPv4 and IPv6 addresses and MD5, SHA-1 and SHA-256 hashes. When there are several it asks which one; when there are none it says so instead of opening an empty search.
- **Reads defanged text.** `hxxps://evil[.]example[.]com`, `8.8.8[.]8` and `user[at]evil[.]com` are cleaned up before checking.
- **Check field on the home screen.** Paste or type, and the line under the field shows exactly what will be checked.
- **Light and dark mode**, large-text support, TalkBack labels and 48dp touch targets.

<img src="docs/screenshots/choose-what-to-check.png" width="360" alt="Dialog listing a link, an IP address and a file hash to choose from">

## Privacy

The app has no account, no ads, no analytics and no permissions, including no internet permission. It opens VirusTotal's website in the user's browser (as a Custom Tab), sending only the item it found, never the rest of the selected text. Before the first check it explains that VirusTotal scans what it is asked about and shares it with the security community, as VirusTotal's [documentation](https://docs.virustotal.com/docs/searching) states.

## How the VirusTotal link works

The app does not use VirusTotal's API. It opens the same address VirusTotal's own search box uses:

```
https://www.virustotal.com/gui/search?query=<percent-encoded item>
```

VirusTotal redirects that to the right report (`/gui/url/…`, `/gui/domain/…`, `/gui/ip-address/…`, `/gui/file/…`). Links typed without a scheme get `http://` added, which is what VirusTotal's own URL scanner does. Version 1.0 used `/gui/search/<item>` in the path, which VirusTotal now answers with "page does not exist" for full URLs. These behaviors were checked against virustotal.com in November 2026; if VirusTotal changes them, `VirusTotal.java` is the one place to update.

## Project layout

| Path | What it is |
|---|---|
| `app/src/main/java/com/ninpo/scout/IndicatorParser.java` | Finds and cleans up links, domains, IPs and hashes in text |
| `app/src/main/java/com/ninpo/scout/Tlds.java` | IANA top-level domain list, so `config.yaml` isn't mistaken for a domain |
| `app/src/main/java/com/ninpo/scout/VirusTotal.java` | Builds the VirusTotal address |
| `app/src/main/java/com/ninpo/scout/CheckActivity.java` | The text-selection and Share handler (no screen of its own) |
| `app/src/main/java/com/ninpo/scout/CheckFlow.java` | Choosing an item, the first-check notice, opening the Custom Tab |
| `app/src/main/java/com/ninpo/scout/MainActivity.java` | Home screen |
| `app/src/main/res/values/colors.xml`, `values-night/colors.xml` | Light and dark color tokens |

The design follows the apple-web-design system: its color tokens, type scale and spacing, the Inter typeface, one pill button style, and WCAG 2.2 AA contrast in both themes.

## Building

Requires Android Studio (or JDK 17+ and the Android SDK with platform 36).

```
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK, shrunk with R8 (sign it with Ninpo's key)
./gradlew testDebugUnitTest      # unit and Robolectric tests
./gradlew lintDebug              # Android lint
./gradlew testDebugUnitTest -Pscreenshots --tests '*ScreenshotTest'
                                 # renders screens to app/build/screenshots/
```

The application ID is `com.ninpo.scout`. It can't change once the app is on Google Play. Version 1.0 was called VT Checker and used `com.example.vtchecker`, so phones with that build will show two apps until the old one is removed.

## Maintenance

- **New top-level domains.** Refresh `Tlds.java` from <https://data.iana.org/TLD/tlds-alpha-by-domain.txt> when ICANN delegates new ones.
- **Android Gradle Plugin 9.** The project stays on AGP 8.13 so it opens in current Android Studio releases without the AGP 9 migration. Moving to AGP 9 and compile SDK 37 is a separate step.

## Licenses

Inter (SIL Open Font License 1.1), Lucide and Feather icons (ISC and MIT), AndroidX and Material Components (Apache 2.0). The full texts ship in the app under Open-source licenses.

Ninpo Scout is not affiliated with or endorsed by VirusTotal.
