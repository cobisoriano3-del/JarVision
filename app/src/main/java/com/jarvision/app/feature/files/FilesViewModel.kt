package com.jarvision.app.feature.files

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvision.app.data.storage.SafStorageGateway
import com.jarvision.app.data.storage.StorageEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FolderLocation(
    val documentId: String,
    val displayName: String
)

data class FilesUiState(
    val treeUriString: String? = null,
    val currentPath: List<FolderLocation> = emptyList(),
    val entries: List<StorageEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, 0)
    private val storageGateway = SafStorageGateway(application.contentResolver)

    private val mutableUiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = mutableUiState.asStateFlow()

    private var operationJob: Job? = null

    init {
        preferences.getString(KEY_TREE_URI, null)?.let(::restoreTree)
    }

    fun selectTree(treeUri: Uri) {
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            mutableUiState.value = FilesUiState(
                treeUriString = treeUri.toString(),
                isLoading = true
            )
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                preferences.edit().putString(KEY_TREE_URI, treeUri.toString()).apply()
                openTreeAndLoadRoot(treeUri.toString(), requirePersistedGrant = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.value = FilesUiState(
                    treeUriString = treeUri.toString(),
                    errorMessage = userMessage(error)
                )
            }
        }
    }

    fun navigateInto(entry: StorageEntry) {
        if (!entry.isDirectory) return
        val state = mutableUiState.value
        val treeUri = state.treeUriString ?: return
        val newLocation = FolderLocation(entry.documentId, entry.displayName)

        operationJob?.cancel()
        mutableUiState.value = state.copy(
            currentPath = state.currentPath + newLocation,
            entries = emptyList(),
            isLoading = true,
            errorMessage = null
        )
        operationJob = viewModelScope.launch {
            loadChildren(treeUri, newLocation)
        }
    }

    fun navigateBack() {
        val state = mutableUiState.value
        val treeUri = state.treeUriString ?: return
        if (state.currentPath.size <= 1) return

        val parent = state.currentPath[state.currentPath.lastIndex - 1]
        operationJob?.cancel()
        mutableUiState.value = state.copy(
            currentPath = state.currentPath.dropLast(1),
            entries = emptyList(),
            isLoading = true,
            errorMessage = null
        )
        operationJob = viewModelScope.launch {
            loadChildren(treeUri, parent)
        }
    }

    fun refresh() {
        val state = mutableUiState.value
        val treeUri = state.treeUriString ?: return
        val location = state.currentPath.lastOrNull() ?: return

        operationJob?.cancel()
        mutableUiState.value = state.copy(
            entries = emptyList(),
            isLoading = true,
            errorMessage = null
        )
        operationJob = viewModelScope.launch {
            loadChildren(treeUri, location)
        }
    }

    private fun restoreTree(treeUriString: String) {
        mutableUiState.value = FilesUiState(treeUriString = treeUriString, isLoading = true)
        operationJob = viewModelScope.launch {
            openTreeAndLoadRoot(treeUriString, requirePersistedGrant = true)
        }
    }

    private suspend fun openTreeAndLoadRoot(
        treeUriString: String,
        requirePersistedGrant: Boolean
    ) {
        try {
            val treeUri = Uri.parse(treeUriString)
            val resolver = getApplication<Application>().contentResolver
            if (
                requirePersistedGrant &&
                resolver.persistedUriPermissions.none {
                    it.uri == treeUri && it.isReadPermission
                }
            ) {
                throw SecurityException("The saved folder permission is no longer available.")
            }

            val rootDocumentId = withContext(Dispatchers.IO) {
                DocumentsContract.getTreeDocumentId(treeUri)
            }
            val root = withContext(Dispatchers.IO) {
                storageGateway.getDocument(treeUri, rootDocumentId)
            } ?: throw IllegalStateException("The selected folder is no longer available.")

            val rootLocation = FolderLocation(root.documentId, root.displayName)
            mutableUiState.value = FilesUiState(
                treeUriString = treeUriString,
                currentPath = listOf(rootLocation),
                isLoading = true
            )
            loadChildren(treeUriString, rootLocation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableUiState.value = FilesUiState(
                treeUriString = treeUriString,
                errorMessage = userMessage(error)
            )
        }
    }

    private suspend fun loadChildren(
        treeUriString: String,
        location: FolderLocation
    ) {
        try {
            val treeUri = Uri.parse(treeUriString)
            val children = withContext(Dispatchers.IO) {
                storageGateway.listChildren(treeUri, location.documentId)
            }
            mutableUiState.value = mutableUiState.value.copy(
                entries = children,
                isLoading = false,
                errorMessage = null
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableUiState.value = mutableUiState.value.copy(
                entries = emptyList(),
                isLoading = false,
                errorMessage = userMessage(error)
            )
        }
    }

    private fun userMessage(error: Exception): String = when (error) {
        is SecurityException ->
            "Folder access is unavailable. Choose the folder again to restore permission."
        else ->
            "Couldn't read this location. It may have moved, or its storage provider may be unavailable."
    }

    private companion object {
        const val PREFERENCES_NAME = "jarvision_storage"
        const val KEY_TREE_URI = "selected_tree_uri"
    }
}
