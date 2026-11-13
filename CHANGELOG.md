# Changelog

## 2.0 (2026-11-13)

VT Checker is now **Ninpo Scout**, rebuilt from the ground up.

### New
- Finds the link, domain, IP address (IPv4 and IPv6) or file hash (MD5, SHA-1 or SHA-256) in any selected text, even a whole sentence or an email header. When there are several, you choose which one to check.
- Reads defanged text such as `hxxps://evil[.]example[.]com` and `8.8.8[.]8`, and checks the domain of an email address.
- Share a link to the app from any app's Share menu.
- **Check copied text**: long-press the app icon to check what's on the clipboard.
- The VirusTotal report opens over the app you're in, as a resizable sheet in Chrome 107 and later. Close it to get back to where you were.
- Check field on the home screen that shows exactly what will be checked.
- **Appearance** setting: Match device, Light or Dark.
- New design with matched light and dark themes, large-text support and TalkBack labels.

### Fixed
- Checking a full link such as `https://example.com/page` opened VirusTotal's "page does not exist" screen.
- Selections with brackets, quotes or surrounding words opened an empty VirusTotal search.
- The app crashed when another app sent it no text.

### Privacy
- Only the link, domain, IP address or hash goes to VirusTotal, never the rest of the selected text.
- Before the first check, a one-time notice explains that VirusTotal scans and shares what it is asked about.
- Copied text that a password manager marks as private is never pasted.
- No account, no ads, no tracking and no permissions.

### Other
- New app ID, `com.ninpo.scout`. If VT Checker 1.0 is installed, remove it after installing Ninpo Scout.
- New app icon.
- Smaller app: the release build is about 1.8 MB, down from 5.2 MB.

## 1.0 (2025-11-08)

First release, as VT Checker: adds "Check on VirusTotal" to the text-selection menu.
