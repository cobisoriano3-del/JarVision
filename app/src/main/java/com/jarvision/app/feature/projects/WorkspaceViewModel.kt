package com.jarvision.app.feature.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvision.app.data.projects.ProjectRepository
import com.jarvision.app.data.projects.ProjectWorkspaceEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorkspaceUiState(
    val projectId: String? = null,
    val relativeDirectory: String = "",
    val entries: List<ProjectWorkspaceEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isWorking: Boolean = false,
    val errorMessage: String? = null
)

class WorkspaceViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ProjectRepository(app.filesDir)
    private val _state = MutableStateFlow(WorkspaceUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    fun openProject(projectId: String) {
        loadEntries(projectId, "")
    }

    fun openDirectory(relativePath: String) {
        val projectId = _state.value.projectId ?: return
        loadEntries(projectId, relativePath)
    }

    fun navigateUp() {
        val current = _state.value
        val projectId = current.projectId ?: return
        if (current.relativeDirectory.isEmpty()) return
        val parent = current.relativeDirectory.substringBeforeLast('/', missingDelimiterValue = "")
        loadEntries(projectId, parent)
    }

    fun refresh() {
        val current = _state.value
        val projectId = current.projectId ?: return
        loadEntries(projectId, current.relativeDirectory)
    }

    fun createFolder(name: String) {
        mutate { projectId, directory ->
            repository.createWorkspaceFolder(projectId, directory, name)
        }
    }

    fun createTextFile(name: String) {
        mutate { projectId, directory ->
            repository.createWorkspaceTextFile(projectId, directory, name)
        }
    }

    fun renameEntry(entry: ProjectWorkspaceEntry, newName: String) {
        mutate { projectId, _ ->
            repository.renameWorkspaceEntry(projectId, entry.relativePath, newName)
        }
    }

    suspend fun readTextFile(projectId: String, relativePath: String): String =
        withContext(Dispatchers.IO) {
            repository.readWorkspaceTextFile(projectId, relativePath)
        }

    suspend fun saveTextFile(projectId: String, relativePath: String, text: String) {
        withContext(Dispatchers.IO) {
            repository.writeWorkspaceTextFile(projectId, relativePath, text)
        }
        val current = _state.value
        if (current.projectId == projectId) {
            loadEntries(projectId, current.relativeDirectory)
        }
    }

    private fun loadEntries(projectId: String, relativeDirectory: String) {
        loadJob?.cancel()
        _state.update {
            it.copy(
                projectId = projectId,
                relativeDirectory = relativeDirectory,
                isLoading = true,
                errorMessage = null
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) {
                    repository.listWorkspaceEntries(projectId, relativeDirectory)
                }
                _state.update { current ->
                    if (current.projectId == projectId &&
                        current.relativeDirectory == relativeDirectory
                    ) {
                        current.copy(entries = entries, isLoading = false, errorMessage = null)
                    } else {
                        current
                    }
                }
            } catch (error: Exception) {
                _state.update { current ->
                    if (current.projectId == projectId &&
                        current.relativeDirectory == relativeDirectory
                    ) {
                        current.copy(
                            entries = emptyList(),
                            isLoading = false,
                            errorMessage = error.message ?: "Could not load this workspace folder."
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun mutate(operation: (String, String) -> Unit) {
        val start = _state.value
        val projectId = start.projectId ?: return
        val directory = start.relativeDirectory
        if (start.isWorking) return

        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, errorMessage = null) }
            try {
                val entries = withContext(Dispatchers.IO) {
                    operation(projectId, directory)
                    repository.listWorkspaceEntries(projectId, directory)
                }
                _state.update { current ->
                    if (current.projectId == projectId &&
                        current.relativeDirectory == directory
                    ) {
                        current.copy(entries = entries, errorMessage = null)
                    } else {
                        current
                    }
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(errorMessage = error.message ?: "The workspace operation failed.")
                }
            } finally {
                _state.update { it.copy(isWorking = false) }
            }
        }
    }
}
