package com.jarvision.app.data.storage

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SevenZipArchiveSupportTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun storageEntry(file: File) = StorageEntry(
        documentId = file.absolutePath,
        displayName = file.name,
        mimeType = null,
        isDirectory = file.isDirectory,
        sizeBytes = file.takeIf { it.isFile }?.length(),
        lastModifiedMillis = file.lastModified(),
        absolutePath = file.absolutePath
    )

    @Test
    fun createsReadableArchiveWithNestedFoldersAndUnicodeNames() {
        val root = temporaryFolder.newFolder("source")
        val nested = File(root, "nested")
        assertTrue(nested.mkdir())
        File(root, "hello.txt").writeText("hello 7z")
        File(nested, "κείμενο.txt").writeText("unicode")
        assertTrue(File(nested, "empty").mkdir())

        val children: (StorageEntry) -> List<StorageEntry> = { entry ->
            File(entry.absolutePath!!).listFiles().orEmpty().map(::storageEntry)
        }
        val open: (StorageEntry) -> java.io.InputStream = { File(it.absolutePath!!).inputStream() }
        val archive = File(temporaryFolder.root, "test.7z")

        val result = SevenZipArchiveSupport.create(
            archive, listOf(storageEntry(File(root, "hello.txt")), storageEntry(nested)), children, open
        )

        assertTrue(archive.isFile && archive.length() > 0)
        assertEquals(2, result.fileCount)
        assertEquals(2, result.directoryCount)

        val names = mutableSetOf<String>()
        val contents = mutableMapOf<String, String>()
        SevenZFile(archive).use { reader ->
            while (true) {
                val item = reader.nextEntry ?: break
                names += item.name
                if (!item.isDirectory) {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    while (true) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    contents[item.name] = output.toString(Charsets.UTF_8.name())
                }
            }
        }
        assertTrue(names.contains("hello.txt"))
        assertTrue(names.contains("nested/"))
        assertTrue(names.contains("nested/κείμενο.txt"))
        assertEquals("hello 7z", contents["hello.txt"])
        assertEquals("unicode", contents["nested/κείμενο.txt"])
    }

    @Test
    fun rejectsUnsafeSourcePathsAndRemovesPartialOutput() {
        val sourceFile = File(temporaryFolder.newFolder("unsafe"), "source.txt").apply { writeText("unsafe") }
        val bad = storageEntry(sourceFile).copy(displayName = "../escape.txt")
        val output = File(temporaryFolder.root, "unsafe.7z")

        val error = runCatching {
            SevenZipArchiveSupport.create(output, listOf(bad), { emptyList() }) {
                File(it.absolutePath!!).inputStream()
            }
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(!output.exists())
    }
}
