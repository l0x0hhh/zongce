# Zongce · Comprehensive Assessment Materials

## Capture it when you receive it. Export it when you need it.

Zongce is a local-first Android tool for organizing materials used in student comprehensive assessment. It focuses on the everyday workflow around certificates and awards: capture, record, classify, validate, and export a submission-ready package.

> Zongce is an independent project. It is not affiliated with or endorsed by any school or education authority. Always verify submission requirements through the official school system and notices.

## Install

The project uses GitHub Actions for verification and tagged releases. Push a version tag to build a signed APK and publish a GitHub Release:

```powershell
git clone https://github.com/l0x0hhh/zongce.git
cd zongce
git tag v1.2.0
git push origin v1.2.0
```

When GitHub is slow or unavailable on a domestic network, configure a mainland object-storage or CDN mirror. Place a `latest.json` manifest at the mirror URL with `version`, `title`, `notes`, and `apkUrl` fields. The app checks the mirror first and falls back to GitHub Release when the mirror is unavailable.

Android 8.0 or later is required. You can also open the repository root in Android Studio, wait for Gradle sync, and run the `app` module.

## Get Started

1. Open the app and take a certificate photo or import supporting images from the system picker.
2. Select one of the Five Educations and enter the award name and award date.
3. Add the award level, grade, or ranking when available on the certificate.
4. Review, edit, or delete records from the list.
5. Open the export screen, select the target academic year, and review validation results.
6. Generate the ZIP package after validation, then review it before submission.

The photo date is not the award date. Use the date shown on the certificate or official supporting document.

## Features

- Organize award records by the Five Educations.
- Capture certificates with the camera or import images from the system picker.
- Store award name, award date, award level, grade, or ranking.
- Determine the academic year from the award date.
- Warn about academic-year boundaries and block records outside the target year when required.
- Validate required fields and supporting photos before export.
- Export a ZIP package grouped by education category.
- Generate readable image names using `award_date_award_name_grade.jpg`.
- Handle image rotation, JPEG conversion, and thumbnail previews.
- View, edit, and delete existing records.

## Data and Privacy

- Award records are stored locally with Room.
- Supporting photos are kept in app-private storage; no backend service is required.
- Export files are written to the app cache directory and handed to the user through Android file sharing.
- The app does not automatically upload materials to a server.

Never publish unredacted certificate photos, ZIP exports, logs, or screenshots. They may contain names, student IDs, identity information, and other personal data.

## Development

### Requirements

- Android Studio
- JDK 17
- Android SDK Platform 35
- Network access for downloading Gradle dependencies

### Build and Test

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

The tests primarily cover pure Kotlin rules such as academic-year classification, file-name generation, and pre-export validation. Camera capture, file picking, image importing, ZIP sharing, and Android-version compatibility still require device or emulator verification.

## Architecture

Room is the local source of truth. Compose screens read and update records through `AppViewModel`, photos are managed by `PhotoStore`, export validation is handled by `ExportCheck`, and `ZipExporter` creates the final ZIP package.

The project uses Kotlin, Jetpack Compose, Material 3, AndroidX, Room, and KSP. See [CODE_STRUCTURE.md](CODE_STRUCTURE.md) for detailed module ownership.

## Project Status

Current version: `1.1.0-dev`

The basic record, photo-management, academic-year validation, and ZIP-export workflows are implemented. CI, tagged release, and GitHub Release workflows are configured; release signing still requires repository Secrets. See [VERSION_HISTORY.md](VERSION_HISTORY.md) for version and session updates.

## Help and Contributing

Use GitHub Issues for reproducible bugs and focused feature requests. Remove personal information, certificate images, authentication data, and local paths before sharing issue material.

## License

No open-source license has been declared yet. Add an appropriate License file before public distribution or external contributions.

## Disclaimer

Zongce is not an official school application and does not define any school's assessment rules. School fields, time windows, and material requirements may change; follow official notices.

---

中文版本：[README.md](README.md) · 代码结构：[CODE_STRUCTURE.md](CODE_STRUCTURE.md) · 版本记录：[VERSION_HISTORY.md](VERSION_HISTORY.md)
