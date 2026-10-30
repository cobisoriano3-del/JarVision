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
        val registeredIds = properties.stringPropertyNames()
            .asSequence()
            .filter { it.endsWith(".name") }
            .map { it.removeSuffix(".name") }
            .distinct()
            .toSet()

        // Recover if the process stopped after renaming the project directory but before the
        // registry transaction committed. The deterministic tombstone preserves the old data.
        registeredIds.forEach { id ->
            val recordedPath = properties.getProperty("${id}.path") ?: return@forEach
            val recordedDirectory = File(recordedPath).canonicalFile
            if (recordedDirectory.parentFile != root.canonicalFile) {
                throw IOException("Project ${id} points outside JarVision's projects directory.")
            }
            val tombstone = File(root, ".deleted-${id}")
            if (!recordedDirectory.exists() && tombstone.exists()) {
                try {
                    Files.move(tombstone.toPath(), recordedDirectory.toPath())
                } catch (error: Exception) {
                    throw IOException("Project recovery is incomplete. Its workspace remains in ${tombstone.name}.", error)
                }
            }
        }

        // A tombstone without a registry record means deletion committed before shutdown.
        root.listFiles()?.filter { it.name.startsWith(".deleted-") }?.forEach { tombstone ->
            val id = tombstone.name.removePrefix(".deleted-")
            if (id !in registeredIds && !tombstone.deleteRecursively()) {
                throw IOException("Could not finish cleanup of deleted project data: ${tombstone.name}.")
            }
        }

        return registeredIds.asSequence()
            .mapNotNull { id ->
                val name = properties.getProperty("${id}.name") ?: return@mapNotNull null
                val path = properties.getProperty("${id}.path") ?: return@mapNotNull null
                val createdAt = properties.getProperty("${id}.created")?.toLongOrNull() ?: 0L
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
    fun findProject(projectId: String): Project? =
        listProjects().firstOrNull { it.id == projectId }

    /**
     * Renames the project directory to a tombstone before changing metadata. If the registry
     * update fails, the original workspace is moved back and remains usable.
     */
    @Synchronized
    @Throws(IOException::class)
    fun deleteProject(projectId: String): Boolean {
        val project = listProjects().firstOrNull { it.id == projectId }
            ?: throw IOException("This project no longer exists.")
        val projectDirectory = resolveProjectDirectory(projectId)
        val tombstone = File(root, ".deleted-${projectId}")
        if (tombstone.exists()) {
            throw IOException("This project already has pending deletion recovery data.")
        }
        try {
            try {
                Files.move(projectDirectory.toPath(), tombstone.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(projectDirectory.toPath(), tombstone.toPath())
            }
        } catch (error: Exception) {
            throw IOException("Could not stage this project for deletion.", error)
        }

        val temporary = File(root, "projects.properties.delete.tmp")
        try {
            val properties = Properties()
            if (registry.exists()) registry.inputStream().use(properties::load)
            properties.remove("${projectId}.name")
            properties.remove("${projectId}.path")
            properties.remove("${projectId}.created")
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
                Files.move(
                    temporary.toPath(),
                    registry.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (error: Exception) {
            temporary.delete()
            try {
                Files.move(tombstone.toPath(), projectDirectory.toPath())
            } catch (restoreError: Exception) {
                val recoveryWrite = runCatching {
                    val recoveryProperties = Properties()
                    if (registry.exists()) registry.inputStream().use(recoveryProperties::load)
                    recoveryProperties.setProperty("${projectId}.name", project.name)
                    recoveryProperties.setProperty("${projectId}.path", tombstone.absolutePath)
                    recoveryProperties.setProperty("${projectId}.created", project.createdAtMillis.toString())
                    val recoveryTemporary = File(root, "projects.properties.recovery.tmp")
                    FileOutputStream(recoveryTemporary).use { output ->
                        recoveryProperties.store(output, "JarVision project recovery registry")
                        output.fd.sync()
                    }
                    try {
                        Files.move(
                            recoveryTemporary.toPath(),
                            registry.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(
                            recoveryTemporary.toPath(),
                            registry.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                }
                if (recoveryWrite.isSuccess) {
                    throw IOException(
                        "Project deletion was cancelled. Its workspace was preserved at a recovery path and remains registered; refresh the Projects tab.",
                        restoreError
                    )
                }
                throw IOException(
                    "Project deletion could not finish. Recovery data remains at ${tombstone.name}, but both workspace restoration and registry repair failed.",
                    recoveryWrite.exceptionOrNull() ?: restoreError
                )
            }
            throw IOException("Could not update the project registry; the original project was restored.", error)
        }

        // False means registry removal committed but the hidden tombstone needs cleanup.
        return tombstone.deleteRecursively()
    }

    @Synchronized
    @Throws(IOException::class)
    fun stageWorkspaceEntry(projectId: String, relativePath: String, destination: File) {
        if (destination.exists()) throw IOException("The transfer staging target already exists.")
        val projectRoot = resolveProjectDirectory(projectId)
        rejectWorkspaceSymbolicLinks(projectRoot, relativePath)
        val source = resolveWorkspacePath(projectRoot, relativePath, allowRoot = false)
        if (!source.exists()) throw IOException("This workspace item no longer exists.")
        copySafeWorkspaceTree(source, destination)
    }

    @Synchronized
    @Throws(IOException::class)
    fun importStagedEntry(
        projectId: String,
        parentRelativePath: String,
        stagedSource: File,
        targetName: String
    ) {
        val projectRoot = resolveProjectDirectory(projectId)
        rejectWorkspaceSymbolicLinks(projectRoot, parentRelativePath)
        val parent = resolveWorkspacePath(projectRoot, parentRelativePath, allowRoot = true)
        if (!parent.isDirectory) throw IOException("The destination workspace folder no longer exists.")
        val name = validateWorkspaceName(targetName)
        val target = File(parent, name)
        if (target.exists() || Files.isSymbolicLink(target.toPath())) {
            throw IOException("An item with that name already exists.")
        }
        try {
            copySafeWorkspaceTree(stagedSource, target)
        } catch (error: Exception) {
            if (target.exists() && !target.deleteRecursively()) {
                throw IOException("The transfer failed and its partial destination could not be removed: ${target.name}.", error)
            }
            throw error
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun deleteWorkspaceEntry(projectId: String, relativePath: String) {
        val projectRoot = resolveProjectDirectory(projectId)
        rejectWorkspaceSymbolicLinks(projectRoot, relativePath)
        val target = resolveWorkspacePath(projectRoot, relativePath, allowRoot = false)
        if (!target.exists()) throw IOException("This workspace item no longer exists.")
        if (!target.deleteRecursively()) throw IOException("Could not completely delete ${target.name}.")
    }

    @Synchronized
    @Throws(IOException::class)
    fun workspaceEntryExists(projectId: String, parentRelativePath: String, name: String): Boolean {
        val projectRoot = resolveProjectDirectory(projectId)
        rejectWorkspaceSymbolicLinks(projectRoot, parentRelativePath)
        val parent = resolveWorkspacePath(projectRoot, parentRelativePath, allowRoot = true)
        if (!parent.isDirectory) throw IOException("The destination workspace folder no longer exists.")
        return parent.listFiles()?.any { it.name.equals(name, ignoreCase = true) }
            ?: throw IOException("Could not inspect the destination workspace folder.")
    }

    @Synchronized
    @Throws(IOException::class)
    fun stageProject(projectId: String, destination: File): Boolean {
        if (destination.exists()) throw IOException("The project staging target already exists.")
        val projectRoot = resolveProjectDirectory(projectId)
        if (!destination.mkdir()) throw IOException("Could not create the project staging folder.")
        try {
            val children = projectRoot.listFiles() ?: throw IOException("Could not read the project workspace.")
            children.forEach { child ->
                if (Files.isSymbolicLink(child.toPath())) {
                    throw IOException("A symbolic link was found in the project; publishing was stopped.")
                }
                copySafeWorkspaceTree(child, File(destination, child.name))
            }
            return true
        } catch (error: Exception) {
            if (!destination.deleteRecursively()) {
                throw IOException("Project staging failed and temporary cleanup was incomplete.", error)
            }
            throw error
        }
    }

    private fun rejectWorkspaceSymbolicLinks(projectRoot: File, relativePath: String) {
        if (relativePath.isEmpty()) return
        require(!relativePath.startsWith('/') && !relativePath.contains('\\')) {
            "Workspace paths must be relative."
        }
        val rootPath = projectRoot.absoluteFile.toPath().normalize()
        var current = rootPath
        relativePath.split('/').forEach { segment ->
            if (segment.isBlank() || segment == "." || segment == "..") {
                throw IOException("The workspace path is invalid.")
            }
            current = current.resolve(segment)
            if (Files.isSymbolicLink(current)) {
                throw IOException("Symbolic links are not allowed in workspace transfers.")
            }
        }
    }

    private fun copySafeWorkspaceTree(source: File, destination: File) {
        if (Files.isSymbolicLink(source.toPath())) {
            throw IOException("Symbolic links are not allowed in workspace transfers.")
        }
        val canonicalSource = source.canonicalFile
        if (!canonicalSource.exists()) throw IOException("The source item no longer exists.")
        if (destination.exists() || Files.isSymbolicLink(destination.toPath())) {
            throw IOException("An item with that name already exists.")
        }
        if (canonicalSource.isDirectory) {
            if (!destination.mkdir()) throw IOException("Could not create the destination folder.")
            try {
                val children = canonicalSource.listFiles()
                    ?: throw IOException("Could not read a source workspace folder.")
                children.forEach { child ->
                    val canonicalChild = child.canonicalFile
                    if (canonicalChild.parentFile != canonicalSource ||
                        Files.isSymbolicLink(child.toPath())
                    ) throw IOException("A workspace folder contains an unsafe link.")
                    copySafeWorkspaceTree(child, File(destination, child.name))
                }
            } catch (error: Exception) {
                if (!destination.deleteRecursively()) {
                    throw IOException("The copy failed and its partial folder could not be removed: ${destination.name}.", error)
                }
                throw error
            }
        } else {
            destination.parentFile?.let {
                if (!it.isDirectory) throw IOException("The destination parent folder is unavailable.")
            }
            try {
                source.inputStream().buffered().use { input ->
                    destination.outputStream().buffered().use { output -> input.copyTo(output) }
                }
            } catch (error: Exception) {
                destination.delete()
                throw error
            }
        }
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
