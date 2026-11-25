# Build & test

Requirements: JDK 17+ and an Android SDK with platform 35 and build-tools 35. Point to the SDK in `local.properties` (`sdk.dir=...`) or with `ANDROID_HOME`.

Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

Run unit, Robolectric and Compose UI tests (69 tests):

```bash
./gradlew :app:testDebugUnitTest
```

Run lint:

```bash
./gradlew :app:lintDebug
```

Build the release APK and the App Bundle (R8-minified, resource-shrunk):

```bash
./gradlew :app:assembleRelease :app:bundleRelease
```

Outputs:
- `app/build/outputs/apk/release/`
- `app/build/outputs/bundle/release/app-release.aab`

## Release signing

Create `keystore.properties` in the project root. It is git-ignored.

```
storeFile=release.jks
storePassword=...
keyAlias=charge-master
keyPassword=...
```

Without this file, release builds are produced unsigned. Play App Signing can then sign the uploaded bundle.

## Robolectric behind a restricted network

Robolectric downloads Android framework jars at test time. If Maven Central is blocked or rate-limited, point it at a mirror:

```bash
./gradlew :app:testDebugUnitTest -ProbolectricRepoUrl=https://maven-central.storage-download.googleapis.com/maven2/
```

## Reference screenshots

`ScreenshotTest` renders dashboard screenshots (light, dark, Bangla) to `app/build/screenshots/` using Robolectric native graphics.

## Strings

All UI strings, in English and Bangla, live in `tools/strings_table.py`. Regenerate both `strings.xml` files with:

```bash
python3 tools/gen_strings.py
```

The generator fails if format arguments differ between languages.
