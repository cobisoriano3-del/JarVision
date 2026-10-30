package com.jarvision.app.feature.projects

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvision.app.data.projects.ProjectRepository
import com.jarvision.app.data.projects.ProjectWorkspaceEntry
import com.jarvision.app.data.storage.StorageEntry
import com.jarvision.app.data.storage.ShizukuStorageAdapter
import com.jarvision.app.feature.files.FileOperationCoordinator
import com.jarvision.app.feature.files.StorageAccessMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import java.util.UUID

sealed interface WorkspaceTransferSource {
    val name: String
    val isDirectory: Boolean
    val sourceDeletePending: Boolean

    data class ProjectItem(
        val projectId: String,
        val relativePath: String,
        override val name: String,
        override val isDirectory: Boolean,
        override val sourceDeletePending: Boolean = false
    ) : WorkspaceTransferSource

    data class StorageItem(
        val mode: StorageAccessMode,
        val treeUriString: String?,
        val entry: StorageEntry,
        val parentDocumentId: String,
        val parentAbsolutePath: String?,
        override val sourceDeletePending: Boolean = false
    ) : WorkspaceTransferSource {
        override val name: String get() = entry.displayName
        override val isDirectory: Boolean get() = entry.isDirectory
    }
}

data class WorkspaceTransferClipboard(
    val id: String,
    val sources: List<WorkspaceTransferSource>,
    val isCut: Boolean
)

data class WorkspaceTransferUiState(
    val clipboard: WorkspaceTransferClipboard? = null,
    val isWorking: Boolean = false,
    val message: String? = null,
    val operationVersion: Int = 0
)

class WorkspaceTransferViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ProjectRepository(app.filesDir)
    private val fileOperations = FileOperationCoordinator(app, ShizukuStorageAdapter.get(app))
    private val _state = MutableStateFlow(WorkspaceTransferUiState())
    val state = _state.asStateFlow()

    fun stageWorkspaceEntries(projectId: String, entries: List<ProjectWorkspaceEntry>, cut: Boolean): String {
        val id = UUID.randomUUID().toString()
        val sources = entries.distinctBy { it.relativePath }.map {
            WorkspaceTransferSource.ProjectItem(projectId, it.relativePath, it.name, it.isDirectory)
        }
        _state.update {
            it.copy(
                clipboard = WorkspaceTransferClipboard(id, sources, cut),
                message = if (cut) "${sources.size} workspace item(s) marked for moving."
                else "${sources.size} workspace item(s) copied."
            )
        }
        return id
    }

    fun stageStorageEntries(
        entries: List<StorageEntry>,
        mode: StorageAccessMode,
        treeUriString: String?,
        parentDocumentId: String,
        parentAbsolutePath: String?,
        cut: Boolean
    ): String {
        val id = UUID.randomUUID().toString()
        val sources = entries.distinctBy { it.documentId }.map {
            WorkspaceTransferSource.StorageItem(mode, treeUriString, it, parentDocumentId, parentAbsolutePath)
        }
        _state.update {
            it.copy(
                clipboard = WorkspaceTransferClipboard(id, sources, cut),
                message = if (cut) "${sources.size} storage item(s) marked for moving."
                else "${sources.size} storage item(s) copied."
            )
        }
        return id
    }

    fun clearClipboard() {
        _state.update { it.copy(clipboard = null) }
    }

    fun completeLegacyStoragePaste(
        clipboardId: String,
        failedDocumentIds: Set<String>,
        sourceDeletePendingIds: Set<String>
    ) {
        _state.update { current ->
            val clipboard = current.clipboard
            if (clipboard == null || clipboard.id != clipboardId) return@update current
            val remaining = clipboard.sources.mapNotNull { source ->
                val storage = source as? WorkspaceTransferSource.StorageItem ?: return@mapNotNull source
                when {
                    storage.entry.documentId in sourceDeletePendingIds -> storage.copy(sourceDeletePending = true)
                    storage.entry.documentId in failedDocumentIds -> storage
                    else -> null
                }
            }
            current.copy(clipboard = clipboard.copy(sources = remaining).takeIf { remaining.isNotEmpty() })
        }
    }

    fun pasteToWorkspace(projectId: String, destinationRelativePath: String) {
        runPaste(Destination.ProjectFolder(projectId, destinationRelativePath))
    }

    fun pasteToStorage(
        mode: StorageAccessMode,
        treeUriString: String?,
        parentDocumentId: String,
        parentAbsolutePath: String?
    ) {
        runPaste(Destination.StorageFolder(mode, treeUriString, parentDocumentId, parentAbsolutePath))
    }

    fun publishProjectToSaf(projectId: String, treeUriString: String) {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, message = null) }
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    val project = repository.findProject(projectId)
                        ?: throw IOException("This project no longer exists.")
                    val staging = File(
                        getApplication<Application>().cacheDir,
                        "jarvision-publish-${UUID.randomUUID()}"
                    )
                    repository.stageProject(projectId, staging)
                    try {
                        val treeUri = Uri.parse(treeUriString)
                        val parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                        if (fileOperations.findNamedChild(
                                StorageAccessMode.DOCUMENT_TREE,
                                treeUriString,
                                parentDocumentId,
                                null,
                                project.name
                            ) != null
                        ) {
                            throw IOException("A folder named ${project.name} already exists in the selected destination.")
                        }
                        fileOperations.publishStagedEntry(
                            StorageAccessMode.DOCUMENT_TREE,
                            treeUriString,
                            parentDocumentId,
                            null,
                            staging,
                            project.name
                        )
                        "Published ${project.name} as a copy. The private workspace was kept unchanged."
                    } finally {
                        if (staging.exists() && !staging.deleteRecursively()) {
                            throw IOException("The project was published, but temporary cleanup failed.")
                        }
                    }
                }
                _state.update { it.copy(message = message, operationVersion = it.operationVersion + 1) }
            } catch (error: Exception) {
                _state.update { it.copy(message = error.message ?: "Could not publish this project.") }
            } finally {
                _state.update { it.copy(isWorking = false) }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private sealed interface Destination {
        data class ProjectFolder(val projectId: String, val relativePath: String) : Destination
        data class StorageFolder(
            val mode: StorageAccessMode,
            val treeUriString: String?,
            val parentDocumentId: String,
            val parentAbsolutePath: String?
        ) : Destination
    }

    private data class PasteSummary(
        val completed: Int,
        val failed: Int,
        val warnings: List<String>,
        val remaining: List<WorkspaceTransferSource>
    )

    private fun runPaste(destination: Destination) {
        val snapshot = _state.value.clipboard
        if (snapshot == null || snapshot.sources.isEmpty()) {
            _state.update { it.copy(message = "The clipboard is empty.") }
            return
        }
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, message = null) }
        viewModelScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) { transfer(snapshot, destination) }
                _state.update { current ->
                    if (current.clipboard?.id != snapshot.id) {
                        current.copy(message = "The clipboard changed while Paste was running. The new clipboard was left untouched.")
                    } else {
                        current.copy(
                            clipboard = snapshot.copy(sources = summary.remaining)
                                .takeIf { summary.remaining.isNotEmpty() },
                            message = buildString {
                                append("${summary.completed} item(s) pasted.")
                                if (summary.failed > 0) append(" ${summary.failed} item(s) failed and remain on the clipboard.")
                                summary.warnings.firstOrNull()?.let { append(" ").append(it) }
                            },
                            operationVersion = current.operationVersion + 1
                        )
                    }
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        message = error.message ?: "Paste failed.",
                        operationVersion = it.operationVersion + 1
                    )
                }
            } finally {
                _state.update { it.copy(isWorking = false) }
            }
        }
    }

    private fun transfer(clipboard: WorkspaceTransferClipboard, destination: Destination): PasteSummary {
        var completed = 0
        var failed = 0
        val warnings = mutableListOf<String>()
        val remaining = mutableListOf<WorkspaceTransferSource>()

        clipboard.sources.forEach { source ->
            if (source.sourceDeletePending) {
                try {
                    deleteSource(source)
                    completed++
                } catch (error: Exception) {
                    failed++
                    remaining += source
                    warnings += "Could not remove the original ${source.name}: ${error.message ?: "access denied"}"
                }
                return@forEach
            }

            if (clipboard.isCut && isSameParent(source, destination)) {
                completed++
                return@forEach
            }

            try {
                if (isDestinationInsideSource(source, destination)) {
                    throw IOException("A folder cannot be pasted into itself or one of its subfolders.")
                }
                val staging = File(
                    getApplication<Application>().cacheDir,
                    "jarvision-transfer-${UUID.randomUUID()}"
                )
                var stagingCleanupFailed = false
                try {
                    stageSource(source, staging)
                    val targetName = uniqueDestinationName(source.name, destination)
                    publishStagedSource(staging, targetName, destination)
                } finally {
                    if (staging.exists()) stagingCleanupFailed = !staging.deleteRecursively()
                }
                if (stagingCleanupFailed) {
                    warnings += "The destination copy succeeded, but temporary staging data could not be cleaned up."
                }

                if (clipboard.isCut) {
                    try {
                        deleteSource(source)
                    } catch (error: Exception) {
                        failed++
                        remaining += source.withDeletePending()
                        warnings += "Copied ${source.name}, but its original could not be deleted. Both copies remain; Paste will retry deletion without copying again."
                        return@forEach
                    }
                }
                completed++
            } catch (error: Exception) {
                failed++
                remaining += source
                warnings += "${source.name}: ${error.message ?: "transfer failed"}"
            }
        }
        return PasteSummary(completed, failed, warnings, remaining)
    }

    private fun WorkspaceTransferSource.withDeletePending(): WorkspaceTransferSource =
        when (this) {
            is WorkspaceTransferSource.ProjectItem -> copy(sourceDeletePending = true)
            is WorkspaceTransferSource.StorageItem -> copy(sourceDeletePending = true)
        }

    private fun stageSource(source: WorkspaceTransferSource, staging: File) {
        when (source) {
            is WorkspaceTransferSource.ProjectItem ->
                repository.stageWorkspaceEntry(source.projectId, source.relativePath, staging)
            is WorkspaceTransferSource.StorageItem ->
                fileOperations.stageStorageEntry(source.mode, source.treeUriString, source.entry, staging)
        }
    }

    private fun publishStagedSource(staging: File, name: String, destination: Destination) {
        when (destination) {
            is Destination.ProjectFolder ->
                repository.importStagedEntry(destination.projectId, destination.relativePath, staging, name)
            is Destination.StorageFolder ->
                fileOperations.publishStagedEntry(
                    destination.mode,
                    destination.treeUriString,
                    destination.parentDocumentId,
                    destination.parentAbsolutePath,
                    staging,
                    name
                )
        }
    }

    private fun deleteSource(source: WorkspaceTransferSource) {
        when (source) {
            is WorkspaceTransferSource.ProjectItem ->
                repository.deleteWorkspaceEntry(source.projectId, source.relativePath)
            is WorkspaceTransferSource.StorageItem ->
                fileOperations.delete(source.mode, source.treeUriString, source.entry)
        }
    }

    private fun isSameParent(source: WorkspaceTransferSource, destination: Destination): Boolean =
        when {
            source is WorkspaceTransferSource.ProjectItem && destination is Destination.ProjectFolder ->
                source.projectId == destination.projectId &&
                    source.relativePath.substringBeforeLast('/', "") == destination.relativePath
            source is WorkspaceTransferSource.StorageItem && destination is Destination.StorageFolder -> {
                val sameSharedStorage = isSharedBackend(source.mode) && isSharedBackend(destination.mode)
                if (sameSharedStorage) {
                    val sourceParentPath = source.parentAbsolutePath ?: source.parentDocumentId
                    val destinationParentPath = destination.parentAbsolutePath ?: destination.parentDocumentId
                    sourceParentPath.isNotBlank() &&
                        sourceParentPath.trimEnd('/') == destinationParentPath.trimEnd('/')
                } else {
                    source.mode == destination.mode &&
                        source.treeUriString == destination.treeUriString &&
                        source.parentDocumentId == destination.parentDocumentId
                }
            }
            else -> false
        }

    private fun isSharedBackend(mode: StorageAccessMode): Boolean =
        mode == StorageAccessMode.SHARED_STORAGE || mode == StorageAccessMode.SHIZUKU_STORAGE

    private fun isDestinationInsideSource(
        source: WorkspaceTransferSource,
        destination: Destination
    ): Boolean {
        if (!source.isDirectory) return false
        return when {
            source is WorkspaceTransferSource.ProjectItem && destination is Destination.ProjectFolder ->
                source.projectId == destination.projectId &&
                    (destination.relativePath == source.relativePath ||
                        destination.relativePath.startsWith(source.relativePath.trimEnd('/') + "/"))
            source is WorkspaceTransferSource.StorageItem && destination is Destination.StorageFolder -> {
                val sameSharedStorage = isSharedBackend(source.mode) && isSharedBackend(destination.mode)
                val sameSafTree = source.mode == StorageAccessMode.DOCUMENT_TREE &&
                    destination.mode == StorageAccessMode.DOCUMENT_TREE &&
                    source.treeUriString == destination.treeUriString
                if (!sameSharedStorage && !sameSafTree) {
                    false
                } else {
                    val sourcePath = (source.entry.absolutePath ?: source.entry.documentId).trimEnd('/')
                    val destinationPath = (destination.parentAbsolutePath ?: destination.parentDocumentId).trimEnd('/')
                    val sourceId = source.entry.documentId.trimEnd('/')
                    val destinationId = destination.parentDocumentId.trimEnd('/')
                    (sameSharedStorage && sourcePath.isNotEmpty() &&
                        (destinationPath == sourcePath || destinationPath.startsWith(sourcePath + "/"))) ||
                        (sameSafTree && (destinationId == sourceId || destinationId.startsWith(sourceId + "/")))
                }
            }
            else -> false
        }
    }

    private fun uniqueDestinationName(original: String, destination: Destination): String {
        if (!destinationExists(original, destination)) return original
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        val extension = if (dot > 0) original.substring(dot) else ""
        for (suffix in 1..10_000) {
            val candidate = "$base (${suffix})$extension"
            if (!destinationExists(candidate, destination)) return candidate
        }
        throw IOException("Could not find an unused destination name for $original.")
    }

    private fun destinationExists(name: String, destination: Destination): Boolean =
        when (destination) {
            is Destination.ProjectFolder -> repository.workspaceEntryExists(destination.projectId, destination.relativePath, name)
            is Destination.StorageFolder -> fileOperations.findNamedChild(
                destination.mode,
                destination.treeUriString,
                destination.parentDocumentId,
                destination.parentAbsolutePath,
                name
            ) != null
        }
}
