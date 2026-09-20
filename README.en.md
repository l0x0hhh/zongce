# Zongce Android App

An Android app for organizing materials used in student comprehensive assessment. It helps students record awards when they receive them, keep supporting photos, and export a structured ZIP package when the materials are needed for submission.

[中文说明](README.md)

## Features

- Organize personal award records by the five education categories (the “Five Educations”).
- Take certificate photos with the camera or import supporting images from the system picker.
- Store award name, award date, award level, grade or ranking, and related metadata.
- Persist records locally with Room and store photos in app-private storage.
- Determine the academic year from the award date and warn about academic-year boundary dates.
- Validate required fields, academic-year membership, and supporting photos before export.
- Export a ZIP package grouped by education category.
- Generate image names using the `award_date_award_name_grade.jpg` convention.
- View, edit, and delete existing records.

## Tech Stack

- Kotlin
- Jetpack Compose and Material 3
- AndroidX Navigation, Lifecycle, and ViewModel
- Room and KSP
- Gradle Kotlin DSL

Current project settings: `minSdk 26`, `targetSdk 35`, Java/Kotlin JVM target 17, and application version `1.0`.

## Project Structure

```text
app/src/main/java/com/zongce/app/
├── core/       Academic-year rules, file-name rules, image processing
├── data/       Room database, award records, and photo storage
├── export/     Pre-export validation and ZIP generation
└── ui/         Compose screens and ViewModel

app/src/test/   Unit tests for academic years, file names, and export checks
```

## Getting Started

### Requirements

- Android Studio with support for Android Gradle Plugin 8.5.2.
- JDK 17.
- Android SDK 35.
- Network access for downloading Gradle dependencies.

### Build a Debug APK

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

The APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

You can also open the project root in Android Studio and run the `app` module.

### Run Tests

```powershell
.\gradlew.bat test
```

The tests primarily cover pure Kotlin rules, including academic-year classification, file-name generation, and pre-export validation. Camera capture, file picking, image importing, and ZIP sharing should still be verified on an Android device or emulator.

## Export Rules

The target academic year is determined from the current date. Export is blocked when a record is missing its award name, award date, or supporting photo. Boundary dates receive an additional confirmation warning. The exported ZIP contains folders for the five education categories, and image names combine the award date, award name, and grade. Multiple photos belonging to the same record receive numeric suffixes.

## Data and Privacy

The current project uses local storage and does not require a backend service. Award records and supporting photos are stored on the device by default. Export files are written to the app cache directory and handed to the user through Android's file-sharing mechanism. Users should back up important materials and review the exported package before submitting it to a school system.

## Project Status

The project is an early working prototype. The repository does not currently include release signing configuration, a published APK, or an automated CI/CD workflow.

## License

No open-source license has been declared yet. Add an appropriate License file before public distribution.
