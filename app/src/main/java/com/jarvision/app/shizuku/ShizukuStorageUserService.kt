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
 * Existing-item browsing and reading are read-only; only creation of new items is supported,
 * and every operation is confined to shared internal storage.
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
        val parent = checkedSharedStoragePath(parentPath)
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
        const val MAX_FOLDER_THUMBNAIL_BYTES = 1L * 1024L * 1024L
        val FOLDER_THUMBNAIL_NAMES = listOf("folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
            ".foldericon", ".foldericon.jpg", "cover.jpg", "cover.png", "thumbnail.jpg")
    }
}
