# Building Spartial Music

Everything below is Windows-first (PowerShell or Git Bash). macOS and Linux work
the same way with the POSIX equivalent of each command.

- [1. Install the tools](#1-install-the-tools)
- [2. Get the code](#2-get-the-code)
- [3. Add your keys](#3-add-your-keys)
- [4. Build and run](#4-build-and-run)
- [5. Create a release keystore](#5-create-a-release-keystore)
- [6. Add GitHub secrets](#6-add-github-secrets)
- [7. Publish a release](#7-publish-a-release)
- [Troubleshooting](#troubleshooting)

## 1. Install the tools

| Tool | Version | Notes |
| --- | --- | --- |
| Android Studio | latest stable | brings the Android SDK |
| JDK | **17** | Android Studio bundles one ("jbr"); a standalone Temurin 17 also works |
| Android SDK | API 37.0 platform, Build-Tools 37.0.0, Platform-Tools | install from SDK Manager |

Set `JAVA_HOME` if Gradle cannot find a JDK:

```powershell
setx JAVA_HOME "C:\Program Files\Java\jdk-17"
```

Then open a **new** terminal so the variable is picked up. Check it:

```powershell
java -version   # must print 17.x
```

Gradle itself is provided by the wrapper — you do not need to install Gradle.

## 2. Get the code

```powershell
git clone https://github.com/NullTrigger/Nodes.git
cd Nodes
```

`--recurse-submodules` is a no-op today (the project has none) but keeps the
clone correct if assets are ever split out.

Create `local.properties` with your SDK path. Android Studio does this
automatically when you open the project; by hand it is:

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

`local.properties` and `keystore.properties` are git-ignored. Never commit them.

## 3. Add your keys

Both keys are **optional**: without them the app builds and runs with the
fictional placeholder catalogue, and the YouTube source shows a setup hint.

```powershell
Copy-Item local.properties.example local.properties   # if it does not exist yet
notepad local.properties
```

- `YOUTUBE_API_KEY` — Google Cloud → enable *YouTube Data API v3* → create an API
  key → restrict it to that API and to your package name + SHA-1. Free tier is
  10,000 quota units/day; Spartial Music counts units locally and stops politely.
- `YOUTUBE_OAUTH_CLIENT_ID` — Android OAuth client for package `app.nodes`
  (use `app.nodes.debug` for debug builds) with your signing SHA-1. Only needed
  for the optional "Sign in with Google" playlist import.
- `JAMENDO_CLIENT_ID` — free at <https://devportal.jamendo.com/>.

Details, quota table and the legal boundaries: [YOUTUBE.md](YOUTUBE.md).

Get your debug SHA-1 (needed for the OAuth client):

```powershell
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

## 4. Build and run

Windows (cmd/PowerShell):

```powershell
.\gradlew.bat assembleDebug
```

Git Bash / macOS / Linux:

```bash
./gradlew assembleDebug
```

The APK lands in `app\build\outputs\apk\debug\app-debug.apk`.

Spartial Music builds in two flavours, one `store` dimension:

- **gms** — for devices with Google Play services. This is the only flavour
  with Cast support (`media3-cast` + Play services Cast framework).
- **foss** — the Free-Software build, no Google binaries pulled in at all.
  The Cast button is absent; everything else is identical.

Both assemble with plain `assembleDebug`; to build one flavour explicitly:

```powershell
.\gradlew.bat :app:assembleGmsDebug      # app\build\outputs\apk\gms\debug\
.\gradlew.bat :app:assembleFossDebug     # app\build\outputs\apk\foss\debug\
```

Install it:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Or press **Run ▶** in Android Studio.

Other useful tasks:

```powershell
.\gradlew.bat :app:testGmsDebugUnitTest :app:testFossDebugUnitTest   # unit tests (both flavours)
.\gradlew.bat :app:lintGmsDebug         # Android lint (fails the build on errors); lintFossDebug for the other flavour
.\gradlew.bat :app:assembleGmsRelease :app:assembleFossRelease   # release builds (need a keystore, see below)
```

## 5. Create a release keystore

Run once, from the repository root. Pick a strong password and **store it in a
password manager** — losing it means you can never update the app on Play, and
losing the file means you must publish under a new package name.

```powershell
keytool -genkeypair -v `
  -keystore nodes-release.jks `
  -alias nodes `
  -keyalg RSA -keysize 4096 -validity 10000 `
  -storetype JKS
```

`keytool` prompts for the keystore password, the key password, and your name and
organisation. Then create the build config:

```powershell
Copy-Item keystore.properties.example keystore.properties
notepad keystore.properties
```

```properties
storeFile=nodes-release.jks
storePassword=your-keystore-password
keyAlias=nodes
keyPassword=your-key-password
```

Back the file up somewhere safe and offline (it is git-ignored on purpose):

```powershell
Copy-Item nodes-release.jks "$env:USERPROFILE\Documents\nodes-release-backup.jks"
```

Verify the release build is signed with your key, not the debug key:

```powershell
.\gradlew.bat :app:assembleGmsRelease :app:assembleFossRelease
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\gms\release\*.apk
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\foss\release\*.apk
```

If `keystore.properties` is missing, the release builds fall back to debug
signing with a warning — good enough for testing, never for distribution.

## 6. Add GitHub secrets

Encode the keystore as base64 (PowerShell, single line, no wrapping):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("nodes-release.jks")) | Set-Clipboard
```

In GitHub: **Settings → Secrets and variables → Actions → New repository secret**.

| Secret | Value | Required |
| --- | --- | --- |
| `KEYSTORE_BASE64` | the clipboard contents from above | yes, for release |
| `KEYSTORE_PASSWORD` | your keystore password | yes, for release |
| `KEY_ALIAS` | `nodes` | yes, for release |
| `KEY_PASSWORD` | your key password | yes, for release |
| `YOUTUBE_API_KEY` | your API key | optional |

Paste secrets straight into the GitHub form. Never put them in a file, an issue,
a commit or a chat message. If a keystore is ever exposed, create a new one and
rotate — a leaked signing key can be used to ship updates that look like yours.

## 7. Publish a release

Bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit, then tag:

```powershell
git tag v0.1.0
git push origin v0.1.0
```

Two workflow files publish the APKs — one file per flavour, each building a
single APK and attaching it (plus its `.sha256`) to the same `v*` tag GitHub
Release:

- `.github/workflows/release.yml` — the FOSS APK, `nodes-foss-<version>.apk`.
- `.github/workflows/release-gms.yml` — the GMS APK, `nodes-gms-<version>.apk`
  (Play services + Cast).

Both decode the keystore, write `keystore.properties`, run their flavour's
`assemble<Flavour>Release` task, and verify the signature before publishing;
if the two workflows race while creating the release on a fresh tag, re-run
the failed one and it uploads into the existing release.

For F-Droid, the same `fastlane/metadata/android/en-US/` files are picked up
automatically; F-Droid signs with its own key, so a separate build flavour is not
required.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `SDK location not found` | create `local.properties` with `sdk.dir=...` (section 2) |
| `Unsupported class file major version` | you are on a newer JDK; set `JAVA_HOME` to JDK 17 |
| `The 'org.jetbrains.kotlin.android' plugin is no longer required` | AGP 9 has built-in Kotlin; remove that plugin from `build.gradle.kts` |
| `Module was compiled with an incompatible version of Kotlin` | a dependency needs a newer Kotlin than AGP bundles; see the note below |
| `Using kotlin.sourceSets DSL ... is not allowed` | keep `android.disallowKotlinSourceSets=false` in `gradle.properties` (KSP needs it) |
| Lint fails the build on a new API | read the report in `app/build/reports/lint-results-debug.html` and fix the cause, or annotate with a reason |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | `adb uninstall app.nodes` first; debug builds use `app.nodes.debug` |

### Kotlin version note

AGP 9 compiles Kotlin itself, so the Kotlin compiler version is tied to the
Android Gradle Plugin, not to `libs.versions.toml`. Dependencies published with a
newer Kotlin metadata version than the bundled compiler will fail with
`Module was compiled with an incompatible version of Kotlin`. The fix is to
either update AGP (which raises the bundled compiler) or pin the dependency to a
version built with the same or older Kotlin. Do not add
`-Xskip-metadata-version-check`: it turns a clear error into runtime crashes.
