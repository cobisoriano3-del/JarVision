package com.jarvision.app.data.storage

import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException
import java.net.URLConnection
import java.util.Locale

/**
 * Direct shared-storage access. Callers must check platform permission before using this adapter.
 * It intentionally does not attempt to access another app's private data directories.
 */
class SharedStorageGateway {

    @Suppress("DEPRECATION")
    fun rootDirectory(): File = Environment.getExternalStorageDirectory()

    fun findFolderThumbnailFile(directoryPath: String): File? {
        val directory = runCatching { File(directoryPath).canonicalFile }.getOrNull() ?: return null
        if (!directory.isDirectory) return null
        for (name in FOLDER_THUMBNAIL_NAMES) {
            val candidate = runCatching { File(directory, name).canonicalFile }.getOrNull() ?: continue
            if (candidate.parentFile?.canonicalFile != directory) continue
            if (!candidate.isFile || !candidate.canRead()) continue
            if (candidate.length() !in 1L..MAX_FOLDER_THUMBNAIL_BYTES) continue
            return candidate
        }
        return null
    }

    @Throws(IOException::class, SecurityException::class)
    fun listChildren(directoryPath: String): List<StorageEntry> {
        val directory = File(directoryPath)
        if (!directory.exists()) throw IOException("The folder no longer exists.")
        if (!directory.isDirectory) throw IOException("This location is not a folder.")
        if (!directory.canRead()) throw SecurityException("Android denied access to this folder.")

        val children = directory.listFiles()
            ?: throw IOException("This folder could not be listed.")

        return children.map { child ->
            val isDirectory = child.isDirectory
            StorageEntry(
                documentId = child.absolutePath,
                displayName = child.name.ifBlank { child.absolutePath },
                mimeType = if (isDirectory) {
                    DocumentsContract.Document.MIME_TYPE_DIR
                } else {
                    URLConnection.guessContentTypeFromName(child.name)
                        ?: "application/octet-stream"
                },
                isDirectory = isDirectory,
                sizeBytes = if (isDirectory) null else child.length().takeIf { it >= 0L },
                lastModifiedMillis = child.lastModified().takeIf { it > 0L },
                absolutePath = child.absolutePath
            )
        }.sortedWith(
            compareByDescending<StorageEntry> { it.isDirectory }
                .thenBy { it.displayName.lowercase(Locale.ROOT) }
        )
    }

    private companion object {
        val FOLDER_THUMBNAIL_NAMES = listOf("folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
            ".foldericon", ".foldericon.jpg", "cover.jpg", "cover.png", "thumbnail.jpg")
        const val MAX_FOLDER_THUMBNAIL_BYTES = 1L * 1024L * 1024L
    }
}
