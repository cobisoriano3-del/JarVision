# JarVision architecture and scope

## Product definition

JarVision is a universal Android file manager and local development workspace. It is not limited to Minecraft, a single programming language, or one installed application.

## Current implementation

The Files tab supports three adapters. It has a thin path toolbar with current-folder search, Compact/Detailed/Grid modes, persistent sort and hidden-file preferences, an overflow menu, an expandable create button, and multi-select with confirmed bulk deletion. Compact mode reduces folder thumbnails to 32 dp and trims row padding. Folder rows use Coil `AsyncImage` with rounded center-crop thumbnails when a supported image is found directly inside a folder; path state is kept in the ViewModel. File and folder rows support long-press contextual menus. Writable shared-storage, SAF, and capability-approved Shizuku destinations implement folder/file creation, copy/cut/paste, rename, confirmed delete, ZIP/7z compression, ZIP testing, and ZIP extraction here or into a separate folder. ZIP-compatible archives can be browsed in-app; text members have a bounded read-only preview. Archive extraction is staged in private cache and rejects unsafe paths and excessive expansion.
- **Shared storage:** direct shared internal-storage listing when Android's all-files special access is granted.
- **Storage Access Framework:** a persisted user-selected folder grant.
- **Shizuku (optional):** a bound UserService operating as shell/ADB or root when the user starts Shizuku and explicitly approves JarVision. It confines service operations to `/storage/emulated/0`, provides bounded previews and read-only staging for external opening, and checks capabilities from the UserService identity. Where that identity and device policy permit, JarVision can create, copy, rename, delete, extract ZIP contents, and publish ZIP/7z archives. Each operation can still be denied by Android or the filesystem; shell identity is not root, so no path is assumed universally writable.

The Shizuku process identity is not guaranteed to be root. Shell/ADB can read some folders on some devices but cannot access all app-private data. Access to each protected path is runtime-checked; errors are shown instead of promising universal access.

## Initial boundary

The app remains one Gradle application module (`:app`) with package-level feature boundaries. Implemented files live in `data/storage/`, `data/projects/`, `feature/files/`, `feature/projects/`, and `feature/access/`. Other early-stage feature placeholders remain grouped in `feature/screens/FeatureScreens.kt`.

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

- Shizuku service supports shared-storage browsing and path-checked creation, copy, rename, delete, and new-file output. The UI uses UserService capability hints, while each actual operation validates permissions again and handles OS denial. Replace-in-place is not supported through Shizuku copy.
- Validate every Shizuku path against shared internal storage; reject symlinks whose canonical target escapes that root.
- The normal app process may preview only small text files from Shizuku, with a 1 MiB cap. Explicit Open/Share can stage a read-only copy in app cache, capped at 512 MiB; source files remain unchanged.
- ZIP tests and extraction enforce path safety, duplicate-entry rejection, a 10,000-entry limit, a 256 MiB per-entry expanded limit, and 1 GiB total expansion limit.
- Copy/cut/paste stays within the same active backend and selected SAF tree when SAF is used. Shizuku copy is limited to its shared-storage scope. Cut copies first and removes the source only after the destination copy succeeds.
- Delete and any replacement supported by the active backend require explicit user confirmation. No operation silently overwrites an existing destination; shared-storage and SAF copy conflicts can offer Keep both or Replace, while Shizuku copy refuses replace-in-place.
- Never overwrite silently. Every backend checks destination conflicts and lets the actual create/write operation reject a race or provider denial.
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
3. Optional Shizuku storage connection with path-specific capability checks and permission-limited mutations — implemented; device permissions determine exact reach.
4. Contextual file operations, in-app ZIP browsing, ZIP extraction, and ZIP/7z compression — implemented for supported storage backends; add operation progress and recoverable trash next.
5. Persistent project workspace with nested folder/text-file operations — initial implementation complete; explicit import/export remains pending.
6. Editor integration and recovery.
7. ZIP validation and extraction — first implementation with path/size limits; expand archive formats through tests.
8. Optional Dual Space, inspection tools, and device testing.

Every milestone needs acceptance tests before the next milestone depends on it.
