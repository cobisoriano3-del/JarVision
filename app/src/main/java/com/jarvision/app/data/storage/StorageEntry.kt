package com.jarvision.app.data.storage

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
