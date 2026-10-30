package com.jarvision.app.feature.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvision.app.data.projects.Project
import com.jarvision.app.data.projects.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ProjectRepository(app.filesDir)
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects = _projects.asStateFlow()
    private val _isCreating = MutableStateFlow(false)
    val isCreating = _isCreating.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { repository.listProjects() } }
            .onSuccess { _projects.value = it; _errorMessage.value = null }
            .onFailure { _errorMessage.value = it.message ?: "Could not load projects." }
    }


    fun deleteProject(projectId: String) {
        if (_isCreating.value) return
        _isCreating.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val directoryRemoved = withContext(Dispatchers.IO) {
                    repository.deleteProject(projectId)
                }
                _projects.value = _projects.value.filterNot { it.id == projectId }
                if (!directoryRemoved) {
                    _errorMessage.value = "The project was removed from the list, but hidden recovery data could not be cleaned up."
                }
            } catch (error: Exception) {
                _errorMessage.value = error.message ?: "Could not delete this project."
                runCatching {
                    _projects.value = withContext(Dispatchers.IO) { repository.listProjects() }
                }.onFailure {
                    _errorMessage.value = (_errorMessage.value.orEmpty() + " " +
                        (it.message ?: "Project recovery refresh failed.")).trim()
                }
            } finally {
                _isCreating.value = false
            }
        }
    }

    fun createProject(name: String) {
        if (_isCreating.value) return
        _isCreating.value = true; _errorMessage.value = null
        viewModelScope.launch {
            try {
                val project = withContext(Dispatchers.IO) { repository.createProject(name) }
                _projects.value = (listOf(project) + _projects.value).distinctBy { it.id }
            } catch (e: Exception) { _errorMessage.value = e.message ?: "Could not create project." }
            finally { _isCreating.value = false }
        }
    }
}