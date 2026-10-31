# SUDHA Browser feature-parity roadmap

Reference: Via Browser Google Play listing (checked 31 Oct 2026): https://play.google.com/store/apps/details?id=mark.via.gp

The listing describes these feature areas. This roadmap is a target, not a claim that they are already implemented.

## Phase 0 — Foundation (started)
- [x] Fresh Kotlin / Jetpack Compose Android project
- [x] SUDHA app identity and launcher mark
- [x] Android WebView rendering
- [x] Address/search input
- [x] Back, forward, reload and home
- [x] GitHub Actions debug APK workflow
- [ ] Confirm a successful CI build and install on a real device

## Phase 1 — Core browsing
- [ ] Multi-tab model, tab switcher, close/restore tabs
- [ ] Home page shortcuts and customizable homepage
- [ ] Bookmarks, folders, import/export HTML
- [ ] Browsing history and clear-data controls
- [ ] Download manager with progress, notifications and file handling
- [ ] Find in page, page save / offline reading
- [ ] Desktop site / custom user-agent controls
- [ ] Search engine selection and search shortcuts

## Phase 2 — Privacy and content controls
- [ ] Private / incognito tabs with isolated storage
- [ ] Built-in ad and tracker filtering with updateable filter lists
- [ ] Per-site JavaScript, image, cookie and ad-filter settings
- [ ] Pop-up and unwanted redirect controls
- [ ] Clear browsing data and privacy dashboard
- [ ] Permission prompts for camera, microphone, location and files
- [ ] Safe handling of external intents and downloads

## Phase 3 — Customization and media
- [ ] Light / dark / night mode
- [ ] Address bar placement and toolbar action customization
- [ ] Homepage wallpaper, shortcuts and layout settings
- [ ] Video full-screen and orientation behaviour
- [ ] QR code scanner
- [ ] Page translation integration
- [ ] Font-size and zoom controls

## Phase 4 — Quality and release
- [ ] Browser lifecycle and process-death recovery
- [ ] Accessibility and large-screen layouts
- [ ] Unit and UI tests for navigation, tabs, downloads and privacy
- [ ] Performance and memory profiling
- [ ] Release signing through user-managed GitHub secrets
- [ ] Release APK / AAB workflow and versioning

## Engineering notes
- Use Android System WebView as the page renderer, matching the approach described in Via's public README.
- Keep browser data local by default. Any future sync must be opt-in and documented.
- Do not claim feature parity until each item has implementation and test evidence.
- Keep SUDHA name, icon, package ID and visual assets distinct from Via.
- Review licences and third-party notices before adding external code or filter lists.
