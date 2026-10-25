# JarVision architecture and scope

## Product definition

JarVision is a universal Android file manager and local development workspace. It is not limited to Minecraft, a single programming language, or one installed application.

## Initial boundary

The scaffold uses one Gradle application module (`:app`) with package-level feature boundaries. Separate Gradle modules should be extracted only when stable contracts, independent tests, and dependency isolation justify the added build complexity. The simple planned screens are temporarily grouped in `feature/screens/FeatureScreens.kt`; move them into dedicated feature packages as each feature becomes real.

## Main layers

- **UI:** Jetpack Compose screens, navigation, theme, and reusable components.
- **Feature packages:** files, projects, editor, archives, Dual Space, settings, and advanced access.
- **Domain contracts (to be introduced with implementations):** file entries, storage locations, operation requests/results, projects, and capability state.
- **Platform/data adapters (to be introduced with implementations):** Storage Access Framework, app-private project files, Room metadata, archive engine, editor engine, and Shizuku adapter.

## Dependency rules

1. UI code must not perform filesystem operations directly.
2. File operations go through a storage abstraction that can represent filesystem paths and Android document URIs.
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

## Explicit non-guarantees

- Access to every protected Android directory.
- Access to another application's private data when the current privilege level cannot reach it.
- Automatic original source-code recovery from installed APKs.
- Compilation or execution of every language without the appropriate toolchain.
- Full create/extract support for every archive format; capabilities are format- and operation-specific.

## Milestones

1. Build foundation and navigation.
2. Storage abstraction and real file browser.
3. Safe operations, search, selection, and contextual menus.
4. Persistent universal projects and explicit import/export.
5. Editor integration and recovery.
6. ZIP support, then expand archive formats through tests.
7. Optional Dual Space, inspection tools, and Shizuku.
8. Device testing, performance profiling, and release packaging.

Every milestone needs acceptance tests before the next milestone depends on it.
