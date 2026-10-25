package com.jarvision.app.data.storage

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.util.Locale

/**
 * Storage Access Framework adapter. Access is limited to the URI grant supplied by Android.
 */
class SafStorageGateway(
    private val contentResolver: ContentResolver
) : StorageGateway {

    private companion object {
        val FOLDER_THUMBNAIL_NAMES = listOf("folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
            ".foldericon", ".foldericon.jpg", "cover.jpg", "cover.png", "thumbnail.jpg")
        const val MAX_FOLDER_THUMBNAIL_BYTES = 1L * 1024L * 1024L
    }

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    override fun getDocument(treeUri: Uri, documentId: String): StorageEntry? {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val cursor = contentResolver.query(documentUri, projection, null, null, null)
            ?: throw IOException("The storage provider did not return document details.")

        return cursor.use {
            if (it.moveToFirst()) it.toStorageEntry() else null
        }
    }

    fun findFolderThumbnailUri(treeUri: Uri, folderDocumentId: String): Uri? {
        val children = runCatching { listChildren(treeUri, folderDocumentId) }.getOrNull() ?: return null
        val byName = children.asSequence()
            .filter { !it.isDirectory && (it.sizeBytes == null || it.sizeBytes in 1L..MAX_FOLDER_THUMBNAIL_BYTES) }
            .associateBy { it.displayName.lowercase(Locale.ROOT) }
        return FOLDER_THUMBNAIL_NAMES.firstNotNullOfOrNull { name ->
            byName[name]?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.documentId) }
        }
    }

    override fun listChildren(
        treeUri: Uri,
        parentDocumentId: String
    ): List<StorageEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentDocumentId
        )
        val cursor = contentResolver.query(childrenUri, projection, null, null, null)
            ?: throw IOException("The storage provider did not return a directory listing.")

        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(it.toStorageEntry())
                }
            }.sortedWith(
                compareByDescending<StorageEntry> { entry -> entry.isDirectory }
                    .thenBy { entry -> entry.displayName.lowercase(Locale.ROOT) }
            )
        }
    }

    private fun Cursor.toStorageEntry(): StorageEntry {
        fun stringValue(column: String): String? {
            val index = getColumnIndex(column)
            return if (index >= 0 && !isNull(index)) getString(index) else null
        }

        fun longValue(column: String): Long? {
            val index = getColumnIndex(column)
            return if (index >= 0 && !isNull(index)) getLong(index) else null
        }

        val documentId = stringValue(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            ?: throw IOException("The storage provider returned an item without an ID.")
        val displayName = stringValue(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: "Unnamed item"
        val mimeType = stringValue(DocumentsContract.Document.COLUMN_MIME_TYPE)

        return StorageEntry(
            documentId = documentId,
            displayName = displayName,
            mimeType = mimeType,
            isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
            sizeBytes = longValue(DocumentsContract.Document.COLUMN_SIZE)?.takeIf { it >= 0L },
            lastModifiedMillis = longValue(
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )?.takeIf { it > 0L }
        )
    }
}
