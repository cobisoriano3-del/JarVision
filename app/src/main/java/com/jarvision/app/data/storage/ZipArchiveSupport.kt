package com.jarvision.app.data.storage

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.ZipInputStream

data class ZipArchiveTestResult(
    val fileCount: Int,
    val directoryCount: Int,
    val expandedBytes: Long
)

/** ZIP-only validation/extraction with path traversal and decompression limits. */
object ZipArchiveSupport {
    const val MAX_ENTRIES = 10_000
    const val MAX_ENTRY_BYTES = 256L * 1024L * 1024L
    const val MAX_TOTAL_BYTES = 1024L * 1024L * 1024L

    fun test(input: InputStream): ZipArchiveTestResult {
        var files = 0
        var directories = 0
        var expanded = 0L
        val seen = mutableSetOf<String>()
        validatedZipInput(input).use { zip ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var entry = zip.nextEntry
            while (entry != null) {
                if (files + directories >= MAX_ENTRIES) {
                    throw IOException("The ZIP contains too many entries (limit: " + MAX_ENTRIES + ").")
                }
                val path = safeEntryPath(entry.name)
                if (!seen.add(path)) throw IOException("The ZIP contains a duplicate entry.")
                if (entry.isDirectory) {
                    directories++
                } else {
                    files++
                    var entryBytes = 0L
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        expanded += read
                        checkLimits(entryBytes, expanded)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return ZipArchiveTestResult(files, directories, expanded)
    }

    fun extract(input: InputStream, destination: File): ZipArchiveTestResult {
        if (!destination.exists() && !destination.mkdirs()) {
            throw IOException("Couldn't create the temporary extraction folder.")
        }
        val root = destination.canonicalFile
        var files = 0
        var directories = 0
        var expanded = 0L
        val seen = mutableSetOf<String>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var entry = zip.nextEntry
            while (entry != null) {
                if (files + directories >= MAX_ENTRIES) {
                    throw IOException("The ZIP contains too many entries (limit: " + MAX_ENTRIES + ").")
                }
                val path = safeEntryPath(entry.name)
                if (!seen.add(path)) throw IOException("The ZIP contains a duplicate entry.")
                val target = File(root, path).canonicalFile
                if (!target.path.startsWith(root.path + File.separator)) {
                    throw IOException("The ZIP contains an unsafe path.")
                }
                if (entry.isDirectory) {
                    if (!target.mkdirs() && !target.isDirectory) {
                        throw IOException("Couldn't create an extracted folder.")
                    }
                    directories++
                } else {
                    val parent = target.parentFile ?: throw IOException("Invalid ZIP path.")
                    if (!parent.mkdirs() && !parent.isDirectory) {
                        throw IOException("Couldn't create the parent folder.")
                    }
                    if (target.exists()) throw IOException("The ZIP would overwrite an extracted item.")
                    target.outputStream().buffered().use { output ->
                        var entryBytes = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            expanded += read
                            checkLimits(entryBytes, expanded)
                            output.write(buffer, 0, read)
                        }
                    }
                    files++
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return ZipArchiveTestResult(files, directories, expanded)
    }

    private fun validatedZipInput(input: InputStream): ZipInputStream {
        val pushback = PushbackInputStream(BufferedInputStream(input), 4)
        val header = ByteArray(4)
        var count = 0
        while (count < header.size) {
            val read = pushback.read(header, count, header.size - count)
            if (read < 0) break
            count += read
        }
        val validSignature = count == 4 &&
            header[0] == 'P'.code.toByte() &&
            header[1] == 'K'.code.toByte() &&
            (
                (header[2] == 3.toByte() && header[3] == 4.toByte()) ||
                (header[2] == 5.toByte() && header[3] == 6.toByte()) ||
                (header[2] == 7.toByte() && header[3] == 8.toByte())
            )
        if (!validSignature) {
            pushback.close()
            throw IOException("This file doesn't contain a readable ZIP archive.")
        }
        pushback.unread(header, 0, count)
        return ZipInputStream(pushback)
    }

    internal fun safeEntryPath(rawName: String): String {
        val normalized = rawName.replace('\\', '/')
        if (normalized.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(normalized)) {
            throw IOException("The ZIP contains an absolute path.")
        }
        val segments = normalized.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty() || segments.any { it == "." || it == ".." || it.any { ch -> ch.code < 32 } }) {
            throw IOException("The ZIP contains an unsafe path.")
        }
        return segments.joinToString(File.separator)
    }

    private fun checkLimits(entryBytes: Long, totalBytes: Long) {
        if (entryBytes > MAX_ENTRY_BYTES) throw IOException("A ZIP entry exceeds the 256 MiB expanded-size limit.")
        if (totalBytes > MAX_TOTAL_BYTES) throw IOException("The ZIP exceeds the 1 GiB total expanded-size limit.")
    }
}
