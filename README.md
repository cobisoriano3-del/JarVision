# JarVision

JarVision is a native Android file manager and universal code workspace. It aims to combine reliable everyday file operations, powerful archive workflows, a capable text editor, and persistent projects in one coherent app.

## Product principles

- **Universal by default:** not tied to Minecraft, one language, or one application.
- **Single-pane first:** normal browsing uses one location; **Dual Space** is optional.
- **Projects are first-class:** work in an app-managed workspace and export explicitly when ready.
- **Honest permissions:** normal browsing works without special access; privileged adapters are explicit and capability-checked.
- **Protect user data:** clear conflict handling, safe writes, recoverable deletion where possible, and explicit warnings for irreversible actions.
- **No fake functionality:** unfinished capabilities are labeled until implemented and tested.

## Current status

**Implemented:** a single-pane file browser with Android all-files access on Android 11+ and a Storage Access Framework folder-picker fallback. An optional Shizuku connection adapter is being added for read-only browsing of existing shared-storage paths that the Shizuku shell/root identity can access, including device-dependent access to `Android/data` and `Android/obb`. Shizuku is an external companion app and must be running and explicitly authorized.

**Still planned:** safe copy/move/delete/rename, favorites, search and selection, archive operations, persistent project workspaces, the universal editor, Dual Space, and broader privileged operations.

## Storage permissions and platform limits

On Android 11+ (API 30+), broad shared-storage access uses `MANAGE_EXTERNAL_STORAGE` and the system's special app-access settings page; it is not a normal runtime permission dialog. Shizuku runs a separate user service as shell/ADB or root when available, but the capabilities are different: shell does not equal root and may still be blocked from individual folders. JarVision's Shizuku adapter is read-only, explicitly limits requested paths to shared internal storage, and refuses to follow paths outside that root. It does not claim access to every `Android/data` entry, other apps' private internal data, or all devices. Google Play restricts all-files access to qualifying core use cases, so distribution requirements must be checked before publishing there.

## Technology baseline

- Kotlin and Jetpack Compose / Material 3
- Gradle Kotlin DSL
- Android min SDK 26; compile/target SDK 36
- Android Gradle Plugin 8.13.2; Gradle 8.13
- Kotlin Gradle plugin 2.3.21
- Compose BOM 2026.05.00; Navigation Compose 2.9.8
- Shizuku API/provider 13.1.5
- Storage Access Framework, shared-storage adapter, optional Shizuku read-only adapter

Versions are pinned to keep the toolchain compatible. Recheck official Android and Shizuku compatibility guidance before upgrades.

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

The workflow builds the debug APK, runs JVM tests, and uploads `jarvision-debug-apk` immediately after that stage succeeds. Only then does it attempt KVM setup and the Android emulator smoke test.

## Documentation

See [architecture and product scope](docs/architecture.md). Test storage access on physical devices and multiple document providers. The `foundation` branch remains available as the pre-promotion development snapshot.
