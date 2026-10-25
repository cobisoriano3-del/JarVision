package com.jarvision.app.data.storage

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class SharedStorageGatewayTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val gateway = SharedStorageGateway()

    @Test
    fun listChildrenPlacesDirectoriesBeforeFilesAndSortsNames() {
        val root = temporaryFolder.newFolder("root")
        root.resolve("zeta.txt").writeText("z")
        root.resolve("Alpha").mkdir()
        root.resolve("beta").mkdir()
        root.resolve("a.txt").writeText("a")

        val entries = gateway.listChildren(root.absolutePath)

        assertEquals(
            listOf("Alpha", "beta", "a.txt", "zeta.txt"),
            entries.map { it.displayName }
        )
        assertEquals(listOf(true, true, false, false), entries.map { it.isDirectory })
    }

    @Test(expected = IOException::class)
    fun listChildrenRejectsAFileInsteadOfDirectory() {
        val file = temporaryFolder.newFile("not-a-folder.txt")
        gateway.listChildren(file.absolutePath)
    }
}
