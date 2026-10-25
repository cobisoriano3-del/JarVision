# JarVision architecture and scope

## Product definition

JarVision is a universal Android file manager and local development workspace. It is not limited to Minecraft, a single programming language, or one installed application.

## Current implementation

The Files tab supports two permission-scoped modes:

- **Shared storage:** on Android 11+ when `Environment.isExternalStorageManager()` confirms special access, a direct file-system adapter lists real shared internal-storage entries. On Android 8–9 it uses granted legacy read/write storage permissions.
- **Selected folder:** the Storage Access Framework remembers a user-selected tree URI and operates only inside that granted tree. This is also the fallback on Android 10, where this target does not use the all-files switch.

The UI checks permissions at startup and when returning from system Settings. Direct file opens use AndroidX `FileProvider` content URIs with temporary read grants instead of unsafe `file://` URIs.

The app intentionally does not request broad filesystem access silently. Users must explicitly enable the Android special access setting; if they decline, folder-only access remains available. Android continues to prevent access to protected directories belonging to other apps.

## Initial boundary

The scaffold remains one Gradle application module (`:app`) with package-level feature boundaries. Separate Gradle modules should be extracted only when stable contracts, independent tests, and dependency isolation justify the added build complexity. Implemented files live in `data/storage/` and `feature/files/`; other early-stage feature placeholders remain grouped in `feature/screens/FeatureScreens.kt`.

## Main layers

- **UI:** Jetpack Compose screens, navigation, theme, and reusable components.
- **Feature packages:** files, projects, editor, archives, Dual Space, settings, and advanced access.
- **Domain contracts:** `StorageEntry` and `StorageGateway` are the initial storage contracts; operation, project, and capability models will follow with their implementations.
- **Platform/data adapters:** Storage Access Framework and permission-checked shared-storage listing are implemented. App-private projects, Room metadata, archive engine, editor engine, and Shizuku adapter remain pending.

## Dependency rules

1. UI code interacts with file providers through the storage adapters and view-model state; it must not perform directory queries directly.
2. Storage entries represent document IDs or direct shared-storage paths without assuming every provider exposes a normal path.
3. Projects own their app-private workspace and metadata; exporting is explicit.
4. The editor accepts a document/session abstraction and must not assume every file has a normal path.
5. Archive processing runs off the main thread and reports progress, conflicts, and failures.
6. Shizuku is optional. Normal browsing must work when it is missing, disconnected, or denied.
7. Optional Dual Space keeps independent location state for both windows; single-pane mode remains the default.

## Safety and data ownership

- Validate source and destination before destructive operations.
- Never overwrite silently; offer skip, rename, or replace where applicable.
- Use temporary output and atomic replacement where supported.
- Keep status truthful: unavailable operations are disabled or explained, not simulated.
- Prefer recoverable deletion where possible and distinguish permanent deletion explicitly.
- Project files live in a private app-managed workspace by default. Database rows should store metadata, recents, favorites, preferences, and operation history—not entire source files.
- Device storage access is limited to app-owned files and user-granted/system-permitted locations.
- Backups and exports are explicit so users can protect work before uninstalling or clearing app data.
- Shared-storage files handed to another app use temporary read-only FileProvider grants.

## Explicit non-guarantees

- Access to every protected Android directory.
- Access to another application's private data when the current privilege level cannot reach it.
- Automatic original source-code recovery from installed APKs.
- Compilation or execution of every language without the appropriate toolchain.
- Full create/extract support for every archive format; capabilities are format- and operation-specific.

## Milestones

1. Build foundation and navigation — complete.
2. Storage abstraction and real file browser — initial implementation, with all-files special access and folder-only fallback.
3. Safe operations, search, selection, and contextual menus.
4. Persistent universal projects and explicit import/export.
5. Editor integration and recovery.
6. ZIP support, then expand archive formats through tests.
7. Optional Dual Space, inspection tools, and Shizuku.
8. Device testing, performance profiling, and release packaging.

Every milestone needs acceptance tests before the next milestone depends on it.
