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


    @Test
    fun deleteProjectRemovesRegistryAndWorkspace() = withRepository { repo, _ ->
        val project = repo.createProject("Delete me")
        val workspace = File(project.directoryPath, "notes.txt")
        workspace.writeText("private workspace")

        assertTrue(repo.deleteProject(project.id))
        assertTrue(repo.listProjects().isEmpty())
        assertTrue(!File(project.directoryPath).exists())
    }

    @Test
    fun failedProjectDeleteRestoresWorkspaceAndRegistry() = withRepository { repo, appFilesDir ->
        val project = repo.createProject("Keep me")
        File(project.directoryPath, "notes.txt").writeText("preserve")
        val temporaryRegistry = File(appFilesDir, "projects/projects.properties.delete.tmp")
        assertTrue(temporaryRegistry.mkdir())

        try {
            repo.deleteProject(project.id)
            fail("Expected the registry write to fail")
        } catch (_: IOException) {
            // A directory at the temporary registry path prevents the new registry from being written.
        }

        assertEquals(listOf(project), repo.listProjects())
        assertEquals("preserve", File(project.directoryPath, "notes.txt").readText())
        assertTrue(File(project.directoryPath).isDirectory)
    }

    @Test
    fun stagedWorkspaceFolderCanBeImportedIntoAnotherProjectWithoutOverwriting() = withRepository { repo, root ->
        val sourceProject = repo.createProject("Source")
        val destinationProject = repo.createProject("Destination")
        val sourceFolder = repo.createWorkspaceFolder(sourceProject.id, "", "src")
        val nested = repo.createWorkspaceTextFile(sourceProject.id, sourceFolder.relativePath, "main")
        repo.writeWorkspaceTextFile(sourceProject.id, nested.relativePath, "println(42)")

        val staged = File(root, "stage-src")
        repo.stageWorkspaceEntry(sourceProject.id, sourceFolder.relativePath, staged)
        repo.importStagedEntry(destinationProject.id, "", staged, "src-copy")

        assertEquals(
            "println(42)",
            File(destinationProject.directoryPath, "src-copy/main.txt").readText()
        )
        assertTrue(repo.workspaceEntryExists(destinationProject.id, "", "SRC-COPY"))

        try {
            repo.importStagedEntry(destinationProject.id, "", staged, "src-copy")
            fail("Expected destination collision rejection")
        } catch (_: IOException) {
            // Import must never overwrite an existing project item.
        }

        assertEquals(
            "println(42)",
            File(destinationProject.directoryPath, "src-copy/main.txt").readText()
        )
    }

    @Test
    fun stagedProjectContainsTheCompleteWorkspaceTree() = withRepository { repo, root ->
        val project = repo.createProject("Publish me")
        val folder = repo.createWorkspaceFolder(project.id, "", "assets")
        val nestedFile = repo.createWorkspaceTextFile(project.id, folder.relativePath, "readme")
        repo.writeWorkspaceTextFile(project.id, nestedFile.relativePath, "asset notes")
        val staging = File(root, "publish-stage")

        repo.stageProject(project.id, staging)

        assertEquals("asset notes", File(staging, "assets/readme.txt").readText())
        assertTrue(File(project.directoryPath, "assets/readme.txt").isFile)
        assertEquals(listOf(project), repo.listProjects())
    }

    @Test
    fun deleteWorkspaceEntryRemovesOnlyTheRequestedItem() = withRepository { repo, _ ->
        val project = repo.createProject("Workspace delete")
        val folder = repo.createWorkspaceFolder(project.id, "", "remove")
        repo.createWorkspaceTextFile(project.id, "", "keep")
        repo.createWorkspaceTextFile(project.id, folder.relativePath, "nested")

        repo.deleteWorkspaceEntry(project.id, folder.relativePath)

        assertTrue(!File(project.directoryPath, "remove").exists())
        assertTrue(File(project.directoryPath, "keep.txt").isFile)
    }


    @Test
    fun projectDirectoryMoveIsRecoveredAfterInterruptedDeletion() = withRepository { repo, _ ->
        val project = repo.createProject("Recover me")
        val originalDirectory = File(project.directoryPath)
        File(originalDirectory, "notes.txt").writeText("recoverable")
        val tombstone = File(originalDirectory.parentFile, ".deleted-${project.id}")
        assertTrue(originalDirectory.renameTo(tombstone))

        val recovered = repo.listProjects()

        assertEquals(listOf(project), recovered)
        assertEquals("recoverable", File(project.directoryPath, "notes.txt").readText())
        assertTrue(!tombstone.exists())
    }

}
