package com.jarvision.app.data.storage

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Date

data class SevenZipArchiveResult(
    val fileCount: Int,
    val directoryCount: Int,
    val expandedBytes: Long
)

/** Streams selected entries from a storage adapter into a seekable 7z output file. */
object SevenZipArchiveSupport {
    fun create(
        outputFile: File,
        sources: List<StorageEntry>,
        listChildren: (StorageEntry) -> List<StorageEntry>,
        openInput: (StorageEntry) -> InputStream
    ): SevenZipArchiveResult {
        if (sources.isEmpty()) throw IOException("Select at least one item to compress.")
        outputFile.parentFile?.let {
            if (!it.exists() && !it.mkdirs()) throw IOException("Couldn't prepare the 7z output folder.")
        }

        val seenPaths = mutableSetOf<String>()
        val counts = longArrayOf(0L, 0L, 0L)
        try {
            SevenZOutputFile(outputFile).use { archive ->
                archive.setContentCompression(SevenZMethod.LZMA2)
                sources.forEach { source ->
                    addEntry(archive, source, source.displayName, listChildren, openInput, seenPaths, counts, 0)
                }
            }
        } catch (error: Exception) {
            outputFile.delete()
            if (error is IOException) throw error
            throw IOException("Couldn't create the 7z archive: " + (error.message ?: "archive error."), error)
        }

        return SevenZipArchiveResult(counts[0].toInt(), counts[1].toInt(), counts[2])
    }

    private fun addEntry(
        archive: SevenZOutputFile,
        source: StorageEntry,
        archivePath: String,
        listChildren: (StorageEntry) -> List<StorageEntry>,
        openInput: (StorageEntry) -> InputStream,
        seenPaths: MutableSet<String>,
        counts: LongArray,
        depth: Int
    ) {
        if (depth > 256) throw IOException("The selected directory structure is too deeply nested.")
        if (counts[0] + counts[1] >= ZipArchiveSupport.MAX_ENTRIES) {
            throw IOException("The selection contains too many entries.")
        }

        val safePath = ZipArchiveSupport.safeEntryPath(archivePath)
        if (!seenPaths.add(safePath)) throw IOException("The selection contains duplicate archive paths.")

        val archiveEntry = SevenZArchiveEntry().apply {
            setName(if (source.isDirectory) "$safePath/" else safePath)
            setDirectory(source.isDirectory)
            setHasStream(!source.isDirectory)
            source.lastModifiedMillis?.takeIf { it > 0L }?.let { setLastModifiedDate(Date(it)) }
        }
        archive.putArchiveEntry(archiveEntry)

        if (source.isDirectory) {
            archive.closeArchiveEntry()
            counts[1]++
            listChildren(source).forEach { child ->
                addEntry(
                    archive, child, "$safePath/\${child.displayName}",
                    listChildren, openInput, seenPaths, counts, depth + 1
                )
            }
            return
        }

        var entryBytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        openInput(source).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                entryBytes += read.toLong()
                counts[2] += read.toLong()
                if (entryBytes > ZipArchiveSupport.MAX_ENTRY_BYTES) {
                    throw IOException("A file exceeds the 256 MiB 7z compression limit.")
                }
                if (counts[2] > ZipArchiveSupport.MAX_TOTAL_BYTES) {
                    throw IOException("The selection exceeds the 1 GiB 7z compression limit.")
                }
                archive.write(buffer, 0, read)
            }
        }
        archive.closeArchiveEntry()
        counts[0]++
    }
}
