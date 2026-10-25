package com.jarvision.app.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipArchiveSupportTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun testsValidZipAndCountsEntries() {
        val bytes = zipOf("docs/" to null, "docs/readme.txt" to "hello", "data.json" to "{}")
        val result = ZipArchiveSupport.test(ByteArrayInputStream(bytes))
        assertEquals(2, result.fileCount)
        assertEquals(1, result.directoryCount)
        assertEquals(7L, result.expandedBytes)
    }

    @Test
    fun extractsValidZipWithinDestination() {
        val destination = temporaryFolder.newFolder("valid-zip")
        try {
            ZipArchiveSupport.extract(
                ByteArrayInputStream(zipOf("folder/note.txt" to "safe")),
                destination
            )
            assertEquals("safe", File(destination, "folder/note.txt").readText())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test
    fun rejectsParentTraversal() {
        val error = runCatching {
            ZipArchiveSupport.test(ByteArrayInputStream(zipOf("../escaped.txt" to "nope")))
        }.exceptionOrNull()
        assertTrue(error is IOException)
    }

    @Test
    fun rejectsAbsolutePaths() {
        val error = runCatching {
            ZipArchiveSupport.test(ByteArrayInputStream(zipOf("/absolute.txt" to "nope")))
        }.exceptionOrNull()
        assertTrue(error is IOException)
    }

    @Test
    fun rejectsNonZipInput() {
        val error = runCatching {
            ZipArchiveSupport.test(ByteArrayInputStream("not a zip".toByteArray()))
        }.exceptionOrNull()
        assertTrue(error is IOException)
    }

    @Test
    fun extractionRejectsNonZipInput() {
        val destination = temporaryFolder.newFolder("invalid-zip")
        try {
            val error = runCatching {
                ZipArchiveSupport.extract(ByteArrayInputStream("not a zip".toByteArray()), destination)
            }.exceptionOrNull()
            assertTrue(error is IOException)
        } finally {
            destination.deleteRecursively()
        }
    }

    private fun zipOf(vararg items: Pair<String, String?>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            items.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                contents?.let { zip.write(it.toByteArray(Charsets.UTF_8)) }
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
