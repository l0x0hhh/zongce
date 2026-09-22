# Jicun · Comprehensive Assessment Materials (Zongce)

> Save the certificate while it is still in your hand; export it when you need to file it.

Jicun is a **local-first Android app** for managing the supporting materials required by a Chinese university's comprehensive assessment (zongce). It files certificate photos under the five education categories and packages them into an upload-ready folder for the target academic year.

- Project / package name: `zongce` (`com.zongce.app`); in-app name: **Jicun (暨存)**
- Android 8.0 (API 26) or later; targets SDK 35
- No account, no backend service

> **Independence notice**: Jicun is an independent personal project. It is not affiliated with or endorsed by any school or education authority, and it does not define any school's assessment rules. Always check the official school system and notices before submitting.

## Why it exists

The pain of assessment filing shows up at the last minute: certificates pile up in the camera roll, and filing means scrolling through hundreds of photos. The school system also insists on readable file names, a 4 MB per-file limit, and one file per upload. Jicun moves that manual work to the moment the certificate is received — photograph it, tag it with one of the five categories, enter the name and date — and does the organizing automatically at export time.

## Features

- **Five-category filing**: moral, intellectual, physical, aesthetic, and labour education, with one-tap filtering and a "missing fields only" view.
- **Capture or import**: system camera capture, or system photo picker import (no camera or storage permissions requested).
- **Record fields**: award name, award date, award level, grade or ranking, your role, issuing organisation, notes, plus 1..N photos.
- **Automatic academic year**: derived from the award date, rolling on 1 September; the first and last day of the year get an explicit confirmation prompt.
- **Pre-export check**: a missing name, missing date, malformed date, missing category, no photos, or a lost photo file **blocks** the export. Missing level/grade/role, boundary dates, and records from other academic years are **warnings** only.
- **One-tap packaging**: a ZIP grouped by category, plus a plain-text checklist you can open in Notepad.
- **Normalised file names**: `award_date_award_name_grade.jpg`, with sorting that matches chronological order.
- **Image handling**: EXIF rotation correction, HEIC to JPEG conversion, and automatic compression below 3.5 MB at export time.
- **Originals kept forever**: imported photos stay in app-private storage; export converts a copy instead of rewriting the original.
- **In-app update check**: the mirror manifest and the GitHub Release are queried concurrently and the higher version wins (the mirror is just a faster download source for users in China); the APK is then handed to the system installer.

## What the exported package looks like

```
comprehensive-assessment-materials_2025-2026.zip
├── filing-checklist.txt          # UTF-8 with BOM, opens cleanly in Notepad
├── 德育/                         # all five category folders are created, even when empty
│   └── 20251123_全国大学生信息安全竞赛_第1等级.jpg
├── 智育/
├── 体育/
├── 美育/
└── 劳育/
```

- Each photo goes into the **category selected when the record was created**; extra photos of the same record get `_2`, `_3`, and so on.
- The checklist lists every record by category with name, date, level, grade, role, issuing organisation, and the relative photo paths, and ends each entry with an `已填报：☐` checkbox for step-by-step filing.
- File name rule: `YYYYMMDD_name[_grade].jpg`, at most 40 characters including the extension (so the school system shows it in full), name truncated to 20 characters and grade to 10, illegal characters `\ / : * ? " < > |` replaced by `-`. The zero-padded date means **sorting by file name equals sorting by award date**.
- The ZIP is written to a `.part` file and renamed on success, so an interrupted export never leaves a half-written package that could be mistaken for a finished one.

## Data and privacy

- Award records live in a local Room (SQLite) database; original photos live in app-private `filesDir/photos/`, named by content hash and mapped to readable names only at export time.
- No account, no sign-in, and **no feature that uploads materials to a server**.
- Only two permissions are requested: `INTERNET` (update check only) and `REQUEST_INSTALL_PACKAGES` (installing a downloaded update). Capture is handled by the system camera app and import by the system photo picker, so **no camera or storage permission is needed**.
- The only outbound request is the update check: the mirror `latest.json` (when configured) and the GitHub Release API are queried concurrently and the higher version wins. It fetches a version number and a download URL, and sends no material content.
- **System cloud backup is off**: the manifest sets `android:allowBackup="false"`, so Android's automatic backup never includes the app database or photo copies (they never reach Google Drive or a vendor cloud such as Huawei or Xiaomi). This project has no server and needs none; the data physically exists only on this device, unless the user explicitly shares an exported ZIP.
- Never publish unredacted certificate photos, exported ZIPs, logs, or screenshots — they may contain names, student IDs, identity documents, and other personal data.

## Install

### Download

Get the latest `jicun-<version>.apk` from [Releases](https://github.com/l0x0hhh/zongce/releases). The first install requires allowing "install unknown apps" in system settings.

You can also download straight from the product page: **https://jicun.netlify.app**. The installer is hosted by the page itself, so one tap downloads a `.apk` — no redirects, and no "it saved as a `.zip`" surprise.

### Behind a slow network

When GitHub is slow or unreachable, host your own mirror: place a `latest.json` at the mirror root and build with the Gradle property `updateManifestUrl`. The app queries the mirror and the GitHub Release concurrently and picks the higher version — the mirror's value is being a faster download source in China, not the sole authority.

```json
{
  "version": "1.2.0",
  "title": "暨存 1.2.0",
  "notes": "优化照片录入和导出流程",
  "apkUrl": "https://download.example.com/jicun/jicun-1.2.0.apk"
}
```

### Build from source

Open the repository root in Android Studio, wait for Gradle sync, and run the `app` module. From the command line:

```powershell
$env:GRADLE_USER_HOME = "<repo-root>\.gradle-user"   # optional: repo-local, pre-warmed Gradle cache
.\gradlew.bat :app:assembleDebug --no-daemon     # output: app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat :app:testDebugUnitTest --no-daemon # JVM unit tests
```

> **The path must be pure ASCII.** If any directory in the full repository path contains non-ASCII characters (for example Chinese), Gradle worker processes fail to start because of command-line encoding issues, reporting `无法加载主类 worker.org.gradle.process.internal.worker.GradleWorkerMain`. `assembleDebug` may still pass, but `testDebugUnitTest` will always fail. The fix is to keep the repository under an English-only path (for example `E:\AIstudy\project\Jicun`) — changing `GRADLE_USER_HOME` does not help.

## Getting started

1. **Capture**: on the home screen, take a photo or pick images from the gallery.
2. **Fill in the basics**: choose one of the five categories (required — it decides the export folder), plus the award name (required — it becomes part of the file name) and the award date (required). Level, grade, role, and issuing organisation can be filled later; the export check will remind you.
3. **Manage**: browse, filter by category, edit, or delete records on the records screen.
4. **Export**: pick the target academic year, run the pre-export check, generate the ZIP, and share it through the system share sheet (WeChat, cloud drive, desktop, ...).
5. **File it**: work through the checklist entry by entry and tick each one off.

> The photo date is not the award date. Use the date on the certificate or official document; records dated on the first or last day of the academic year get an extra confirmation prompt before export.

## Project layout

```
app/src/main/java/com/zongce/app/
├── MainActivity.kt        # single activity, Compose navigation, update dialog entry
├── WidgetActions.kt       # home-screen widget entry protocol (actions + isEntryAction)
├── widget/                # the widget itself (Glance UI + AppWidget receiver)
├── core/                  # pure Kotlin rules: academic year, file names, image processing
├── data/                  # Room entities, DAO, database, private photo store
├── export/                # pre-export validation and ZIP packaging
├── ui/                    # Compose screens, glass navigation bar, theme, ViewModel
└── update/                # update check and APK download
app/src/test/              # JVM unit tests for the academic year, file names, and export check
docs/adr/                  # architecture decision records
```

Call chain: `MainActivity` → `AppViewModel` (record flow, photo import, export state) → `AwardDao` / `PhotoStore` / `ExportCheck` / `ZipExporter`. Stack: Kotlin + Jetpack Compose (BOM 2024.09.02) + Material 3 + Navigation Compose + Room 2.6.1 (KSP) + ExifInterface, on JDK 17.

See [CODE_STRUCTURE.md](CODE_STRUCTURE.md) for per-module ownership and [AGENTS.md](AGENTS.md) for development and commit conventions.

## Development and release

- **CI** (`.github/workflows/ci.yml`): on pushes to `main` and pull requests, runs `./gradlew test` and `:app:assembleDebug`; pushes also upload the debug APK artifact (kept 14 days).
- **Release** (`.github/workflows/release.yml`): triggered by a `v*.*.*` tag or manually; decodes the signing key, runs tests, builds a signed release APK, renames it to `jicun-<version>.apk`, and creates a GitHub Release (`versionCode = 1000000 + build number`).
- **Signing secrets**: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Keep them in GitHub Actions Secrets; `*.keystore` is git-ignored.
- **Optional mirror (Gitee)**: add one more Secret `GITEE_TOKEN` (a Gitee personal token with the `projects` scope) plus two Variables `GITEE_OWNER` / `GITEE_REPO`, and a release will also upload the APK to a Gitee Release and refresh `latest.json` at a fixed raw path. **If any of the three is missing the whole mirror step is skipped** and the GitHub release is unaffected. The mirror repository needs at least one branch first (create any file on the web UI).
- **Optional product-page sync**: add one Secret `LANDING_TOKEN` (a token with `contents:write` on the landing repo `l0x0hhh/JICUN`; override the repo with the Variable `LANDING_REPO`), and a release will overwrite the landing page's `public/downloads/jicun.apk`, which makes Netlify redeploy. **Skipped when unset** — the landing repo has its own daily workflow that pulls the latest APK as a fallback.
- Without signing properties a local release build still succeeds; the APK is simply unsigned.

## Release process

A release goes in this order (★ = the only two manual steps):

1. **Edit code → push `main`** (automatic): CI runs unit tests and `assembleDebug`. **A green build only means "it compiles and the tests pass"** — not that it works on a device.
2. ★ **Verify on a device (recommended)**: **do not use CI's debug APK** — its `versionCode` is fixed at 2, so it will not install (the released APK is `1000000 + build number`), and it is debug-signed, so a forced install needs an uninstall that also wipes the photos in the app's private storage. Build a signed APK with a version code above the released one instead:
   ```powershell
   .\gradlew.bat :app:assembleRelease -PversionName=1.2.2 -PreleaseVersionCode=1000006 -PsigningStoreFile=release.keystore
   ```
3. ★ **Tag and release**: `git tag v1.2.2 && git push origin v1.2.2` (or trigger the Release workflow manually with a version). The pipeline decodes the signing key, runs tests, builds the signed APK, and creates the GitHub Release.
4. **Mirror and product page** (automatic): the same run also uploads the APK to the Gitee Release, refreshes the mirror's `latest.json`, and syncs the APK into the landing page's `public/downloads/jicun.apk`. Both steps are "run only if configured, skipped otherwise".
5. The landing page is deployed by Netlify watching the repo — nothing else to do.

> Only steps 2 and 3 are manual. Everything else is automatic, so "forgot to update the product page's installer" can no longer happen.

## Known gaps

- **The home-screen widget is wired up.** Long-press the home screen and add "暨存 · 快速录入" (3×2 cells): "拍照" jumps straight into the system camera, "相册" into the photo picker, and tapping the title opens the app normally. The widget only sends an Intent — capture and picking stay in `MainActivity` + `CaptureScreen` (see [ADR-0001](docs/adr/0001-widget-entry-routing.md)).
- **No device verification yet.** Camera capture, photo picking, image import, ZIP sharing, placing the widget, and Android version compatibility still need verification on a real device or emulator. Unit tests cover pure rules only (academic-year classification, file-name generation, export check, version comparison).
- `isMinifyEnabled = false`, so release builds are not yet shrunk or obfuscated by R8.

## Current version

The current version is tracked in [VERSION_HISTORY.md](VERSION_HISTORY.md) (the app `versionName` lives in `app/build.gradle.kts`), which also records the development session history.

## Feedback and contributions

Use GitHub Issues for reproducible bugs and focused feature requests. **Remove all personal information first** — certificate images, exported ZIPs, logs, local paths.

## License

[MIT](LICENSE). Use, modify, and distribute freely, including commercially; just keep the original copyright and license notice.

## Disclaimer

Jicun is not an official application of any school and does not define any school's assessment rules. School fields, time windows, and material requirements can change; follow the official notices before submitting.

---

Chinese: [README.md](README.md) · Code structure: [CODE_STRUCTURE.md](CODE_STRUCTURE.md) · Version history: [VERSION_HISTORY.md](VERSION_HISTORY.md) · Guidelines: [AGENTS.md](AGENTS.md)
