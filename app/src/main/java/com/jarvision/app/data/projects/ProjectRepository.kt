package com.jarvision.app.data.projects

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

data class Project(
    val id: String,
    val name: String,
    val directoryPath: String,
    val createdAtMillis: Long
)

class ProjectRepository(private val appFilesDir: File) {
    private val root = File(appFilesDir, "projects")
    private val registry = File(root, "projects.properties")

    @Synchronized
    @Throws(IOException::class)
    fun listProjects(): List<Project> {
        if (!registry.exists()) return emptyList()
        val properties = Properties()
        registry.inputStream().use(properties::load)

        // The registry has three properties per project. Enumerate IDs from .name keys only;
        // treating every key as an ID made each metadata lookup fail and hid every project.
        return properties.stringPropertyNames()
            .asSequence()
            .filter { it.endsWith(".name") }
            .map { it.removeSuffix(".name") }
            .distinct()
            .mapNotNull { id ->
                val name = properties.getProperty("$id.name") ?: return@mapNotNull null
                val path = properties.getProperty("$id.path") ?: return@mapNotNull null
                val createdAt = properties.getProperty("$id.created")?.toLongOrNull() ?: 0L
                Project(id, name, path, createdAt)
            }
            .sortedByDescending { it.createdAtMillis }
            .toList()
    }

    @Synchronized
    @Throws(IOException::class)
    fun createProject(rawName: String): Project {
        val name = rawName.trim()
        require(name.isNotEmpty()) { "Enter a project name." }
        require(name.length <= 64) { "Project names must be 64 characters or fewer." }
        require(name.none { it == '/' || it == '\\' || it.code < 32 }) {
            "Use a name without path separators or control characters."
        }

        if (!root.exists() && !root.mkdirs()) {
            throw IOException("Could not create the projects directory.")
        }
        require(listProjects().none { it.name.equals(name, ignoreCase = true) }) {
            "A project with that name already exists."
        }

        val id = UUID.randomUUID().toString()
        val directory = File(root, id)
        if (!directory.mkdir()) throw IOException("Could not create this project workspace.")

        val project = Project(id, name, directory.absolutePath, System.currentTimeMillis())
        val properties = Properties()
        if (registry.exists()) registry.inputStream().use(properties::load)
        properties.setProperty("$id.name", name)
        properties.setProperty("$id.path", directory.absolutePath)
        properties.setProperty("$id.created", project.createdAtMillis.toString())

        val temporary = File(root, "projects.properties.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                properties.store(output, "JarVision project registry")
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    registry.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                // Same-directory replacement preserves the previous registry if the move fails.
                Files.move(
                    temporary.toPath(),
                    registry.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (error: Exception) {
            temporary.delete()
            directory.deleteRecursively()
            throw error
        }
        return project
    }
}
