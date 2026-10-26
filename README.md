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

**Implemented:** a compact single-pane file browser with a thin path toolbar, current-folder search, Compact/Detailed/Grid views, persisted sorting and hidden-file preferences, an overflow menu, expandable create-action button, smaller folder thumbnails, and multi-select with confirmed bulk deletion. It supports Android all-files access, a Storage Access Framework fallback, optional read-only Shizuku browsing, Coil-powered folder thumbnails, and long-press contextual actions. For writable shared-storage and SAF locations, item actions include information, share, copy, cut/paste, rename, and confirmed delete. Copy and cut/paste operate within the same selected storage scope; conflicts require an explicit Keep both or Replace choice. SAF mutations require a persisted write grant. Shizuku remains read-only: it can list, preview small text files, validate ZIP archives, and stage explicitly shared/opened files through a bounded temporary cache, but it cannot modify source items.

ZIP support includes streaming compression of a selected file/folder, archive testing, and extraction here or into a new folder for writable storage scopes. Extraction rejects absolute/traversal paths, duplicate normalized entries, more than 10,000 entries, any entry over 256 MiB expanded, and total expansion over 1 GiB. Output is staged in private cache and path-checked before it is copied into storage. The folder-thumbnail loader checks `folder.jpg`, `folder.jpeg`, `folder.png`, `folder.webp`, `.foldericon`, `.foldericon.jpg`, `cover.jpg`, `cover.png`, or `thumbnail.jpg` inside each folder. Missing or inaccessible images use a folder-name-based fallback icon.

**Still planned:** recursive global search, persistent project workspaces, the universal editor, Dual Space, recoverable trash, background/progress reporting for large operations, broader archive formats such as 7z, and additional device/provider testing.

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
- Coil Compose 3.4.0 for folder thumbnails
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
