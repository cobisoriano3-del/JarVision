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

**Implemented:** a single-pane Storage Access Framework browser. Users choose a folder through Android's system picker; JarVision persists the read grant, lists the granted folder's real contents, sorts folders before files, navigates into subfolders and back, refreshes, and attempts to open files with a compatible installed app. Access is limited to the folder the user grants.

**Still planned:** safe copy/move/delete/rename, favorites, search and selection, archive operations, persistent project workspaces, the universal editor, Dual Space, and optional Shizuku integration.

## Technology baseline

- Kotlin and Jetpack Compose / Material 3
- Gradle Kotlin DSL
- Android min SDK 26; compile/target SDK 36
- Android Gradle Plugin 8.13.2; Gradle 8.13
- Kotlin Gradle plugin 2.3.21
- Compose BOM 2026.05.00; Navigation Compose 2.9.8
- Storage Access Framework adapter isolated behind `StorageGateway`

Versions are pinned to keep the toolchain compatible. Recheck the official Android Gradle Plugin / Gradle / Kotlin compatibility matrix before upgrades.

## Build

Requirements: JDK 17 or newer and Android SDK platform 36. The Gradle wrapper downloads the pinned Gradle 8.13 distribution and verifies its SHA-256 checksum.

macOS/Linux:
```sh
./gradlew --version
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

Windows:
```bat
gradlew.bat --version
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
gradlew.bat connectedDebugAndroidTest
```

Instrumentation tests require an emulator or connected Android device. After a successful build, the debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

The wrapper consists of the checked-in launcher scripts, `gradle/wrapper/gradle-wrapper.jar`, and `gradle/wrapper/gradle-wrapper.properties`. Keep these files together. When intentionally upgrading Gradle, update the distribution URL and checksum together and verify the official checksum before committing.

Android Auto Backup is disabled in the starter manifest so future project files are not silently swept into OS-managed backups. A dedicated, user-controlled backup/export flow is planned separately.

The GitHub Actions workflow at `.github/workflows/android-build.yml` runs on pushes to `main` and can also be started manually from the Actions tab. It builds the debug APK and runs JVM tests, configures Linux KVM permissions for the Android emulator, and runs the instrumentation smoke test. The APK artifact `jarvision-debug-apk` is uploaded whenever the compile/JVM-test step succeeds, even if the later emulator startup or instrumentation test fails. Open the workflow run and download the artifact from its Artifacts section.

## Documentation

See [architecture and product scope](docs/architecture.md). Treat storage browsing as beta until it has been exercised against several Android document providers and on a physical device. The `foundation` branch remains available as the pre-promotion development snapshot.
