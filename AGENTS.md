# Repository Guidelines

## Project Structure

This repository is an Android app written in Kotlin with Jetpack Compose.

- `app/src/main/java/com/zongce/app/`: application entry point, UI, data, export, update, and core rules.
- `app/src/main/java/com/zongce/app/ui/`: Compose screens, shared UI components, theme, and `AppViewModel`.
- `app/src/main/java/com/zongce/app/data/`: Room entities, DAO, database, and private photo storage.
- `app/src/main/java/com/zongce/app/core/`: academic-year, filename, and image-processing rules.
- `app/src/main/java/com/zongce/app/export/`: export validation and ZIP generation.
- `app/src/test/`: JVM unit tests for rules and export checks.
- `app/src/main/res/`: Android manifest resources, strings, launcher assets, and provider paths.
- `docs/adr/`: architecture decision records.

## Build, Test, and Development

Run commands from the repository root (`zongce-android`):

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

The first command creates `app/build/outputs/apk/debug/app-debug.apk`. The second runs JVM unit tests. Use Android Studio's **Run app** action or an attached USB-debugging device for manual verification. Do not commit generated `build/` outputs or local APKs.

## Coding Style and Naming

Use Kotlin official style with four-space indentation and one top-level declaration per logical file. Name classes and composables with `PascalCase`, functions and properties with `camelCase`, and constants with `UPPER_SNAKE_CASE`. Keep UI state and navigation in the existing Compose/ViewModel pattern. Reuse `JicunTheme`, shared components in `UiKit.kt`, and existing domain helpers before adding abstractions. Add a short Chinese comment at the start of newly created or substantially changed source files when it clarifies the module's role.

## Testing Guidelines

Tests use JUnit 4 under `app/src/test/`. Name test classes after the unit under test, for example `AcademicYearTest` or `ExportCheckTest`, and use descriptive method names. Add focused tests when changing academic-year rules, filename generation, photo validation, or export behavior. A successful compilation is not a substitute for unit tests or real-device checks.

## Commits and Pull Requests

Use concise, imperative-style commit messages with the repository's existing prefixes, such as `feat:`, `fix:`, and `docs:`. Keep each commit focused. Pull requests should describe user-visible changes, list validation commands and results, mention migration or compatibility impact, and include emulator/device screenshots for UI changes. Never include secrets, local paths, generated APKs, or private user photos.

## Security and Configuration

Do not commit `local.properties`, signing keys, credentials, update tokens, or personal photos. Photos belong in the app's private storage and should continue to flow through `PhotoStore`. Preserve the existing FileProvider and system camera approach; do not add broad storage or camera permissions without an explicit design decision.
