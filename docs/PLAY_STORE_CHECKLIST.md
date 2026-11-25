# Play Store release checklist

## Identity
- [x] Application ID: `com.chargemaster.app` (debug builds use `.debug`)
- [ ] Confirm the "Charge Master" name and check trademark / Play listing availability
- [x] Adaptive launcher icon with a monochrome layer (themed icons)
- [x] Splash screen (`core-splashscreen`)
- [ ] 512×512 hi-res icon and 1024×500 feature graphic
- [ ] Phone screenshots in EN and BN (starting points in `docs/screenshots/`)

## Build
- [x] R8 minification and resource shrinking
- [x] CI: tests, lint and release build on every push (`.github/workflows/android.yml`)
- [x] Predictive back enabled (`enableOnBackInvokedCallback`)
- [x] StrictMode enabled in debug builds
- [x] `bundleRelease` produces an AAB
- [ ] Create the upload key and `keystore.properties`; enroll in Play App Signing
- [ ] Bump `versionCode` / `versionName`
- [ ] Smoke test on real devices: one with µA current, one with mA current, one with inverted sign, one without current support, and Android 8, 10, 13, 14 and 15

## Permissions (final merged manifest)
- `POST_NOTIFICATIONS`: user-enabled alerts
- `RECEIVE_BOOT_COMPLETED`: resume monitoring and close interrupted sessions
- `WAKE_LOCK`: added by WorkManager
- `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE` are explicitly removed
- No INTERNET, location, contacts, SMS, accessibility or overlay permissions

## Data safety form
- Data collected: **None**. No data is transmitted off the device.
- Data shared: **None**. Report sharing is user-initiated through the Android share sheet.
- Data is processed on-device only.
- Users can delete all data in Settings › Data › Clear all history.

## Store listing claims (keep honest)
- Don't claim the app improves battery health, extends battery life, or controls or limits charging.
- Describe health and capacity as *estimates*, available on supported devices.
- Note that some readings (current, charge counter, cycle count) depend on the device.

## Policy
- [ ] Publish the privacy policy (`docs/PRIVACY_POLICY.md`) at a public URL
- [ ] Content rating questionnaire
- [ ] Target audience: not directed at children
