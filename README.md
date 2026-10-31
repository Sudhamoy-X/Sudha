# SUDHA Browser

A fresh Android browser project using Kotlin, Jetpack Compose and Android WebView.

## Project identity

- App name: SUDHA
- Application ID: `com.sudha.browser`
- Minimum Android: 8.0 (API 26)
- Target SDK: 35
- JDK: 17
- UI: Jetpack Compose + Material 3
- Rendering engine: Android System WebView

## Current implementation

This first foundation commit includes:

- A new SUDHA launcher mark and app identity
- Android project and Gradle configuration
- Basic address/search field
- WebView-based page loading
- Back, forward, reload and home navigation
- Loading progress indicator
- GitHub Actions debug APK workflow

The tab button is currently a clearly labelled placeholder. Multi-tab management, bookmarks, history, download management, private browsing, ad filtering, custom search engines, desktop mode, QR scanning, page translation, find-in-page, advanced settings and the remaining feature-parity work are not implemented yet.

## Via reference

The public `tuyafeng/Via` repository describes itself as a localization repository and contains translated Android string resources rather than the complete browser application source. Its repository metadata does not declare a software licence. This project therefore starts as an independent implementation and does not copy Via source files or proprietary branding.

Feature research reference: https://play.google.com/store/apps/details?id=mark.via.gp

## Build

GitHub Actions builds a debug APK on pushes and pull requests to `main`, and on manual workflow dispatch.

After a successful run, download the `SUDHA-debug-apk` artifact from the Actions run page.

## Development status

This is an early foundation build, not a feature-complete browser. Each browser subsystem must be implemented and tested before claiming parity with Via.
