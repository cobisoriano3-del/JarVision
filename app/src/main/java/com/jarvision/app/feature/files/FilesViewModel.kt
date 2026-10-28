package com.jarvision.app.feature.files

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvision.app.data.storage.SafStorageGateway
import com.jarvision.app.data.storage.SharedStorageGateway
import com.jarvision.app.data.storage.ShizukuStorageAdapter
import com.jarvision.app.data.storage.StorageEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

enum class StorageAccessMode {
    NONE,
    SHARED_STORAGE,
    DOCUMENT_TREE,
    SHIZUKU_STORAGE
}

data class FolderLocation(
    val documentId: String,
    val displayName: String,
    val absolutePath: String? = null
)

data class FilesUiState(
    val accessMode: StorageAccessMode = StorageAccessMode.NONE,
    val treeUriString: String? = null,
    val currentPath: List<FolderLocation> = emptyList(),
    val entries: List<StorageEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, 0)
    private val safGateway = SafStorageGateway(application.contentResolver)
    private val sharedStorageGateway = SharedStorageGateway()
    private val shizukuStorageAdapter = ShizukuStorageAdapter.get(application)
    private val entryVisualLoader = EntryVisualLoader(application)
    private val entryVisualLoadSemaphore = Semaphore(3)

    private val mutableUiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = mutableUiState.asStateFlow()

    private var operationJob: Job? = null

    init {
        refreshAccess()
    }

    /**
     * Re-check this when returning from system Settings or a runtime permission prompt.
     * Broad access is preferred on first launch; an existing folder-only session is preserved.
     */
    fun refreshAccess() {
        val current = mutableUiState.value
        if (
            current.accessMode == StorageAccessMode.SHIZUKU_STORAGE &&
            shizukuStorageAdapter.status.value.isConnected &&
            current.currentPath.isNotEmpty()
        ) return

        if (hasSharedStorageAccess()) {
            when {
                current.accessMode == StorageAccessMode.SHARED_STORAGE &&
                    current.currentPath.isNotEmpty() -> refresh()

                current.accessMode == StorageAccessMode.DOCUMENT_TREE &&
                    current.currentPath.isNotEmpty() &&
                    hasPersistedTreeGrant(current.treeUriString) -> Unit

                else -> loadSharedStorageRoot()
            }
            return
        }

        if (
            current.accessMode == StorageAccessMode.DOCUMENT_TREE &&
            current.currentPath.isNotEmpty() &&
            hasPersistedTreeGrant(current.treeUriString)
        ) return

        val savedTreeUri = preferences.getString(KEY_TREE_URI, null)
        if (savedTreeUri != null && hasPersistedTreeGrant(savedTreeUri)) {
            if (
                current.accessMode != StorageAccessMode.DOCUMENT_TREE ||
                current.treeUriString != savedTreeUri ||
                current.currentPath.isEmpty()
            ) {
                restoreTree(savedTreeUri)
            }
            return
        }

        operationJob?.cancel()
        mutableUiState.value = FilesUiState()
    }

    fun selectTree(treeUri: Uri) {
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            mutableUiState.value = FilesUiState(
                accessMode = StorageAccessMode.DOCUMENT_TREE,
                treeUriString = treeUri.toString(),
                isLoading = true
            )
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                preferences.edit().putString(KEY_TREE_URI, treeUri.toString()).apply()
                openTreeAndLoadRoot(treeUri.toString())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.value = FilesUiState(
                    accessMode = StorageAccessMode.DOCUMENT_TREE,
                    treeUriString = treeUri.toString(),
                    errorMessage = userMessage(error)
                )
            }
        }
    }

    fun navigateInto(entry: StorageEntry) {
        if (!entry.isDirectory) return
        val state = mutableUiState.value
        val newLocation = if (
            state.accessMode == StorageAccessMode.SHARED_STORAGE ||
            state.accessMode == StorageAccessMode.SHIZUKU_STORAGE
        ) {
            val path = entry.absolutePath ?: return
            FolderLocation(entry.documentId, entry.displayName, path)
        } else {
            FolderLocation(entry.documentId, entry.displayName)
        }

        operationJob?.cancel()
        mutableUiState.value = state.copy(
            currentPath = state.currentPath + newLocation,
            entries = emptyList(),
            isLoading = true,
            errorMessage = null
        )
        operationJob = viewModelScope.launch {
            loadChildren(state.accessMode, state.treeUriString, newLocation)
        }
    }

    fun navigateBack() {
        val state = mutableUiState.value
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
            loadChildren(state.accessMode, state.treeUriString, parent)
        }
    }

    fun refresh() {
        val state = mutableUiState.value
        val location = state.currentPath.lastOrNull() ?: return
        if (state.accessMode == StorageAccessMode.NONE) return

        operationJob?.cancel()
        mutableUiState.value = state.copy(
            entries = emptyList(),
            isLoading = true,
            errorMessage = null
        )
        operationJob = viewModelScope.launch {
            loadChildren(state.accessMode, state.treeUriString, location)
        }
    }

    internal suspend fun loadEntryVisual(entry: StorageEntry, state: FilesUiState): EntryVisual =
        withContext(Dispatchers.IO) {
            entryVisualLoadSemaphore.withPermit { entryVisualLoader.load(entry, state) }
        }

    private fun hasSharedStorageAccess(): Boolean {
        val application = getApplication<Application>()
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                Environment.isExternalStorageManager()

            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ->
                application.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED &&
                    application.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED

            // Android 10 has scoped storage but not the all-files special access switch.
            // Use the user-selected Storage Access Framework tree on that release.
            else -> false
        }
    }

    private fun hasPersistedTreeGrant(treeUriString: String?): Boolean {
        if (treeUriString.isNullOrBlank()) return false
        val treeUri = Uri.parse(treeUriString)
        return getApplication<Application>().contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }
    }

    private fun loadSharedStorageRoot() {
        operationJob?.cancel()
        mutableUiState.value = FilesUiState(
            accessMode = StorageAccessMode.SHARED_STORAGE,
            isLoading = true
        )
        operationJob = viewModelScope.launch {
            try {
                val rootLocation = withContext(Dispatchers.IO) {
                    val root = sharedStorageGateway.rootDirectory()
                    if (!root.exists() || !root.isDirectory || !root.canRead()) {
                        throw SecurityException("Android denied access to shared storage.")
                    }
                    FolderLocation(
                        documentId = root.absolutePath,
                        displayName = "Internal storage",
                        absolutePath = root.absolutePath
                    )
                }
                mutableUiState.value = FilesUiState(
                    accessMode = StorageAccessMode.SHARED_STORAGE,
                    currentPath = listOf(rootLocation),
                    isLoading = true
                )
                val children = withContext(Dispatchers.IO) {
                    sharedStorageGateway.listChildren(rootLocation.absolutePath!!)
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
                    isLoading = false,
                    errorMessage = userMessage(error)
                )
            }
        }
    }

    fun openShizukuRoot() {
        if (!shizukuStorageAdapter.status.value.isConnected) {
            mutableUiState.value = mutableUiState.value.copy(
                errorMessage = "Connect to Shizuku first to browse protected folders."
            )
            return
        }

        val rootPath = shizukuStorageAdapter.sharedStorageRootPath
        val rootLocation = FolderLocation(
            documentId = rootPath,
            displayName = "Shared storage (Shizuku)",
            absolutePath = rootPath
        )
        operationJob?.cancel()
        mutableUiState.value = FilesUiState(
            accessMode = StorageAccessMode.SHIZUKU_STORAGE,
            currentPath = listOf(rootLocation),
            isLoading = true
        )
        operationJob = viewModelScope.launch {
            loadChildren(StorageAccessMode.SHIZUKU_STORAGE, null, rootLocation)
        }
    }

    private fun restoreTree(treeUriString: String) {
        operationJob?.cancel()
        mutableUiState.value = FilesUiState(
            accessMode = StorageAccessMode.DOCUMENT_TREE,
            treeUriString = treeUriString,
            isLoading = true
        )
        operationJob = viewModelScope.launch {
            openTreeAndLoadRoot(treeUriString)
        }
    }

    private suspend fun openTreeAndLoadRoot(treeUriString: String) {
        try {
            val treeUri = Uri.parse(treeUriString)
            if (!hasPersistedTreeGrant(treeUriString)) {
                throw SecurityException("The saved folder permission is no longer available.")
            }

            val rootDocumentId = withContext(Dispatchers.IO) {
                DocumentsContract.getTreeDocumentId(treeUri)
            }
            val root = withContext(Dispatchers.IO) {
                safGateway.getDocument(treeUri, rootDocumentId)
            } ?: throw IllegalStateException("The selected folder is no longer available.")

            val rootLocation = FolderLocation(root.documentId, root.displayName)
            mutableUiState.value = FilesUiState(
                accessMode = StorageAccessMode.DOCUMENT_TREE,
                treeUriString = treeUriString,
                currentPath = listOf(rootLocation),
                isLoading = true
            )
            loadChildren(StorageAccessMode.DOCUMENT_TREE, treeUriString, rootLocation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableUiState.value = FilesUiState(
                accessMode = StorageAccessMode.DOCUMENT_TREE,
                treeUriString = treeUriString,
                errorMessage = userMessage(error)
            )
        }
    }

    private suspend fun loadChildren(
        accessMode: StorageAccessMode,
        treeUriString: String?,
        location: FolderLocation
    ) {
        try {
            val children = withContext(Dispatchers.IO) {
                when (accessMode) {
                    StorageAccessMode.SHARED_STORAGE ->
                        sharedStorageGateway.listChildren(location.absolutePath ?: location.documentId)

                    StorageAccessMode.SHIZUKU_STORAGE ->
                        shizukuStorageAdapter.listChildren(location.absolutePath ?: location.documentId)

                    StorageAccessMode.DOCUMENT_TREE -> {
                        val treeUri = Uri.parse(
                            treeUriString ?: throw IllegalStateException("No folder was selected.")
                        )
                        safGateway.listChildren(treeUri, location.documentId)
                    }

                    StorageAccessMode.NONE -> emptyList()
                }
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
            "Android denied access to this location. Grant storage access or choose a folder again."
        else ->
            "Couldn't read this location. It may have moved, or its storage provider may be unavailable."
    }

    override fun onCleared() {
        entryVisualLoader.clear()
        super.onCleared()
    }

    private companion object {
        const val PREFERENCES_NAME = "jarvision_storage"
        const val KEY_TREE_URI = "selected_tree_uri"
    }
}
