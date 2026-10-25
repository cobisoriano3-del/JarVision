package com.jarvision.app.data.storage

import android.net.Uri
import java.io.IOException

/**
 * An item exposed by a storage provider. SAF entries use document IDs; direct shared-storage
 * entries also carry an absolute path for operations that are allowed by Android's permissions.
 */
data class StorageEntry(
    val documentId: String,
    val displayName: String,
    val mimeType: String?,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val lastModifiedMillis: Long?,
    val absolutePath: String? = null
)

/**
 * Storage Access Framework contract. Document IDs are provider-defined and must not be treated
 * as filesystem paths.
 */
interface StorageGateway {
    @Throws(IOException::class)
    fun getDocument(treeUri: Uri, documentId: String): StorageEntry?

    @Throws(IOException::class)
    fun listChildren(treeUri: Uri, parentDocumentId: String): List<StorageEntry>
}
