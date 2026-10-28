package com.jarvision.app.feature.files

import android.app.Application
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.util.LruCache
import android.util.Size
import androidx.core.graphics.drawable.toBitmap
import com.jarvision.app.data.storage.ShizukuStorageAdapter
import com.jarvision.app.data.storage.StorageEntry
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile

internal enum class EntryVisualKind {
    FOLDER, APP_FOLDER, TEXT, PDF, APK, IMAGE, VIDEO, AUDIO, ARCHIVE, ZIP_UNKNOWN, GENERIC
}

internal data class EntryVisual(val kind: EntryVisualKind, val bitmap: Bitmap? = null)

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
private val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "ts", "mpeg", "mpg")
private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "mid", "midi", "amr")
private val textExtensions = setOf(
    "txt", "md", "markdown", "json", "xml", "yaml", "yml", "toml", "properties",
    "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "html", "htm", "css",
    "log", "cfg", "ini", "csv", "sh", "gradle", "sql", "c", "h", "cpp", "rs", "bat", "ps1"
)
private val archiveExtensions = setOf("7z", "rar", "tar", "gz", "tgz", "bz2", "xz", "jar", "war", "mcpack", "mcaddon")
private val packageNamePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

internal fun isAndroidDataPackageFolder(entry: StorageEntry, state: FilesUiState): Boolean {
    if (!entry.isDirectory || !packageNamePattern.matches(entry.displayName)) return false
    val parentPath = state.currentPath.lastOrNull()?.absolutePath
        ?.replace('\\', '/')
        ?.trimEnd('/')
    val pathMatches = parentPath?.endsWith("/Android/data", ignoreCase = true) == true
    val breadcrumbMatches = state.currentPath.takeLast(2)
        .map { it.displayName.lowercase(Locale.ROOT) } == listOf("android", "data")
    return pathMatches || breadcrumbMatches
}

internal fun defaultEntryVisualKind(entry: StorageEntry, state: FilesUiState): EntryVisualKind {
    if (entry.isDirectory) return if (isAndroidDataPackageFolder(entry, state)) {
        EntryVisualKind.APP_FOLDER
    } else EntryVisualKind.FOLDER
    val extension = entry.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val mime = entry.mimeType.orEmpty().lowercase(Locale.ROOT)
    return when {
        extension == "apk" || mime == "application/vnd.android.package-archive" -> EntryVisualKind.APK
        extension == "pdf" || mime == "application/pdf" -> EntryVisualKind.PDF
        extension == "zip" -> EntryVisualKind.ZIP_UNKNOWN
        extension in imageExtensions || mime.startsWith("image/") -> EntryVisualKind.IMAGE
        extension in videoExtensions || mime.startsWith("video/") -> EntryVisualKind.VIDEO
        extension in audioExtensions || mime.startsWith("audio/") -> EntryVisualKind.AUDIO
        extension in textExtensions || mime.startsWith("text/") -> EntryVisualKind.TEXT
        extension in archiveExtensions -> EntryVisualKind.ARCHIVE
        else -> EntryVisualKind.GENERIC
    }
}

/** Expensive icon, media, and ZIP work happens off the main thread via FilesViewModel. */
internal class EntryVisualLoader(private val application: Application) {
    private val packageManager = application.packageManager
    private val cache = object : LruCache<String, EntryVisual>(MAX_CACHE_KILOBYTES) {
        override fun sizeOf(key: String, value: EntryVisual): Int =
            maxOf(1, (value.bitmap?.byteCount ?: 1024) / 1024)
    }

    fun load(entry: StorageEntry, state: FilesUiState): EntryVisual {
        val key = cacheKey(entry, state)
        cache.get(key)?.let { return it }
        val kind = defaultEntryVisualKind(entry, state)
        val visual = when (kind) {
            EntryVisualKind.APP_FOLDER -> EntryVisual(kind, runCatching { loadInstalledAppIcon(entry.displayName) }.getOrNull())
            EntryVisualKind.APK -> EntryVisual(kind, runCatching { loadApkIcon(entry, state) }.getOrNull())
            EntryVisualKind.IMAGE -> EntryVisual(kind, runCatching { decodeSampledImage(entry, state) }.getOrNull())
            EntryVisualKind.VIDEO -> EntryVisual(kind, runCatching { decodeVideoThumbnail(entry, state) }.getOrNull())
            EntryVisualKind.ZIP_UNKNOWN -> {
                val recognized = runCatching { hasRecognizedArchiveContents(entry, state) }.getOrDefault(false)
                EntryVisual(if (recognized) EntryVisualKind.ARCHIVE else EntryVisualKind.ZIP_UNKNOWN)
            }
            else -> EntryVisual(kind)
        }
        cache.put(key, visual)
        return visual
    }

    fun clear() = cache.evictAll()

    private fun cacheKey(entry: StorageEntry, state: FilesUiState): String = listOf(
        state.accessMode.name,
        state.treeUriString.orEmpty(),
        state.currentPath.lastOrNull()?.documentId.orEmpty(),
        entry.absolutePath ?: entry.documentId,
        entry.displayName,
        entry.sizeBytes?.toString().orEmpty(),
        entry.lastModifiedMillis?.toString().orEmpty()
    ).joinToString("|")

    private fun loadInstalledAppIcon(packageName: String): Bitmap? =
        packageManager.getApplicationIcon(packageName)
            .toBitmap(THUMBNAIL_PIXELS, THUMBNAIL_PIXELS, Bitmap.Config.ARGB_8888)

    @Suppress("DEPRECATION")
    private fun loadApkIcon(entry: StorageEntry, state: FilesUiState): Bitmap? {
        val directFile = directSharedFile(entry, state)
        if (directFile != null && directFile.length() > MAX_APK_BYTES) return null
        val apkFile = directFile ?: copyEntryToCache(entry, state, MAX_APK_BYTES) ?: return null
        val temporary = apkFile != directFile
        return try {
            val info: PackageInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0) ?: return null
            val appInfo = info.applicationInfo ?: return null
            appInfo.sourceDir = apkFile.absolutePath
            appInfo.publicSourceDir = apkFile.absolutePath
            appInfo.loadIcon(packageManager)
                .toBitmap(THUMBNAIL_PIXELS, THUMBNAIL_PIXELS, Bitmap.Config.ARGB_8888)
        } finally {
            if (temporary) apkFile.delete()
        }
    }

    private fun decodeSampledImage(entry: StorageEntry, state: FilesUiState): Bitmap? {
        if ((entry.sizeBytes ?: 0L) > MAX_IMAGE_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openEntryStream(entry, state)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > THUMBNAIL_PIXELS * 2 ||
            bounds.outHeight / sampleSize > THUMBNAIL_PIXELS * 2) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return openEntryStream(entry, state)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun decodeVideoThumbnail(entry: StorageEntry, state: FilesUiState): Bitmap? {
        if (state.accessMode == StorageAccessMode.DOCUMENT_TREE) {
            val treeUri = state.treeUriString?.let(Uri::parse) ?: return null
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.documentId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    application.contentResolver.loadThumbnail(uri, Size(THUMBNAIL_PIXELS, THUMBNAIL_PIXELS), CancellationSignal())
                }.getOrNull()?.let { return it }
            }
            return decodeVideoUri(uri)
        }
        val direct = directSharedFile(entry, state)
        if (direct != null && direct.length() > MAX_VIDEO_BYTES) return null
        val file = direct ?: copyEntryToCache(entry, state, MAX_VIDEO_BYTES) ?: return null
        val temporary = file != direct
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(file, Size(THUMBNAIL_PIXELS, THUMBNAIL_PIXELS), CancellationSignal())
            } else {
                MediaMetadataRetriever().let { retriever ->
                    try {
                        retriever.setDataSource(file.absolutePath)
                        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        retriever.release()
                    }
                }
            }
        } finally {
            if (temporary) file.delete()
        }
    }

    private fun decodeVideoUri(uri: Uri): Bitmap? = MediaMetadataRetriever().let { retriever ->
        try {
            retriever.setDataSource(application, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            retriever.release()
        }
    }

    private fun hasRecognizedArchiveContents(entry: StorageEntry, state: FilesUiState): Boolean {
        val direct = directSharedFile(entry, state)
        if (direct != null && direct.length() > MAX_ARCHIVE_BYTES) return false
        val archive = direct ?: copyEntryToCache(entry, state, MAX_ARCHIVE_BYTES) ?: return false
        val temporary = archive != direct
        return try {
            val names = HashSet<String>()
            ZipFile(archive).use { zip ->
                val entries = zip.entries()
                var count = 0
                while (entries.hasMoreElements() && count < MAX_ARCHIVE_ENTRIES) {
                    names += entries.nextElement().name.replace('\\', '/').lowercase(Locale.ROOT)
                    count++
                }
            }
            val office = names.any {
                it == "word/document.xml" || it.endsWith("/word/document.xml") ||
                    it == "ppt/presentation.xml" || it.endsWith("/ppt/presentation.xml") ||
                    it == "xl/workbook.xml" || it.endsWith("/xl/workbook.xml")
            }
            val jar = names.any { it == "meta-inf/manifest.mf" || it.endsWith("/meta-inf/manifest.mf") }
            val pack = names.any { it == "manifest.json" || it.endsWith("/manifest.json") } &&
                names.any { it == "pack_icon.png" || it.endsWith("/pack_icon.png") }
            office || jar || pack
        } finally {
            if (temporary) archive.delete()
        }
    }

    private fun directSharedFile(entry: StorageEntry, state: FilesUiState): File? {
        if (state.accessMode != StorageAccessMode.SHARED_STORAGE) return null
        val path = entry.absolutePath ?: return null
        return File(path).takeIf { it.isFile && it.canRead() }
    }

    private fun openEntryStream(entry: StorageEntry, state: FilesUiState): InputStream? {
        return when (state.accessMode) {
            StorageAccessMode.SHARED_STORAGE -> entry.absolutePath?.let { path ->
                File(path).takeIf { it.isFile && it.canRead() }?.inputStream()
            }
            StorageAccessMode.SHIZUKU_STORAGE -> entry.absolutePath?.let { path ->
                ShizukuStorageAdapter.get(application).openReadOnlyInputStream(path)
            }
            StorageAccessMode.DOCUMENT_TREE -> {
                val tree = state.treeUriString?.let(Uri::parse) ?: return null
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId)
                application.contentResolver.openInputStream(uri)
            }
            StorageAccessMode.NONE -> null
        }
    }

    private fun copyEntryToCache(entry: StorageEntry, state: FilesUiState, maxBytes: Long): File? {
        val temporary = File.createTempFile("jarvision-visual-", ".tmp", application.cacheDir)
        var keep = false
        try {
            val source = openEntryStream(entry, state) ?: return null
            var total = 0L
            source.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) return null
                        output.write(buffer, 0, read)
                    }
                }
            }
            keep = true
            return temporary
        } catch (_: Exception) {
            return null
        } finally {
            if (!keep) temporary.delete()
        }
    }

    private companion object {
        const val THUMBNAIL_PIXELS = 160
        const val MAX_CACHE_KILOBYTES = 12 * 1024
        const val MAX_IMAGE_BYTES = 32L * 1024L * 1024L
        const val MAX_APK_BYTES = 128L * 1024L * 1024L
        const val MAX_VIDEO_BYTES = 64L * 1024L * 1024L
        const val MAX_ARCHIVE_BYTES = 128L * 1024L * 1024L
        const val MAX_ARCHIVE_ENTRIES = 512
        const val COPY_BUFFER_BYTES = 32 * 1024
    }
}
