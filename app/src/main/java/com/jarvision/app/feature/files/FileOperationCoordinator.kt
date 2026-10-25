package com.jarvision.app.feature.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.jarvision.app.data.storage.SafStorageGateway
import com.jarvision.app.data.storage.SharedStorageGateway
import com.jarvision.app.data.storage.ShizukuStorageAdapter
import com.jarvision.app.data.storage.StorageEntry
import com.jarvision.app.data.storage.ZipArchiveSupport
import com.jarvision.app.data.storage.ZipArchiveTestResult
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URLConnection
import java.util.Locale

/**
 * Executes file operations through the active storage adapter. Writes are supported only for
 * shared storage with Android access, and for SAF trees with a persisted write grant.
 * Shizuku is deliberately read-only.
 */
class FileOperationCoordinator(
    private val context: Context,
    private val shizuku: ShizukuStorageAdapter
) {
    private val resolver: ContentResolver = context.contentResolver
    private val shared = SharedStorageGateway()
    private val saf = SafStorageGateway(resolver)

    fun openInputStream(
        mode: StorageAccessMode,
        treeUriString: String?,
        entry: StorageEntry
    ): InputStream = when (mode) {
        StorageAccessMode.SHARED_STORAGE -> {
            val file = checkedSharedFile(entry.absolutePath ?: entry.documentId)
            if (!file.isFile || !file.canRead()) throw IOException("This file isn't readable.")
            file.inputStream()
        }
        StorageAccessMode.DOCUMENT_TREE -> resolver.openInputStream(
            documentUri(parseTreeUri(treeUriString), entry.documentId)
        ) ?: throw IOException("The storage provider couldn't open this file.")
        StorageAccessMode.SHIZUKU_STORAGE -> shizuku.openReadOnlyInputStream(
            entry.absolutePath ?: throw IOException("Shizuku returned no file path.")
        )
        StorageAccessMode.NONE -> throw IOException("Choose a storage location first.")
    }

    fun findNamedChild(
        mode: StorageAccessMode,
        treeUriString: String?,
        parentDocumentId: String,
        parentAbsolutePath: String?,
        name: String
    ): StorageEntry? = when (mode) {
        StorageAccessMode.SHARED_STORAGE -> {
            val parent = checkedSharedDirectory(parentAbsolutePath ?: parentDocumentId)
            shared.listChildren(parent.path).firstOrNull {
                it.displayName.equals(name, ignoreCase = true)
            }
        }
        StorageAccessMode.DOCUMENT_TREE -> saf.listChildren(
            parseTreeUri(treeUriString), parentDocumentId
        ).firstOrNull { it.displayName.equals(name, ignoreCase = true) }
        else -> throw IOException("Changes are unavailable in read-only Shizuku mode.")
    }

    fun rename(
        mode: StorageAccessMode,
        treeUriString: String?,
        entry: StorageEntry,
        parentDocumentId: String,
        parentAbsolutePath: String?,
        newName: String
    ) {
        validateName(newName)
        when (mode) {
            StorageAccessMode.SHARED_STORAGE -> {
                val source = checkedSharedFile(entry.absolutePath ?: entry.documentId)
                val parent = checkedSharedDirectory(parentAbsolutePath ?: parentDocumentId)
                val target = File(parent, newName).canonicalFile
                if (source.canonicalFile == target) return
                if (target.exists()) throw IOException("An item with that name already exists.")
                if (!source.renameTo(target)) throw IOException("Android couldn't rename this item.")
            }
            StorageAccessMode.DOCUMENT_TREE -> {
                val treeUri = parseTreeUri(treeUriString)
                requireWriteGrant(treeUri)
                val conflict = saf.listChildren(treeUri, parentDocumentId).firstOrNull {
                    it.displayName.equals(newName, ignoreCase = true) && it.documentId != entry.documentId
                }
                if (conflict != null) throw IOException("An item with that name already exists.")
                if (DocumentsContract.renameDocument(
                        resolver, documentUri(treeUri, entry.documentId), newName
                    ) == null
                ) throw IOException("The storage provider couldn't rename this item.")
            }
            else -> throw IOException("Changes are unavailable in read-only Shizuku mode.")
        }
    }

    fun delete(mode: StorageAccessMode, treeUriString: String?, entry: StorageEntry) {
        when (mode) {
            StorageAccessMode.SHARED_STORAGE -> {
                val file = checkedSharedFile(entry.absolutePath ?: entry.documentId)
                if (!file.deleteRecursively()) throw IOException("Android couldn't delete this item.")
            }
            StorageAccessMode.DOCUMENT_TREE -> {
                val treeUri = parseTreeUri(treeUriString)
                requireWriteGrant(treeUri)
                if (!DocumentsContract.deleteDocument(resolver, documentUri(treeUri, entry.documentId))) {
                    throw IOException("The storage provider couldn't delete this item.")
                }
            }
            else -> throw IOException("Changes are unavailable in read-only Shizuku mode.")
        }
    }

    /**
     * Copy is completed before a cut removes its source. Replace is only allowed after the UI
     * obtains explicit confirmation; no operation silently overwrites an existing item.
     */
    fun copyOrMove(
        mode: StorageAccessMode,
        treeUriString: String?,
        source: StorageEntry,
        destinationParentDocumentId: String,
        destinationParentAbsolutePath: String?,
        targetName: String,
        moveSource: Boolean,
        replaceExisting: Boolean
    ): String {
        validateName(targetName)
        when (mode) {
            StorageAccessMode.SHARED_STORAGE -> {
                val sourceFile = checkedSharedFile(source.absolutePath ?: source.documentId)
                val destinationParent = checkedSharedDirectory(
                    destinationParentAbsolutePath ?: destinationParentDocumentId
                )
                var replaceNotice: String? = null
                val target = File(destinationParent, targetName).canonicalFile
                if (sourceFile.canonicalFile == target) {
                    if (moveSource) return "This item is already in that folder."
                    throw IOException("Copying an item over itself isn't allowed.")
                }
                if (source.isDirectory &&
                    target.path.startsWith(sourceFile.canonicalPath + File.separator)
                ) throw IOException("A folder can't be copied into itself or a subfolder.")
                if (target.exists()) {
                    if (!replaceExisting) throw IOException("An item with that name already exists.")
                    val staged = File(destinationParent, ".jarvision-stage-" + System.nanoTime())
                    val backup = File(destinationParent, ".jarvision-backup-" + System.nanoTime())
                    try {
                        copyFileTree(sourceFile, staged)
                        if (!target.renameTo(backup)) {
                            throw IOException("Couldn't safely move the existing item aside for replacement.")
                        }
                        if (!staged.renameTo(target)) {
                            if (!backup.renameTo(target)) {
                                throw IOException("Replacement failed; the previous item is preserved as " + backup.name + ".")
                            }
                            throw IOException("Replacement failed; the original item was restored.")
                        }
                        if (!backup.deleteRecursively()) {
                            replaceNotice = "The previous item remains as " + backup.name + "."
                        }
                    } catch (error: Exception) {
                        staged.deleteRecursively()
                        if (backup.exists() && !target.exists()) backup.renameTo(target)
                        throw error
                    }
                } else {
                    try {
                        copyFileTree(sourceFile, target)
                    } catch (error: Exception) {
                        target.deleteRecursively()
                        throw error
                    }
                }
                if (moveSource) {
                    if (!sourceFile.deleteRecursively()) {
                        return "Copied successfully, but the original couldn't be removed; both copies remain."
                    }
                    return if (replaceNotice == null) "Moved successfully."
                    else "Moved successfully. " + replaceNotice
                }
                return replaceNotice?.let { "Copied successfully. " + it } ?: "Copied successfully."
            }
            StorageAccessMode.DOCUMENT_TREE -> {
                val treeUri = parseTreeUri(treeUriString)
                requireWriteGrant(treeUri)
                val sourceUri = documentUri(treeUri, source.documentId)
                val destinationParentUri = documentUri(treeUri, destinationParentDocumentId)
                var replaceNotice: String? = null
                val existing = saf.listChildren(treeUri, destinationParentDocumentId)
                    .firstOrNull { it.displayName.equals(targetName, ignoreCase = true) }
                if (existing != null) {
                    if (!replaceExisting) throw IOException("An item with that name already exists.")
                    if (existing.documentId == source.documentId) {
                        if (moveSource) return "This item is already in that folder."
                        throw IOException("Copying an item over itself isn't allowed.")
                    }
                    val stagedName = ".jarvision-stage-" + System.nanoTime()
                    val backupName = ".jarvision-backup-" + System.nanoTime()
                    val stagedUri = copySafNode(treeUri, source, destinationParentUri, stagedName)
                    val backupUri = DocumentsContract.renameDocument(
                        resolver, documentUri(treeUri, existing.documentId), backupName
                    )
                    if (backupUri == null) {
                        runCatching { DocumentsContract.deleteDocument(resolver, stagedUri) }
                        throw IOException("This provider can't safely replace an existing item; use Keep both instead.")
                    }
                    val replacedUri = DocumentsContract.renameDocument(resolver, stagedUri, targetName)
                    if (replacedUri == null) {
                        val restoredUri = DocumentsContract.renameDocument(
                            resolver, backupUri, existing.displayName
                        )
                        runCatching { DocumentsContract.deleteDocument(resolver, stagedUri) }
                        if (restoredUri == null) {
                            throw IOException("Replacement failed; the previous item is preserved under " + backupName + ".")
                        }
                        throw IOException("Replacement failed; the original item was restored.")
                    }
                    val removedBackup = runCatching {
                        DocumentsContract.deleteDocument(resolver, backupUri)
                    }.getOrDefault(false)
                    if (!removedBackup) {
                        replaceNotice = "The previous item remains under " + backupName + "."
                    }
                } else {
                    copySafNode(treeUri, source, destinationParentUri, targetName)
                }
                if (moveSource) {
                    val deleted = runCatching {
                        DocumentsContract.deleteDocument(resolver, sourceUri)
                    }.getOrDefault(false)
                    if (!deleted) {
                        return "Copied successfully, but the original couldn't be removed; both copies remain."
                    }
                    return if (replaceNotice == null) "Moved successfully."
                    else "Moved successfully. " + replaceNotice
                }
                return replaceNotice?.let { "Copied successfully. " + it } ?: "Copied successfully."
            }
            else -> throw IOException("Changes are unavailable in read-only Shizuku mode.")
        }
    }

    fun testZip(
        mode: StorageAccessMode,
        treeUriString: String?,
        entry: StorageEntry
    ): ZipArchiveTestResult {
        if (entry.isDirectory || !entry.displayName.lowercase(Locale.ROOT).endsWith(".zip")) {
            throw IOException("Only ZIP files are supported.")
        }
        return openInputStream(mode, treeUriString, entry).use { ZipArchiveSupport.test(it) }
    }

    /** Extracts into the current folder without overwriting anything. On failure, created outputs are rolled back. */
    fun extractZipHere(
        mode: StorageAccessMode,
        treeUriString: String?,
        archive: StorageEntry,
        destinationParentDocumentId: String,
        destinationParentAbsolutePath: String?
    ): String {
        if (mode != StorageAccessMode.SHARED_STORAGE && mode != StorageAccessMode.DOCUMENT_TREE) {
            throw IOException("Extraction is disabled in read-only Shizuku mode.")
        }
        if (archive.isDirectory || !archive.displayName.lowercase(Locale.ROOT).endsWith(".zip")) {
            throw IOException("Only ZIP files can be extracted.")
        }
        val stage = File(context.cacheDir, "jarvision-zip-here-" + System.nanoTime())
        if (!stage.mkdirs()) throw IOException("Couldn't prepare a temporary extraction folder.")
        val createdShared = mutableListOf<File>()
        val createdSaf = mutableListOf<Uri>()
        try {
            val result = openInputStream(mode, treeUriString, archive).use {
                ZipArchiveSupport.extract(it, stage)
            }
            val children = stage.listFiles() ?: throw IOException("Couldn't read staged ZIP contents.")
            when (mode) {
                StorageAccessMode.SHARED_STORAGE -> {
                    val parent = checkedSharedDirectory(destinationParentAbsolutePath ?: destinationParentDocumentId)
                    val names = shared.listChildren(parent.path).map { it.displayName.lowercase(Locale.ROOT) }.toSet()
                    if (children.any { it.name.lowercase(Locale.ROOT) in names }) {
                        throw IOException("An extracted item already exists here. Use Extract to folder to avoid overwriting.")
                    }
                    children.forEach { child ->
                        val target = File(parent, child.name)
                        createdShared.add(target)
                        copyFileTree(child, target)
                    }
                }
                StorageAccessMode.DOCUMENT_TREE -> {
                    val treeUri = parseTreeUri(treeUriString)
                    requireWriteGrant(treeUri)
                    val names = saf.listChildren(treeUri, destinationParentDocumentId)
                        .map { it.displayName.lowercase(Locale.ROOT) }.toSet()
                    if (children.any { it.name.lowercase(Locale.ROOT) in names }) {
                        throw IOException("An extracted item already exists here. Use Extract to folder to avoid overwriting.")
                    }
                    copyLocalDirectoryToSaf(
                        stage,
                        documentUri(treeUri, destinationParentDocumentId),
                        createdSaf
                    )
                }
                else -> throw IOException("Extraction is disabled in read-only Shizuku mode.")
            }
            return "Extracted " + result.fileCount + " files here."
        } catch (error: Exception) {
            createdShared.asReversed().forEach { runCatching { it.deleteRecursively() } }
            createdSaf.asReversed().forEach { uri -> runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
            throw error
        } finally {
            stage.deleteRecursively()
        }
    }

    /** Stage ZIP output in private cache, validate all member paths, then publish into a new folder. */
    fun extractZipToFolder(
        mode: StorageAccessMode,
        treeUriString: String?,
        archive: StorageEntry,
        destinationParentDocumentId: String,
        destinationParentAbsolutePath: String?
    ): String {
        if (mode != StorageAccessMode.SHARED_STORAGE && mode != StorageAccessMode.DOCUMENT_TREE) {
            throw IOException("Extraction is disabled in read-only Shizuku mode.")
        }
        if (archive.isDirectory || !archive.displayName.lowercase(Locale.ROOT).endsWith(".zip")) {
            throw IOException("Only ZIP files can be extracted.")
        }

        val parentName = archive.displayName.substringBeforeLast('.', archive.displayName)
            .ifBlank { "Extracted" }
        val stage = File(context.cacheDir, "jarvision-zip-stage-" + System.nanoTime())
        if (!stage.mkdirs()) throw IOException("Couldn't prepare a temporary extraction folder.")
        var createdSharedFolder: File? = null
        var createdSafFolder: Uri? = null
        try {
            openInputStream(mode, treeUriString, archive).use { ZipArchiveSupport.extract(it, stage) }
            val targetName = uniqueFolderName(
                mode, treeUriString, destinationParentDocumentId, destinationParentAbsolutePath, parentName
            )
            when (mode) {
                StorageAccessMode.SHARED_STORAGE -> {
                    val parent = checkedSharedDirectory(destinationParentAbsolutePath ?: destinationParentDocumentId)
                    val target = File(parent, targetName)
                    if (!target.mkdir()) throw IOException("Couldn't create the extraction folder.")
                    createdSharedFolder = target
                    val children = stage.listFiles() ?: throw IOException("Couldn't read staged ZIP contents.")
                    children.forEach { child -> copyFileTree(child, File(target, child.name)) }
                }
                StorageAccessMode.DOCUMENT_TREE -> {
                    val treeUri = parseTreeUri(treeUriString)
                    requireWriteGrant(treeUri)
                    val target = DocumentsContract.createDocument(
                        resolver,
                        documentUri(treeUri, destinationParentDocumentId),
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        targetName
                    ) ?: throw IOException("The storage provider couldn't create the extraction folder.")
                    createdSafFolder = target
                    copyLocalDirectoryToSaf(stage, target)
                }
                else -> throw IOException("Extraction is disabled in read-only Shizuku mode.")
            }
            return targetName
        } catch (error: Exception) {
            createdSharedFolder?.let { runCatching { it.deleteRecursively() } }
            createdSafFolder?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            throw error
        } finally {
            stage.deleteRecursively()
        }
    }

    /** Only used when a protected Shizuku item must be handed to Android's explicit share sheet. */
    fun stageShareCopy(mode: StorageAccessMode, treeUriString: String?, entry: StorageEntry): File {
        if (entry.isDirectory) throw IOException("Folders can't be shared as a single file.")
        val folder = File(context.cacheDir, "shared")
        if (!folder.exists() && !folder.mkdirs()) throw IOException("Couldn't prepare the share cache.")
        val extension = entry.displayName.substringAfterLast('.', "bin")
            .replace(Regex("[^A-Za-z0-9]"), "").take(10).ifBlank { "bin" }
        val target = File.createTempFile("jarvision-share-", "." + extension, folder)
        try {
            openInputStream(mode, treeUriString, entry).use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 512L * 1024L * 1024L) {
                            throw IOException("This file exceeds the 512 MiB sharing limit.")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            return target
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private fun uniqueFolderName(
        mode: StorageAccessMode,
        treeUriString: String?,
        parentDocumentId: String,
        parentPath: String?,
        desired: String
    ): String {
        var candidate = desired
        var suffix = 1
        while (findNamedChild(mode, treeUriString, parentDocumentId, parentPath, candidate) != null) {
            candidate = desired + " (" + suffix + ")"
            suffix++
            if (suffix > 10_000) throw IOException("Couldn't find an unused extraction folder name.")
        }
        return candidate
    }

    private fun copySafNode(
        treeUri: Uri,
        source: StorageEntry,
        destinationParentUri: Uri,
        destinationName: String
    ): Uri {
        val mime = if (source.isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            source.mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                ?: URLConnection.guessContentTypeFromName(destinationName)
                ?: "application/octet-stream"
        }
        val destinationUri = DocumentsContract.createDocument(
            resolver, destinationParentUri, mime, destinationName
        ) ?: throw IOException("The storage provider couldn't create the destination item.")
        try {
            if (source.isDirectory) {
                saf.listChildren(treeUri, source.documentId).forEach { child ->
                    copySafNode(treeUri, child, destinationUri, child.displayName)
                }
            } else {
                resolver.openInputStream(documentUri(treeUri, source.documentId)).use { input ->
                    if (input == null) throw IOException("The storage provider couldn't read the source item.")
                    resolver.openOutputStream(destinationUri, "w").use { output ->
                        if (output == null) throw IOException("The storage provider couldn't write the destination item.")
                        input.copyTo(output)
                    }
                }
            }
            return destinationUri
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, destinationUri) }
            throw error
        }
    }

    private fun copyLocalDirectoryToSaf(
        sourceDir: File,
        destinationUri: Uri,
        created: MutableList<Uri> = mutableListOf()
    ) {
        val children = sourceDir.listFiles() ?: throw IOException("Couldn't read staged ZIP contents.")
        children.forEach { child ->
            val mime = if (child.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR
            else URLConnection.guessContentTypeFromName(child.name) ?: "application/octet-stream"
            val childUri = DocumentsContract.createDocument(resolver, destinationUri, mime, child.name)
                ?: throw IOException("The storage provider couldn't create an extracted item.")
            created.add(childUri)
            try {
                if (child.isDirectory) {
                    copyLocalDirectoryToSaf(child, childUri, created)
                } else {
                    child.inputStream().buffered().use { input ->
                        resolver.openOutputStream(childUri, "w").use { output ->
                            if (output == null) throw IOException("The storage provider couldn't write an extracted item.")
                            input.copyTo(output)
                        }
                    }
                }
            } catch (error: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, childUri) }
                throw error
            }
        }
    }

    private fun copyFileTree(source: File, target: File) {
        val canonicalSource = source.canonicalFile
        if (!canonicalSource.exists()) throw IOException("The source item no longer exists.")
        if (canonicalSource.isDirectory) {
            if (!target.mkdirs() && !target.isDirectory) throw IOException("Couldn't create the destination folder.")
            val children = canonicalSource.listFiles() ?: throw IOException("Couldn't list a source folder.")
            children.forEach { child ->
                val canonicalChild = child.canonicalFile
                if (!canonicalChild.path.startsWith(canonicalSource.path + File.separator)) {
                    throw IOException("A folder contains an unsafe link; copy stopped.")
                }
                copyFileTree(canonicalChild, File(target, canonicalChild.name))
            }
            target.setLastModified(canonicalSource.lastModified())
        } else {
            target.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("Couldn't create the destination folder.") }
            canonicalSource.inputStream().buffered().use { input ->
                target.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            target.setLastModified(canonicalSource.lastModified())
        }
    }

    private fun checkedSharedDirectory(path: String): File {
        val file = File(path).canonicalFile
        val root = shared.rootDirectory().canonicalFile
        if (file != root && !file.path.startsWith(root.path + File.separator)) {
            throw IOException("Operations are restricted to shared internal storage.")
        }
        if (!file.isDirectory || !file.canRead()) throw IOException("The destination folder isn't accessible.")
        return file
    }

    private fun checkedSharedFile(path: String): File {
        val file = File(path).canonicalFile
        val root = shared.rootDirectory().canonicalFile
        if (file != root && !file.path.startsWith(root.path + File.separator)) {
            throw IOException("Operations are restricted to shared internal storage.")
        }
        if (!file.exists()) throw IOException("This item no longer exists.")
        return file
    }

    private fun requireWriteGrant(treeUri: Uri) {
        val hasWrite = resolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isWritePermission
        }
        if (!hasWrite) {
            throw IOException("This folder only has read access. Choose the folder again and allow read and write access before changing files.")
        }
    }

    private fun parseTreeUri(value: String?): Uri =
        value?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: throw IOException("No selected folder permission is available.")

    private fun documentUri(treeUri: Uri, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    private fun validateName(name: String) {
        if (name.isBlank() || name == "." || name == ".." ||
            name.contains('/') || name.contains('\\') || name.any { it.code < 32 }
        ) throw IOException("Enter a valid name without folder separators.")
    }
}
