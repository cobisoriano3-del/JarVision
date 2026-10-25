# JarVision

JarVision is a native Android file manager and universal code workspace. It aims to combine reliable everyday file operations, powerful archive workflows, a capable text editor, and persistent projects in one coherent app.

## Product principles

- **Universal by default:** not tied to Minecraft, one language, or one application.
- **Single-pane first:** normal browsing uses one location; **Dual Space** is optional.
- **Projects are first-class:** work in an app-managed workspace and export explicitly when ready.
- **Honest permissions:** normal file access works independently of Shizuku; restricted operations are capability-checked.
- **Protect user data:** clear conflict handling, safe writes, recoverable deletion where possible, and explicit warnings for irreversible actions.
- **No fake functionality:** unfinished capabilities are labeled until implemented and tested.

## Current status

This `foundation` branch contains the Android project configuration, navigation shell, feature boundaries, product/architecture notes, a smoke-test scaffold, and a manual-only Android build workflow. File operations, the editor engine, archive processing, project persistence, and Shizuku actions are **not implemented yet**.

## Technology baseline

- Kotlin and Jetpack Compose / Material 3
- Gradle Kotlin DSL
- Android min SDK 26; compile/target SDK 36
- Android Gradle Plugin 8.13.2; Gradle 8.13
- Kotlin Gradle plugin 2.4.21
- Compose BOM 2026.09.00

Versions are pinned. Recheck the official Android Gradle Plugin / Gradle / Kotlin compatibility matrix before upgrades.

## Build

Requirements: JDK 17 or newer, Android SDK platform 36, and Gradle 8.13 installed locally.

```sh
gradle assembleDebug
gradle testDebugUnitTest
gradle connectedDebugAndroidTest
```

On Windows, use the same tasks with `gradle.bat`. Instrumentation tests require an emulator or connected Android device. After a successful build, the debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

**Gradle wrapper status:** standard wrapper scripts and the binary wrapper JAR are not included in this initial remote scaffold because the connected GitHub integration rejects binary wrapper content. I did not commit broken wrapper scripts. A standard wrapper should be generated and committed from a local Gradle 8.13 installation before describing command-line builds as self-contained.

Android Auto Backup is disabled in the starter manifest so future project files are not silently swept into OS-managed backups. A dedicated, user-controlled backup/export flow is planned separately.

The manual-only workflow at `.github/workflows/android-build.yml` can compile, test, and upload a debug APK once it is available from the default branch. No build has been run yet.

## Documentation

See [architecture and product scope](docs/architecture.md). The `foundation` branch does not replace `main`; treat the app as a scaffold until compilation, tests, and a real-device smoke test pass.
