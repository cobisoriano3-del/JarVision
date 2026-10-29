package com.jarvision.app.shizuku

import android.os.Bundle
import java.io.ByteArrayOutputStream
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.provider.DocumentsContract
import android.system.Os
import androidx.annotation.Keep
import com.jarvision.app.data.storage.StorageEntry
import java.io.File
import java.io.IOException
import java.net.URLConnection
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipOutputStream

/**
 * Runs in the Shizuku UserService process, separate from JarVision's normal app process.
 * Mutations run under the active Shizuku UserService identity and are confined to shared internal
 * storage. Each operation validates its path and reports permission failures.
 */
@Keep
class ShizukuStorageUserService : IShizukuStorageService.Stub() {

    override fun listDirectory(path: String): Array<Bundle> {
        val directory = checkedSharedStoragePath(path)
        if (!directory.exists()) throw RemoteException("This folder no longer exists.")
        if (!directory.isDirectory) throw RemoteException("This location is not a folder.")

        val children = try {
            directory.listFiles()
                ?: throw IOException("The current Shizuku identity cannot list this folder.")
        } catch (error: SecurityException) {
            throw RemoteException("Android denied Shizuku access to this folder: ${error.message}")
        } catch (error: IOException) {
            throw RemoteException(error.message)
        }

        return children.mapNotNull { child ->
            val canonicalChild = try {
                child.canonicalFile
            } catch (_: IOException) {
                return@mapNotNull null
            }
            // Do not follow a symlink outside the user's shared-storage root.
            if (!isInsideSharedStorage(canonicalChild.path)) return@mapNotNull null

            val isDirectory = canonicalChild.isDirectory
            val mimeType = if (isDirectory) {
                DocumentsContract.Document.MIME_TYPE_DIR
            } else {
                URLConnection.guessContentTypeFromName(canonicalChild.name)
                    ?: "application/octet-stream"
            }
            Bundle().apply {
                putString(KEY_DOCUMENT_ID, canonicalChild.path)
                putString(KEY_DISPLAY_NAME, canonicalChild.name.ifBlank { canonicalChild.path })
                putString(KEY_MIME_TYPE, mimeType)
                putBoolean(KEY_IS_DIRECTORY, isDirectory)
                putLong(KEY_SIZE_BYTES, if (isDirectory) -1L else canonicalChild.length())
                putLong(KEY_LAST_MODIFIED, canonicalChild.lastModified())
                putString(KEY_ABSOLUTE_PATH, canonicalChild.path)
            }
        }.sortedWith(
            compareByDescending<Bundle> { it.getBoolean(KEY_IS_DIRECTORY) }
                .thenBy { it.getString(KEY_DISPLAY_NAME).orEmpty().lowercase(Locale.ROOT) }
        ).toTypedArray()
    }

    override fun openFileReadOnly(path: String): ParcelFileDescriptor {
        val file = checkedSharedStoragePath(path)
        if (!file.exists() || !file.isFile) {
            throw RemoteException("This item is not a readable file.")
        }
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (error: SecurityException) {
            throw RemoteException("Android denied Shizuku access to this file: ${error.message}")
        } catch (error: IOException) {
            throw RemoteException(error.message)
        }
    }

    override fun createDirectory(parentPath: String, name: String) {
        val target = checkedCreationTarget(parentPath, name)
        try {
            if (target.exists()) throw RemoteException("An item with that name already exists.")
            if (!target.mkdir()) throw RemoteException("Couldn't create the folder.")
        } catch (error: RemoteException) {
            throw error
        } catch (error: SecurityException) {
            throw RemoteException("Shizuku was denied permission to create this folder.")
        }
    }

    override fun createEmptyFile(parentPath: String, name: String) {
        val target = checkedCreationTarget(parentPath, name)
        try {
            if (!target.createNewFile()) {
                throw RemoteException("An item with that name already exists.")
            }
        } catch (error: RemoteException) {
            throw error
        } catch (error: IOException) {
            throw RemoteException("Couldn't create the file: " + (error.message ?: "storage error."))
        } catch (error: SecurityException) {
            throw RemoteException("Shizuku was denied permission to create this file.")
        }
    }

    override fun createEmptyZip(parentPath: String, name: String) {
        val target = checkedCreationTarget(parentPath, name)
        var created = false
        try {
            if (!target.createNewFile()) {
                throw RemoteException("An item with that name already exists.")
            }
            created = true
            ZipOutputStream(target.outputStream().buffered()).use { }
        } catch (error: RemoteException) {
            throw error
        } catch (error: Exception) {
            if (created) runCatching { target.delete() }
            throw RemoteException("Couldn't create the ZIP archive: " + (error.message ?: "storage error."))
        }
    }

    override fun getOperationCapabilities(path: String): Bundle {
        return try {
            val file = checkedMutationPath(path)
            val root = checkedMutationPath(SHARED_STORAGE_ROOT)
            val isRoot = file.canonicalFile == root.canonicalFile
            val parentWritable = file.parentFile?.let(::canCreateAndDeleteProbe) == true
            Bundle().apply {
                putBoolean(KEY_EXISTS, file.exists())
                putBoolean(KEY_IS_DIRECTORY, file.isDirectory)
                putBoolean(KEY_CAN_READ, file.exists() && file.canRead() && (!file.isDirectory || file.listFiles() != null))
                putBoolean(KEY_CAN_CREATE_CHILDREN, file.isDirectory && canCreateAndDeleteProbe(file))
                putBoolean(KEY_CAN_RENAME, file.exists() && !isRoot && parentWritable)
                putBoolean(KEY_CAN_DELETE, file.exists() && !isRoot && parentWritable)
            }
        } catch (_: Exception) {
            Bundle().apply {
                putBoolean(KEY_EXISTS, false)
                putBoolean(KEY_IS_DIRECTORY, false)
                putBoolean(KEY_CAN_READ, false)
                putBoolean(KEY_CAN_CREATE_CHILDREN, false)
                putBoolean(KEY_CAN_RENAME, false)
                putBoolean(KEY_CAN_DELETE, false)
            }
        }
    }

    override fun renameExistingItem(path: String, newName: String) {
        validateItemName(newName)
        val source = checkedMutationPath(path)
        val root = checkedMutationPath(SHARED_STORAGE_ROOT)
        if (source.canonicalFile == root.canonicalFile) {
            throw RemoteException("The shared-storage root cannot be renamed.")
        }
        val parent = source.parentFile ?: throw RemoteException("The selected item has no parent folder.")
        ensureWritableDirectory(parent)
        val target = checkedNewTarget(parent, newName)
        if (target.canonicalFile == source.canonicalFile) return
        if (target.exists()) throw RemoteException("An item with that name already exists.")
        if (!source.renameTo(target)) {
            throw RemoteException("The Shizuku identity or Android policy denied this rename.")
        }
    }

    override fun deleteExistingItem(path: String) {
        val target = checkedMutationPath(path)
        val root = checkedMutationPath(SHARED_STORAGE_ROOT)
        if (target.canonicalFile == root.canonicalFile) {
            throw RemoteException("The shared-storage root cannot be deleted.")
        }
        val parent = target.parentFile ?: throw RemoteException("The selected item has no parent folder.")
        ensureWritableDirectory(parent)
        validateTreeForDeletion(target)
        deleteTreeWithoutFollowingLinks(target)
    }

    override fun openNewFileForWrite(parentPath: String, name: String): ParcelFileDescriptor {
        val target = checkedCreationTarget(parentPath, name)
        var created = false
        try {
            if (!target.createNewFile()) throw RemoteException("An item with that name already exists.")
            created = true
            return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_WRITE_ONLY)
        } catch (error: RemoteException) {
            throw error
        } catch (error: Exception) {
            if (created) runCatching { target.delete() }
            throw RemoteException("Couldn't open the new file for writing: " + (error.message ?: "storage error."))
        }
    }

    override fun copyExistingItem(sourcePath: String, destinationParentPath: String, destinationName: String) {
        validateItemName(destinationName)
        val source = checkedMutationPath(sourcePath)
        val parent = checkedMutationPath(destinationParentPath)
        ensureWritableDirectory(parent)
        if (source.canonicalFile == parent.canonicalFile ||
            (source.isDirectory && parent.canonicalPath.startsWith(source.canonicalPath + File.separator))
        ) {
            throw RemoteException("A folder cannot be copied into itself or one of its subfolders.")
        }
        val target = checkedNewTarget(parent, destinationName)
        if (target.exists()) throw RemoteException("An item with that name already exists.")
        try {
            copyTreeWithoutFollowingLinks(source, target)
        } catch (error: Exception) {
            runCatching { deleteTreeWithoutFollowingLinks(target) }
            if (error is RemoteException) throw error
            throw RemoteException("Shizuku couldn't copy this item: " + (error.message ?: "storage error."))
        }
    }

    override fun getUid(): Int = Os.getuid()

    override fun loadFolderThumbnail(path: String): ByteArray? {
        val directory = checkedSharedStoragePath(path)
        if (!directory.isDirectory) return null
        for (name in FOLDER_THUMBNAIL_NAMES) {
            val candidate = try { File(directory, name).canonicalFile } catch (_: IOException) { continue }
            if (!isInsideSharedStorage(candidate.path)) continue
            if (candidate.parentFile?.canonicalFile != directory.canonicalFile) continue
            if (!candidate.isFile || !candidate.canRead()) continue
            if (candidate.length() !in 1L..MAX_FOLDER_THUMBNAIL_BYTES) continue
            try {
                val bytes = candidate.inputStream().use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size().toLong() + count.toLong() > MAX_FOLDER_THUMBNAIL_BYTES) return null
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                if (bytes.isNotEmpty()) return bytes
            } catch (_: IOException) {
                // Try the next supported name if a thumbnail disappears during lookup.
            } catch (_: SecurityException) {
                // The Shizuku identity may not be allowed to read this candidate.
            }
        }
        return null
    }

    override fun destroy() {
        System.exit(0)
    }

    private fun checkedSharedStoragePath(path: String): File {
        if (path.isBlank()) throw RemoteException("A storage path is required.")
        val file = try {
            File(path).canonicalFile
        } catch (error: IOException) {
            throw RemoteException("The storage path is invalid: ${error.message}")
        }
        if (!isInsideSharedStorage(file.path)) {
            throw RemoteException("Shizuku access is limited to shared internal storage.")
        }
        return file
    }

    private fun checkedCreationTarget(parentPath: String, name: String): File {
        if (name.isBlank() || name == "." || name == ".." ||
            name.contains('/') || name.contains('\\') || name.any { it.code < 32 }
        ) {
            throw RemoteException("Enter a valid name without folder separators.")
        }
        val parent = checkedMutationPath(parentPath)
        if (!parent.isDirectory) throw RemoteException("The destination is not a folder.")
        val canonicalParent = parent.canonicalFile
        val requestedTarget = File(canonicalParent, name)
        if (Files.isSymbolicLink(requestedTarget.toPath())) {
            throw RemoteException("Creating over a symbolic link is not allowed.")
        }
        val target = try {
            requestedTarget.canonicalFile
        } catch (error: IOException) {
            throw RemoteException("The new item path is invalid.")
        }
        if (target.parentFile != canonicalParent || !isInsideSharedStorage(target.path)) {
            throw RemoteException("Creation is limited to direct children of shared internal storage folders.")
        }
        return target
    }

    private fun checkedMutationPath(path: String): File {
        if (path.isBlank()) throw RemoteException("A storage path is required.")
        val rootPath = File(SHARED_STORAGE_ROOT).absoluteFile.toPath().normalize()
        val requested = File(path).absoluteFile.toPath().normalize()
        if (!requested.startsWith(rootPath)) {
            throw RemoteException("Shizuku mutations are limited to shared internal storage.")
        }
        var cursor = rootPath
        for (component in rootPath.relativize(requested)) {
            cursor = cursor.resolve(component)
            if (Files.isSymbolicLink(cursor)) throw RemoteException("Mutating symbolic links is not supported.")
        }
        val canonical = try {
            requested.toFile().canonicalFile
        } catch (error: IOException) {
            throw RemoteException("The selected path is invalid: " + (error.message ?: "I/O error."))
        }
        if (!isInsideSharedStorage(canonical.path) || !canonical.exists()) {
            throw RemoteException("The selected item does not exist inside shared internal storage.")
        }
        return canonical
    }

    private fun checkedNewTarget(parent: File, name: String): File {
        validateItemName(name)
        val safeParent = checkedMutationPath(parent.absolutePath)
        ensureWritableDirectory(safeParent)
        val candidate = File(safeParent, name)
        if (Files.isSymbolicLink(candidate.toPath())) throw RemoteException("Writing through a symbolic link is not allowed.")
        val normalized = candidate.absoluteFile.toPath().normalize().toFile()
        if (normalized.parentFile?.canonicalFile != safeParent.canonicalFile || !isInsideSharedStorage(normalized.canonicalPath)) {
            throw RemoteException("The destination is not a direct child of the selected folder.")
        }
        return normalized
    }

    private fun validateItemName(name: String) {
        if (name.isBlank() || name == "." || name == ".." ||
            name.contains('/') || name.contains('\\') || name.any { it.code < 32 }
        ) {
            throw RemoteException("Enter a valid name without folder separators.")
        }
    }

    /** Probes actual create/delete ability under the UserService identity, not the app process. */
    private fun canCreateAndDeleteProbe(directory: File): Boolean {
        if (!directory.isDirectory) return false
        var probe: File? = null
        return try {
            probe = File.createTempFile(".jarvision-capability-", ".tmp", directory)
            probe.delete() && !probe.exists()
        } catch (_: Exception) {
            false
        } finally {
            probe?.takeIf { it.exists() }?.delete()
        }
    }

    private fun ensureWritableDirectory(directory: File) {
        if (!canCreateAndDeleteProbe(directory)) {
            throw RemoteException("The active Shizuku identity cannot create and remove items in this folder.")
        }
    }

    private fun copyTreeWithoutFollowingLinks(source: File, target: File) {
        val safeSource = checkedMutationPath(source.absolutePath)
        if (safeSource.isDirectory) {
            if (!target.mkdir()) throw RemoteException("Couldn't create the destination folder.")
            val children = safeSource.listFiles() ?: throw RemoteException("Shizuku cannot list the source folder.")
            children.forEach { child ->
                if (Files.isSymbolicLink(child.toPath())) throw RemoteException("A symbolic link was found; copy was stopped.")
                copyTreeWithoutFollowingLinks(child, checkedNewTarget(target, child.name))
            }
        } else {
            safeSource.inputStream().buffered().use { input ->
                target.outputStream().buffered().use { output -> input.copyTo(output) }
            }
        }
        target.setLastModified(safeSource.lastModified())
    }

    private fun validateTreeForDeletion(target: File) {
        val safe = checkedMutationPath(target.absolutePath)
        if (Files.isSymbolicLink(safe.toPath())) throw RemoteException("Deleting symbolic links is not supported.")
        if (safe.isDirectory) {
            val children = safe.listFiles() ?: throw RemoteException("Shizuku cannot list this folder for deletion.")
            children.forEach { validateTreeForDeletion(it) }
        }
    }

    private fun deleteTreeWithoutFollowingLinks(target: File) {
        if (Files.isSymbolicLink(target.toPath())) throw RemoteException("Deleting symbolic links is not supported.")
        if (target.isDirectory) {
            val children = target.listFiles() ?: throw RemoteException("Shizuku cannot list this folder for deletion.")
            children.forEach { child -> deleteTreeWithoutFollowingLinks(checkedMutationPath(child.absolutePath)) }
        }
        if (!target.delete()) throw RemoteException("The Shizuku identity or Android policy denied this delete.")
    }

    private val canonicalSharedStorageRoot: String by lazy {
        try {
            File(SHARED_STORAGE_ROOT).canonicalPath
        } catch (_: IOException) {
            SHARED_STORAGE_ROOT
        }
    }

    private fun isInsideSharedStorage(path: String): Boolean {
        val root = canonicalSharedStorageRoot
        return path == root || path.startsWith("$root${File.separator}")
    }

    private companion object {
        const val SHARED_STORAGE_ROOT = "/storage/emulated/0"
        const val KEY_DOCUMENT_ID = "documentId"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_MIME_TYPE = "mimeType"
        const val KEY_IS_DIRECTORY = "isDirectory"
        const val KEY_SIZE_BYTES = "sizeBytes"
        const val KEY_LAST_MODIFIED = "lastModifiedMillis"
        const val KEY_ABSOLUTE_PATH = "absolutePath"
        const val KEY_EXISTS = "exists"
        const val KEY_CAN_READ = "canRead"
        const val KEY_CAN_CREATE_CHILDREN = "canCreateChildren"
        const val KEY_CAN_RENAME = "canRename"
        const val KEY_CAN_DELETE = "canDelete"
        const val MAX_FOLDER_THUMBNAIL_BYTES = 1L * 1024L * 1024L
        val FOLDER_THUMBNAIL_NAMES = listOf("folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
            ".foldericon", ".foldericon.jpg", "cover.jpg", "cover.png", "thumbnail.jpg")
    }
}
