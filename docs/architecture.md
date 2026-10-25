# JarVision architecture and scope

## Product definition

JarVision is a universal Android file manager and local development workspace. It is not limited to Minecraft, a single programming language, or one installed application.

## Current implementation

The Files tab supports three adapters:
- **Shared storage:** direct shared internal-storage listing when Android's all-files special access is granted.
- **Storage Access Framework:** a persisted user-selected folder grant.
- **Shizuku (optional):** a bound UserService operating as shell/ADB or root when the user starts Shizuku and explicitly approves JarVision. The current adapter is read-only, confines paths to `/storage/emulated/0`, can list directories and provide a bounded in-app text preview, and does not create, edit, overwrite, or delete files.

The Shizuku process identity is not guaranteed to be root. Shell/ADB can read some folders on some devices but cannot access all app-private data. Access to each protected path is runtime-checked; errors are shown instead of promising universal access.

## Initial boundary

The app remains one Gradle application module (`:app`) with package-level feature boundaries. Implemented files live in `data/storage/`, `feature/files/`, and `feature/access/`. Other early-stage feature placeholders remain grouped in `feature/screens/FeatureScreens.kt`.

## Main layers

- **UI:** Jetpack Compose screens, navigation, theme, and reusable components.
- **Feature packages:** files, projects, editor, archives, Dual Space, settings, and advanced access.
- **Domain contracts:** storage entry metadata and the SAF storage gateway.
- **Platform/data adapters:** SAF, shared-storage listing, and optional Shizuku connection/service.

## Dependency rules

1. UI code interacts with storage adapters through view-model state; it must not perform directory queries directly.
2. Storage entries represent document IDs or shared-storage paths without assuming every provider exposes a normal path.
3. Projects own their app-private workspace and metadata; exporting is explicit.
4. The editor accepts a document/session abstraction and must not assume every file has a normal path.
5. Archive processing runs off the main thread and reports progress, conflicts, and failures.
6. Shizuku is optional. Normal browsing works without it; privileged adapter disconnects are surfaced as errors.
7. Dual Space remains optional; single-pane browsing is the default.

## Safety and data ownership

- Shizuku service is restricted to read-only directory listing and read-only file descriptors.
- Validate every Shizuku path against shared internal storage; reject symlinks whose canonical target escapes that root.
- The normal app process may preview only small text files from Shizuku, with a 1 MiB cap; binary/large files fail clearly.
- Never overwrite silently; offer skip, rename, or replace where applicable when write features are later added.
- Prefer recoverable deletion where possible and distinguish permanent deletion explicitly.
- Project files live in a private app-managed workspace by default. Database rows should store metadata, recents, favorites, preferences, and operation history—not entire source files.
- Device storage access is limited to app-owned files and user-granted/system-permitted locations.
- Backups and exports are explicit so users can protect work before uninstalling or clearing app data.

## Explicit non-guarantees

- Access to every protected Android directory.
- Access to another application's private data when the current privilege level cannot reach it.
- Access to every `Android/data` entry across Android versions and OEMs.
- Automatic original source-code recovery from installed APKs.
- Compilation or execution of every language without the appropriate toolchain.
- Full create/extract support for every archive format; capabilities are format- and operation-specific.

## Milestones

1. Build foundation and navigation — complete.
2. Storage abstraction and real file browser — initial implementation.
3. Optional read-only Shizuku storage connection — in implementation; device permissions determine exact reach.
4. Safe operations, search, selection, and contextual menus.
5. Persistent universal projects and explicit import/export.
6. Editor integration and recovery.
7. ZIP support, then expand archive formats through tests.
8. Optional Dual Space, inspection tools, and device testing.

Every milestone needs acceptance tests before the next milestone depends on it.
