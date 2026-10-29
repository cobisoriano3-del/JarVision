package com.jarvision.app.data.projects

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProjectRepositoryTest {
    private fun withRepository(block: (ProjectRepository, File) -> Unit) {
        val root = createTempDirectory("jarvision-project-test").toFile()
        try { block(ProjectRepository(root), root) } finally { root.deleteRecursively() }
    }

    @Test fun metadataSurvivesRepositoryRecreationAndCreatesWorkspace() = withRepository { repo, root ->
        val created = repo.createProject("Demo Project")
        assertTrue(File(created.directoryPath).isDirectory)
        assertEquals(listOf(created), ProjectRepository(root).listProjects())
    }

    @Test fun duplicateNamesAreRejectedCaseInsensitively() = withRepository { repo, _ ->
        repo.createProject("Demo")
        try { repo.createProject("demo"); fail("Expected duplicate rejection") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("already exists")) }
    }

    @Test fun invalidNamesAreRejected() = withRepository { repo, _ ->
        listOf("", "  ", "../escape", "bad/name", "bad\\name", "x".repeat(65)).forEach { name ->
            try { repo.createProject(name); fail("Expected invalid name rejection: $name") }
            catch (_: IllegalArgumentException) { }
        }
    }
}