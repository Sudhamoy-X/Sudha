# SUDHA Browser

Independent Android browser built with Kotlin, Jetpack Compose and Android System WebView.

## Identity and platform

- Application ID: `com.sudha.browser`
- Minimum Android: API 26 (Android 8)
- Target SDK: 35
- JDK: 17
- Web engine: Android System WebView

## Current browser implementation

The project currently contains the SUDHA browser shell, multi-tab UI, WebView navigation, download handoff, bookmarks stored locally, find-in-page, settings scaffolding, and an in-app update screen. Some reference-browser settings remain UI-only or are not yet implemented. Do not treat those placeholders as active security controls.

## In-app updates

SUDHA checks the latest public GitHub Release at:

`https://api.github.com/repos/Sudhamoy-X/Sudha/releases/latest`

A release must contain exactly these assets:

- `SUDHA-release.apk`
- `SUDHA-release.apk.sha256`

The updater downloads the APK into app-private storage, verifies the SHA-256 checksum, then hands the APK to Android's package installer through a scoped FileProvider URI. Android requires the user to allow installs from SUDHA and confirm the package installation. Silent installation is not available to an ordinary Play-distributed Android app.

### Important: one-time migration from debug APK

Debug APKs built by GitHub Actions use the Android debug signing identity and cannot be safely upgraded by a separately signed production APK. Before using the updater, install the first signed release build once. After that, keep the same release keystore for every future release. Do not lose the keystore: a different signing key cannot update the existing installed app.

## Configure permanent release signing

Create a private Android upload/release keystore on a trusted computer. Keep a secure offline backup. Never commit the keystore or passwords to this repository.

Add these GitHub repository Actions secrets under Settings -> Secrets and variables -> Actions:

- `ANDROID_KEYSTORE_BASE64`: Base64-encoded keystore file
- `ANDROID_KEYSTORE_PASSWORD`: keystore password
- `ANDROID_KEY_ALIAS`: key alias
- `ANDROID_KEY_PASSWORD`: key password

On Linux/macOS, base64 encode the keystore file as a single line before saving it as the secret. Use the same keystore for the lifetime of this application.

Push a semantic version tag such as `v0.2.0`. The release workflow builds a signed APK, creates a SHA-256 checksum, and publishes both assets to GitHub Releases. The updater checks the latest published release.

## CI builds

The `android-apk.yml` workflow produces a debug APK for development testing. Debug artifacts are not the distribution channel for in-app updates. Use signed GitHub Releases for installable upgrades.

## Via reference and licensing

The public Via GitHub repository supplied for research is primarily a localization repository, not the complete Android browser source. SUDHA is an independent implementation and does not use Via branding or claim to contain Via's proprietary source.
