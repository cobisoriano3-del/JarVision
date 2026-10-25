# JarVision

JarVision is a native Android file manager and universal code workspace. It aims to combine reliable everyday file operations, powerful archive workflows, a capable text editor, and persistent projects in one coherent app.

## Product principles

- **Universal by default:** not tied to Minecraft, one language, or one application.
- **Single-pane first:** normal browsing uses one location; **Dual Space** is optional.
- **Projects are first-class:** work in an app-managed workspace and export explicitly when ready.
- **Honest permissions:** normal browsing works without special access; restricted operations are capability-checked.
- **Protect user data:** clear conflict handling, safe writes, recoverable deletion where possible, and explicit warnings for irreversible actions.
- **No fake functionality:** unfinished capabilities are labeled until implemented and tested.

## Current status

**Implemented:** a single-pane file browser that requests Android's special **Allow access to manage all files** access on Android 11+ and, once granted, lists actual shared internal-storage entries. It navigates folders, shows file sizes and modification dates, refreshes, and hands files to other apps with temporary read-only URI grants. The folder picker remains as a fallback if broad access is declined or unavailable.

**Still planned:** safe copy/move/delete/rename, favorites, search and selection, archive operations, persistent project workspaces, the universal editor, Dual Space, and optional Shizuku integration.

## Storage permissions and platform limits

On Android 11+ (API 30+), broad shared-storage access uses `MANAGE_EXTERNAL_STORAGE` and the system's special app-access settings page; it is not a normal runtime permission dialog. Users must explicitly enable it. JarVision does not gain access to other apps' private directories, including protected `Android/data` locations. On Android 10, JarVision uses the folder picker because scoped storage has no equivalent all-files switch for this target; older supported releases use legacy runtime storage permissions when available. Google Play restricts all-files access to qualifying core use cases, so distribution requirements must be checked before publishing there.

## Technology baseline

- Kotlin and Jetpack Compose / Material 3
- Gradle Kotlin DSL
- Android min SDK 26; compile/target SDK 36
- Android Gradle Plugin 8.13.2; Gradle 8.13
- Kotlin Gradle plugin 2.3.21
- Compose BOM 2026.05.00; Navigation Compose 2.9.8
- Storage Access Framework fallback and a permission-checked shared-storage adapter

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

See [architecture and product scope](docs/architecture.md). Treat broad storage access and browser operations as beta until tested on physical devices and multiple document providers. The `foundation` branch remains available as the pre-promotion development snapshot.
