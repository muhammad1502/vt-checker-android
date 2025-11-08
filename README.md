# VT Checker - Android Utility

## Project Description

VT Checker is a lightweight, single-purpose Android utility that adds a powerful "Check on VirusTotal" option directly to your text selection context menu.

This app is designed for security-conscious users, IT professionals, or anyone who frequently needs to check the reputation of an IP address, domain, or URL.

## How It Works

This app uses the Intent.ACTION_PROCESS_TEXT feature, which was introduced in Android 6.0 (Marshmallow). This allows the app to register itself as a valid handler for selected text.

When you select text in any app and tap the overflow menu (...), "Check on VirusTotal" will appear as an option.

## Key Features

No API Key Needed: The app does not use the official VirusTotal API. Instead, it intelligently constructs a VirusTotal search URL and opens it in your default web browser. This makes the app lightweight and maintenance-free.

Versatile: Works seamlessly with:

IP addresses (e.g., 8.8.8.8)

Domains (e.g., google.com)

Full URLs (e.g., https://some-suspicious-link.com/path)

System-Wide Integration: Works in any app that uses the standard Android text selection menu (browsers, notepads, chat apps, etc.).

Minimalist: The app itself is just a "how-to" screen. The core functionality is in the invisible ProcessTextActivity that handles the intent, launches the browser, and immediately closes.

## How to Use

Install the app on your Android device (must be Android 6.0+).

Find any text (IP, domain, or URL) in any app.

Long-press to select the text.

In the floating context menu (Copy, Paste, etc.), tap the ... (overflow) button.

Select "Check on VirusTotal" from the list.

Your default browser will open with the VirusTotal results for that query.
