package com.jarvision.app.data.storage

import android.net.Uri
import java.io.IOException

/**
 * A storage item represented by a provider document ID rather than a filesystem path.
 * Android Storage Access Framework providers are not required to expose normal paths.
 */
data class StorageEntry(
    val documentId: String,
    val displayName: String,
    val mimeType: String?,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val lastModifiedMillis: Long?
)

interface StorageGateway {
    @Throws(IOException::class)
    fun getDocument(treeUri: Uri, documentId: String): StorageEntry?

    @Throws(IOException::class)
    fun listChildren(treeUri: Uri, parentDocumentId: String): List<StorageEntry>
}
