package com.jarvision.app.data.projects

import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.charset.StandardCharsets
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

data class ProjectWorkspaceEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val childCount: Int
)

class ProjectRepository(private val appFilesDir: File) {
    companion object {
        const val MAX_WORKSPACE_TEXT_BYTES = 1_048_576L
    }
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
    @Synchronized
    @Throws(IOException::class)
    fun listWorkspaceEntries(projectId: String, relativeDirectoryPath: String = ""): List<ProjectWorkspaceEntry> {
        val projectRoot = resolveProjectDirectory(projectId)
        val directory = resolveWorkspacePath(projectRoot, relativeDirectoryPath, allowRoot = true)
        if (!directory.isDirectory) throw IOException("This workspace folder no longer exists.")

        val children = directory.listFiles()
            ?: throw IOException("Could not read this workspace folder.")

        return children.mapNotNull { child ->
            if (Files.isSymbolicLink(child.toPath())) return@mapNotNull null
            val canonicalChild = child.canonicalFile
            if (canonicalChild.parentFile != directory.canonicalFile) return@mapNotNull null
            val isDirectory = canonicalChild.isDirectory
            ProjectWorkspaceEntry(
                name = canonicalChild.name,
                relativePath = projectRoot.toPath().relativize(canonicalChild.toPath())
                    .toString().replace(File.separatorChar, '/'),
                isDirectory = isDirectory,
                sizeBytes = if (isDirectory) 0L else canonicalChild.length(),
                childCount = if (isDirectory) canonicalChild.list()?.size ?: 0 else 0
            )
        }.sortedWith(
            compareBy<ProjectWorkspaceEntry> { if (it.isDirectory) 0 else 1 }
                .thenBy { it.name.lowercase(java.util.Locale.ROOT) }
        )
    }

    @Synchronized
    @Throws(IOException::class)
    fun createWorkspaceFolder(
        projectId: String,
        parentRelativePath: String,
        rawName: String
    ): ProjectWorkspaceEntry {
        val projectRoot = resolveProjectDirectory(projectId)
        val parent = resolveWorkspacePath(projectRoot, parentRelativePath, allowRoot = true)
        if (!parent.isDirectory) throw IOException("The destination folder no longer exists.")
        val name = validateWorkspaceName(rawName)
        val destination = File(parent, name)
        if (!destination.mkdir()) {
            if (destination.exists()) throw IOException("An item with that name already exists.")
            throw IOException("Could not create the folder.")
        }
        return workspaceEntry(projectRoot, destination)
    }

    @Synchronized
    @Throws(IOException::class)
    fun createWorkspaceTextFile(
        projectId: String,
        parentRelativePath: String,
        rawName: String
    ): ProjectWorkspaceEntry {
        val projectRoot = resolveProjectDirectory(projectId)
        val parent = resolveWorkspacePath(projectRoot, parentRelativePath, allowRoot = true)
        if (!parent.isDirectory) throw IOException("The destination folder no longer exists.")

        val enteredName = validateWorkspaceName(rawName)
        val name = if (enteredName.endsWith(".txt", ignoreCase = true)) enteredName else "$enteredName.txt"
        if (name.length > 255) throw IllegalArgumentException("File names must be 255 characters or fewer.")

        val destination = File(parent, name)
        if (!destination.createNewFile()) {
            if (destination.exists()) throw IOException("An item with that name already exists.")
            throw IOException("Could not create the text file.")
        }
        return workspaceEntry(projectRoot, destination)
    }

    @Synchronized
    @Throws(IOException::class)
    fun renameWorkspaceEntry(projectId: String, relativePath: String, rawNewName: String) {
        val projectRoot = resolveProjectDirectory(projectId)
        val source = resolveWorkspacePath(projectRoot, relativePath, allowRoot = false)
        if (!source.exists()) throw IOException("This workspace item no longer exists.")
        val enteredName = validateWorkspaceName(rawNewName)
        val newName = if (source.isFile && !enteredName.endsWith(".txt", ignoreCase = true)) {
            "$enteredName.txt"
        } else {
            enteredName
        }
        if (newName.length > 255) throw IllegalArgumentException("File names must be 255 characters or fewer.")
        if (source.name == newName) return

        val parent = source.parentFile ?: throw IOException("Could not resolve the item's parent folder.")
        val destination = File(parent, newName)
        if (destination.exists()) throw IOException("An item with that name already exists.")
        try {
            Files.move(source.toPath(), destination.toPath())
        } catch (error: Exception) {
            throw IOException("Could not rename this workspace item.", error)
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun readWorkspaceTextFile(projectId: String, relativePath: String): String {
        val projectRoot = resolveProjectDirectory(projectId)
        val file = resolveWorkspacePath(projectRoot, relativePath, allowRoot = false)
        if (!file.isFile || !file.name.endsWith(".txt", ignoreCase = true)) {
            throw IOException("Only workspace .txt files can be opened here.")
        }
        if (file.length() > MAX_WORKSPACE_TEXT_BYTES) {
            throw IOException("Text files larger than 1 MiB cannot be opened in this workspace editor.")
        }
        val initialCapacity = file.length().toInt().coerceAtMost(8 * 1024)
        val output = ByteArrayOutputStream(initialCapacity)
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            var totalBytes = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                totalBytes += count
                if (totalBytes > MAX_WORKSPACE_TEXT_BYTES) {
                    throw IOException("Text files larger than 1 MiB cannot be opened in this workspace editor.")
                }
                output.write(buffer, 0, count)
            }
        }
        return String(output.toByteArray(), StandardCharsets.UTF_8)
    }

    @Synchronized
    @Throws(IOException::class)
    fun writeWorkspaceTextFile(projectId: String, relativePath: String, text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size.toLong() > MAX_WORKSPACE_TEXT_BYTES) {
            throw IOException("Text files are limited to 1 MiB in this workspace editor.")
        }

        val projectRoot = resolveProjectDirectory(projectId)
        val file = resolveWorkspacePath(projectRoot, relativePath, allowRoot = false)
        if (!file.isFile || !file.name.endsWith(".txt", ignoreCase = true)) {
            throw IOException("Only workspace .txt files can be saved here.")
        }

        val parent = file.parentFile ?: throw IOException("Could not resolve the file's parent folder.")
        val temporary = File(parent, ".jarvision-${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            temporary.delete()
        }
    }

    private fun resolveProjectDirectory(projectId: String): File {
        val project = listProjects().firstOrNull { it.id == projectId }
            ?: throw IOException("This project no longer exists.")
        val expectedRoot = root.canonicalFile
        val projectDirectory = File(project.directoryPath).canonicalFile
        if (projectDirectory.parentFile != expectedRoot || !projectDirectory.isDirectory) {
            throw IOException("The project workspace is missing or outside JarVision's project folder.")
        }
        return projectDirectory
    }

    private fun resolveWorkspacePath(
        projectRoot: File,
        relativePath: String,
        allowRoot: Boolean
    ): File {
        if (relativePath.isEmpty()) return projectRoot
        require(!relativePath.startsWith('/') && !relativePath.contains('\\')) {
            "Workspace paths must be relative."
        }
        val segments = relativePath.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "The workspace path is invalid."
        }

        val canonicalRoot = projectRoot.canonicalFile
        val resolved = File(canonicalRoot, segments.joinToString(File.separator)).canonicalFile
        if (!resolved.toPath().startsWith(canonicalRoot.toPath())) {
            throw IOException("The workspace path points outside this project.")
        }
        if (!allowRoot && resolved == canonicalRoot) {
            throw IOException("The project root cannot be changed from inside the workspace.")
        }
        return resolved
    }

    private fun validateWorkspaceName(rawName: String): String {
        val name = rawName.trim()
        require(name.isNotEmpty()) { "Enter a name." }
        require(name.length <= 255) { "Names must be 255 characters or fewer." }
        require(name != "." && name != "..") { "Choose a valid name." }
        require(name.none { it == '/' || it == '\\' || it.code < 32 }) {
            "Names cannot contain path separators or control characters."
        }
        return name
    }

    private fun workspaceEntry(projectRoot: File, file: File): ProjectWorkspaceEntry {
        val canonical = file.canonicalFile
        return ProjectWorkspaceEntry(
            name = canonical.name,
            relativePath = projectRoot.canonicalFile.toPath().relativize(canonical.toPath())
                .toString().replace(File.separatorChar, '/'),
            isDirectory = canonical.isDirectory,
            sizeBytes = if (canonical.isDirectory) 0L else canonical.length(),
            childCount = if (canonical.isDirectory) canonical.list()?.size ?: 0 else 0
        )
    }

}
