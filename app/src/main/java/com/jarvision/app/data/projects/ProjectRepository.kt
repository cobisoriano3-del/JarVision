package com.jarvision.app.data.projects

import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.Properties

data class Project(val id: String, val name: String, val directoryPath: String, val createdAtMillis: Long)

class ProjectRepository(private val appFilesDir: File) {
    private val root = File(appFilesDir, "projects")
    private val registry = File(root, "projects.properties")

    @Synchronized @Throws(IOException::class)
    fun listProjects(): List<Project> {
        if (!registry.exists()) return emptyList()
        val p = Properties(); registry.inputStream().use(p::load)
        return p.stringPropertyNames().mapNotNull { id ->
            val name = p.getProperty("$id.name") ?: return@mapNotNull null
            val path = p.getProperty("$id.path") ?: return@mapNotNull null
            Project(id, name, path, p.getProperty("$id.created")?.toLongOrNull() ?: 0L)
        }.sortedByDescending { it.createdAtMillis }
    }

    @Synchronized @Throws(IOException::class)
    fun createProject(rawName: String): Project {
        val name = rawName.trim()
        require(name.isNotEmpty()) { "Enter a project name." }
        require(name.length <= 64) { "Project names must be 64 characters or fewer." }
        require(name.none { it == '/' || it == '\\' || it.code < 32 }) { "Use a name without path separators or control characters." }
        if (!root.exists() && !root.mkdirs()) throw IOException("Could not create the projects directory.")
        require(listProjects().none { it.name.equals(name, true) }) { "A project with that name already exists." }
        val id = UUID.randomUUID().toString()
        val dir = File(root, id)
        if (!dir.mkdir()) throw IOException("Could not create this project workspace.")
        val project = Project(id, name, dir.absolutePath, System.currentTimeMillis())
        val p = Properties()
        if (registry.exists()) registry.inputStream().use(p::load)
        p.setProperty("$id.name", name); p.setProperty("$id.path", dir.absolutePath); p.setProperty("$id.created", project.createdAtMillis.toString())
        val tmp = File(root, "projects.properties.tmp")
        try {
            tmp.outputStream().use { p.store(it, "JarVision project registry") }
            if (registry.exists() && !registry.delete()) throw IOException("Could not update project registry.")
            if (!tmp.renameTo(registry)) throw IOException("Could not save project metadata.")
        } catch (e: Exception) {
            tmp.delete(); dir.delete(); throw e
        }
        return project
    }
}