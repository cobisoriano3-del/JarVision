package com.jarvision.app.data.projects

import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProjectRepositoryTest {
    private fun withRepository(block: (ProjectRepository, File) -> Unit) {
        val root = createTempDirectory("jarvision-project-test").toFile()
        try {
            block(ProjectRepository(root), root)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun metadataSurvivesRepositoryRecreationAndCreatesWorkspace() = withRepository { repo, root ->
        val created = repo.createProject("Demo Project")
        assertTrue(File(created.directoryPath).isDirectory)
        assertEquals(listOf(created), ProjectRepository(root).listProjects())
    }

    @Test
    fun duplicateNamesAreRejectedCaseInsensitively() = withRepository { repo, _ ->
        repo.createProject("Demo")
        try {
            repo.createProject("demo")
            fail("Expected duplicate rejection")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("already exists"))
        }
    }

    @Test
    fun invalidNamesAreRejected() = withRepository { repo, _ ->
        listOf("", "  ", "../escape", "bad/name", "bad\\name", "x".repeat(65)).forEach { name ->
            try {
                repo.createProject(name)
                fail("Expected invalid-name rejection: " + name)
            } catch (_: IllegalArgumentException) {
                // Expected validation failure.
            }
        }
    }

    @Test
    fun failedRegistryWritePreservesExistingProjects() = withRepository { repo, appFilesDir ->
        val existing = repo.createProject("Existing")
        val tempRegistry = File(appFilesDir, "projects/projects.properties.tmp")
        assertTrue(tempRegistry.mkdir())

        try {
            repo.createProject("Should roll back")
            fail("Expected the registry write to fail")
        } catch (_: IOException) {
            // Simulated write failure: temporary registry path is a directory.
        }

        assertEquals(listOf(existing), repo.listProjects())
        assertTrue(File(existing.directoryPath).isDirectory)
    }
    @Test
    fun workspaceSupportsFoldersTextFilesRenameAndNestedNavigation() = withRepository { repo, _ ->
        val project = repo.createProject("Workspace")
        val sourceFolder = repo.createWorkspaceFolder(project.id, "", "src")
        val readme = repo.createWorkspaceTextFile(project.id, "", "README")
        val nestedFile = repo.createWorkspaceTextFile(project.id, sourceFolder.relativePath, "main")

        assertEquals("README.txt", readme.name)
        assertEquals(listOf("src", "README.txt"), repo.listWorkspaceEntries(project.id).map { it.name })
        assertEquals(1, repo.listWorkspaceEntries(project.id).first().childCount)
        assertEquals("main.txt", repo.listWorkspaceEntries(project.id, "src").single().name)

        repo.writeWorkspaceTextFile(project.id, readme.relativePath, "JarVision workspace")
        assertEquals("JarVision workspace", repo.readWorkspaceTextFile(project.id, readme.relativePath))
        repo.renameWorkspaceEntry(project.id, nestedFile.relativePath, "entry")
        assertEquals("entry.txt", repo.listWorkspaceEntries(project.id, "src").single().name)
    }

    @Test
    fun workspaceRejectsTraversalAndNeverOverwritesOnCreateOrRename() = withRepository { repo, root ->
        val project = repo.createProject("Workspace")
        val original = repo.createWorkspaceTextFile(project.id, "", "notes")
        repo.createWorkspaceTextFile(project.id, "", "other")

        try {
            repo.createWorkspaceFolder(project.id, "../outside", "escaped")
            fail("Expected traversal rejection")
        } catch (_: IllegalArgumentException) {
            // Expected path validation failure.
        }

        try {
            repo.createWorkspaceTextFile(project.id, "", "notes")
            fail("Expected duplicate creation rejection")
        } catch (_: IOException) {
            // Existing items must not be truncated.
        }

        try {
            repo.renameWorkspaceEntry(project.id, original.relativePath, "other.txt")
            fail("Expected rename conflict rejection")
        } catch (_: IOException) {
            // Existing target must remain untouched.
        }

        assertTrue(File(project.directoryPath, "notes.txt").isFile)
        assertTrue(File(project.directoryPath, "other.txt").isFile)
        assertTrue(!File(root, "outside").exists())
    }

    @Test
    fun workspaceTextEditorEnforcesItsSizeLimit() = withRepository { repo, _ ->
        val project = repo.createProject("Workspace")
        val file = repo.createWorkspaceTextFile(project.id, "", "notes")
        val oversized = "x".repeat(ProjectRepository.MAX_WORKSPACE_TEXT_BYTES.toInt() + 1)
        try {
            repo.writeWorkspaceTextFile(project.id, file.relativePath, oversized)
            fail("Expected the 1 MiB editor limit")
        } catch (_: IOException) {
            // The workspace editor is intentionally bounded.
        }
        assertEquals("", repo.readWorkspaceTextFile(project.id, file.relativePath))
    }

}
