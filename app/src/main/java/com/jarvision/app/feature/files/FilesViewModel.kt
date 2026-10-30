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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

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

data class StoragePathCapabilities(
    val canCreateChildren: Boolean = false,
    val canRename: Boolean = false,
    val canDelete: Boolean = false
)

data class QuickAccessLocation(
    val id: String,
    val label: String,
    val accessMode: StorageAccessMode,
    val treeUriString: String?,
    val path: List<FolderLocation>,
    val isCustom: Boolean = false
)

internal fun folderIconKey(state: FilesUiState, entry: StorageEntry): String = listOf(
    state.accessMode.name,
    state.treeUriString.orEmpty(),
    entry.absolutePath ?: entry.documentId
).joinToString("|")

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, 0)
    private val safGateway = SafStorageGateway(application.contentResolver)
    private val sharedStorageGateway = SharedStorageGateway()
    private val shizukuStorageAdapter = ShizukuStorageAdapter.get(application)
    private val entryVisualLoader = EntryVisualLoader(application)
    private val entryVisualLoadSemaphore = Semaphore(3)

    private val mutableUiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = mutableUiState.asStateFlow()

    private val mutableSelectedEntryIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedEntryIds: StateFlow<Set<String>> = mutableSelectedEntryIds.asStateFlow()

    fun toggleEntrySelection(documentId: String) {
        mutableSelectedEntryIds.value = if (documentId in mutableSelectedEntryIds.value) {
            mutableSelectedEntryIds.value - documentId
        } else {
            mutableSelectedEntryIds.value + documentId
        }
    }

    fun selectEntries(documentIds: Collection<String>) {
        mutableSelectedEntryIds.value = mutableSelectedEntryIds.value + documentIds
    }

    fun invertSelection(documentIds: Collection<String>) {
        val updated = mutableSelectedEntryIds.value.toMutableSet()
        documentIds.forEach { id -> if (!updated.add(id)) updated.remove(id) }
        mutableSelectedEntryIds.value = updated
    }

    fun clearSelection() { mutableSelectedEntryIds.value = emptySet() }

    fun setSelectedEntries(documentIds: Collection<String>) {
        mutableSelectedEntryIds.value = documentIds.toSet()
    }

    private val mutableCetShortcuts = MutableStateFlow(readCetShortcuts())
    val cetShortcuts: StateFlow<List<QuickAccessLocation>> = mutableCetShortcuts.asStateFlow()
    private val mutableCustomFolderIconUris = MutableStateFlow(readCustomFolderIconUris())
    val customFolderIconUris: StateFlow<Map<String, String>> = mutableCustomFolderIconUris.asStateFlow()

    private var operationJob: Job? = null
    private var shizukuReconnectAttempted = false

    init {
        viewModelScope.launch {
            shizukuStorageAdapter.status.collect { status ->
                if (preferredAccessMode() != StorageAccessMode.SHIZUKU_STORAGE) {
                    shizukuReconnectAttempted = false
                    return@collect
                }

                val current = mutableUiState.value
                if (status.isConnected) {
                    shizukuReconnectAttempted = false
                    if (
                        current.accessMode != StorageAccessMode.SHIZUKU_STORAGE ||
                        current.currentPath.isEmpty() ||
                        current.errorMessage != null
                    ) {
                        openShizukuRoot()
                    }
                } else if (
                    status.binderAvailable &&
                    status.permissionGranted &&
                    !status.isBinding &&
                    !shizukuReconnectAttempted
                ) {
                    shizukuReconnectAttempted = true
                    shizukuStorageAdapter.reconnectIfAuthorized()
                } else {
                    if (!status.binderAvailable) shizukuReconnectAttempted = false
                    val message = when {
                        status.isBinding -> null
                        !status.binderAvailable ->
                            "Shizuku is not running. Start its service to reconnect."
                        !status.permissionGranted ->
                            "Grant JarVision permission in Shizuku to reconnect."
                        else -> status.message
                    }
                    mutableUiState.value = current.copy(
                        accessMode = StorageAccessMode.SHIZUKU_STORAGE,
                        treeUriString = null,
                        entries = emptyList(),
                        isLoading = status.isBinding,
                        errorMessage = message
                    )
                }
            }
        }
        refreshAccess()
    }

    /**
     * Re-check this when returning from system Settings or a runtime permission prompt.
     * Broad access is preferred on first launch; an existing folder-only session is preserved.
     */
    private fun preferredAccessMode(): StorageAccessMode? =
        preferences.getString(ShizukuStorageAdapter.KEY_PREFERRED_ACCESS_MODE, null)
            ?.let { runCatching { StorageAccessMode.valueOf(it) }.getOrNull() }

    fun preferShizukuStorage() {
        savePreferredAccessMode(StorageAccessMode.SHIZUKU_STORAGE)
    }

    private fun savePreferredAccessMode(mode: StorageAccessMode) {
        preferences.edit()
            .putString(ShizukuStorageAdapter.KEY_PREFERRED_ACCESS_MODE, mode.name)
            .apply()
    }

    fun refreshAccess() {
        val current = mutableUiState.value
        when (preferredAccessMode()) {
            StorageAccessMode.SHIZUKU_STORAGE -> {
                val status = shizukuStorageAdapter.status.value
                if (status.isConnected) {
                    if (
                        current.accessMode == StorageAccessMode.SHIZUKU_STORAGE &&
                        current.currentPath.isNotEmpty() &&
                        current.errorMessage == null
                    ) {
                        return
                    }
                    openShizukuRoot()
                    return
                }

                if (
                    status.binderAvailable &&
                    status.permissionGranted &&
                    !status.isBinding &&
                    !shizukuReconnectAttempted
                ) {
                    shizukuReconnectAttempted = true
                    shizukuStorageAdapter.reconnectIfAuthorized()
                }
                val message = when {
                    status.isBinding -> null
                    !status.binderAvailable ->
                        "Shizuku is not running. Start its service to reconnect."
                    !status.permissionGranted ->
                        "Grant JarVision permission in Shizuku to reconnect."
                    else -> status.message
                }
                mutableUiState.value = current.copy(
                    accessMode = StorageAccessMode.SHIZUKU_STORAGE,
                    treeUriString = null,
                    entries = emptyList(),
                    isLoading = status.isBinding,
                    errorMessage = message
                )
                return
            }
            StorageAccessMode.DOCUMENT_TREE -> {
                val savedTreeUri = preferences.getString(KEY_TREE_URI, null)
                if (savedTreeUri != null && hasPersistedTreeGrant(savedTreeUri)) {
                    if (
                        current.accessMode == StorageAccessMode.DOCUMENT_TREE &&
                        current.treeUriString == savedTreeUri &&
                        current.currentPath.isNotEmpty()
                    ) return
                    restoreTree(savedTreeUri)
                    return
                }
            }
            StorageAccessMode.SHARED_STORAGE -> {
                if (hasSharedStorageAccess()) {
                    if (
                        current.accessMode == StorageAccessMode.SHARED_STORAGE &&
                        current.currentPath.isNotEmpty()
                    ) {
                        refresh()
                    } else {
                        loadSharedStorageRoot()
                    }
                    return
                }
            }
            StorageAccessMode.NONE, null -> Unit
        }
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
                preferences.edit()
                    .putString(KEY_TREE_URI, treeUri.toString())
                    .putString(ShizukuStorageAdapter.KEY_PREFERRED_ACCESS_MODE, StorageAccessMode.DOCUMENT_TREE.name)
                    .apply()
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

    /**
     * Path-specific write capability. This is an advisory check; the storage operation remains
     * authoritative because filesystem and document-provider permissions can change at runtime.
     */
    suspend fun canWriteDirectory(
        location: FolderLocation,
        state: FilesUiState = mutableUiState.value
    ): Boolean = withContext(Dispatchers.IO) {
        when (state.accessMode) {
            StorageAccessMode.SHARED_STORAGE -> runCatching {
                val root = sharedStorageGateway.rootDirectory().canonicalFile
                val requested = File(location.absolutePath ?: location.documentId).absoluteFile
                val directory = requested.canonicalFile
                directory.isDirectory &&
                    (directory == root || directory.path.startsWith(root.path + File.separator)) &&
                    directory.canWrite()
            }.getOrDefault(false)

            StorageAccessMode.DOCUMENT_TREE -> runCatching {
                val treeUri = state.treeUriString?.let(Uri::parse) ?: return@runCatching false
                val hasWriteGrant = getApplication<Application>().contentResolver.persistedUriPermissions.any {
                    it.uri == treeUri && it.isWritePermission
                }
                hasWriteGrant && safGateway.getDocument(treeUri, location.documentId)?.isDirectory == true
            }.getOrDefault(false)

            StorageAccessMode.SHIZUKU_STORAGE -> runCatching {
                val status = shizukuStorageAdapter.status.value
                if (!status.isConnected || !status.permissionGranted) return@runCatching false
                shizukuStorageAdapter.getOperationCapabilities(
                    location.absolutePath ?: location.documentId
                ).getBoolean(ShizukuStorageAdapter.KEY_CAN_CREATE_CHILDREN)
            }.getOrDefault(false)

            StorageAccessMode.NONE -> false
        }
    }

    /** Path-specific mutation hints for the selected item, evaluated by its active backend. */
    suspend fun getEntryCapabilities(
        entry: StorageEntry,
        parent: FolderLocation,
        state: FilesUiState = mutableUiState.value
    ): StoragePathCapabilities = withContext(Dispatchers.IO) {
        when (state.accessMode) {
            StorageAccessMode.SHARED_STORAGE -> runCatching {
                val root = sharedStorageGateway.rootDirectory().canonicalFile
                val parentDirectory = File(parent.absolutePath ?: parent.documentId).canonicalFile
                val item = File(entry.absolutePath ?: entry.documentId).canonicalFile
                val insideRoot = item.path.startsWith(root.path + File.separator)
                val parentMatches = item.parentFile?.canonicalFile == parentDirectory
                val allowed = parentDirectory.isDirectory && parentDirectory.canWrite() &&
                    item.exists() && insideRoot && parentMatches
                StoragePathCapabilities(
                    canCreateChildren = entry.isDirectory && item.canWrite(),
                    canRename = allowed,
                    canDelete = allowed
                )
            }.getOrDefault(StoragePathCapabilities())

            StorageAccessMode.DOCUMENT_TREE -> runCatching {
                val treeUri = state.treeUriString?.let(Uri::parse) ?: return@runCatching StoragePathCapabilities()
                val hasWriteGrant = getApplication<Application>().contentResolver.persistedUriPermissions.any {
                    it.uri == treeUri && it.isWritePermission
                }
                val parentIsDirectory = safGateway.getDocument(treeUri, parent.documentId)?.isDirectory == true
                val exists = safGateway.getDocument(treeUri, entry.documentId) != null
                val canMutate = hasWriteGrant && parentIsDirectory && exists
                StoragePathCapabilities(
                    canCreateChildren = entry.isDirectory && canMutate,
                    canRename = canMutate,
                    canDelete = canMutate
                )
            }.getOrDefault(StoragePathCapabilities())

            StorageAccessMode.SHIZUKU_STORAGE -> runCatching {
                val status = shizukuStorageAdapter.status.value
                if (!status.isConnected || !status.permissionGranted) return@runCatching StoragePathCapabilities()
                val path = entry.absolutePath ?: return@runCatching StoragePathCapabilities()
                val capabilities = shizukuStorageAdapter.getOperationCapabilities(path)
                StoragePathCapabilities(
                    canCreateChildren = capabilities.getBoolean(ShizukuStorageAdapter.KEY_CAN_CREATE_CHILDREN),
                    canRename = capabilities.getBoolean(ShizukuStorageAdapter.KEY_CAN_RENAME),
                    canDelete = capabilities.getBoolean(ShizukuStorageAdapter.KEY_CAN_DELETE)
                )
            }.getOrDefault(StoragePathCapabilities())

            StorageAccessMode.NONE -> StoragePathCapabilities()
        }
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
        savePreferredAccessMode(StorageAccessMode.SHARED_STORAGE)
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
                        displayName = "Storage",
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
        savePreferredAccessMode(StorageAccessMode.SHIZUKU_STORAGE)
        val rootLocation = FolderLocation(
            documentId = rootPath,
            displayName = "Storage",
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

    fun saveCurrentFolderAsCet(): Boolean {
        val state = mutableUiState.value
        val current = state.currentPath.lastOrNull() ?: return false
        if (state.accessMode == StorageAccessMode.NONE) return false
        if (mutableCetShortcuts.value.any {
                it.accessMode == state.accessMode &&
                    it.treeUriString == state.treeUriString &&
                    it.path.lastOrNull()?.documentId == current.documentId
            }) return false
        val updated = mutableCetShortcuts.value + QuickAccessLocation(
            id = UUID.randomUUID().toString(),
            label = current.displayName.ifBlank { "Custom folder" },
            accessMode = state.accessMode,
            treeUriString = state.treeUriString,
            path = state.currentPath.toList(),
            isCustom = true
        )
        mutableCetShortcuts.value = updated
        writeCetShortcuts(updated)
        return true
    }

    fun saveEntryAsFavorite(entry: StorageEntry): Boolean {
        if (!entry.isDirectory) return false
        val state = mutableUiState.value
        if (state.accessMode == StorageAccessMode.NONE) return false

        val location = when (state.accessMode) {
            StorageAccessMode.SHARED_STORAGE, StorageAccessMode.SHIZUKU_STORAGE -> {
                val path = entry.absolutePath ?: return false
                FolderLocation(entry.documentId, entry.displayName, path)
            }
            StorageAccessMode.DOCUMENT_TREE -> FolderLocation(entry.documentId, entry.displayName)
            StorageAccessMode.NONE -> return false
        }

        if (mutableCetShortcuts.value.any {
                it.accessMode == state.accessMode &&
                    it.treeUriString == state.treeUriString &&
                    it.path.lastOrNull()?.documentId == entry.documentId
            }) return false

        val updated = mutableCetShortcuts.value + QuickAccessLocation(
            id = UUID.randomUUID().toString(),
            label = entry.displayName.ifBlank { "Favorite folder" },
            accessMode = state.accessMode,
            treeUriString = state.treeUriString,
            path = state.currentPath + location,
            isCustom = true
        )
        mutableCetShortcuts.value = updated
        writeCetShortcuts(updated)
        return true
    }

    fun removeCetShortcut(id: String) {
        val updated = mutableCetShortcuts.value.filterNot { it.id == id }
        mutableCetShortcuts.value = updated
        writeCetShortcuts(updated)
    }

    fun navigateToQuickAccess(shortcut: QuickAccessLocation) {
        val target = shortcut.path.lastOrNull() ?: return
        when (shortcut.accessMode) {
            StorageAccessMode.SHARED_STORAGE -> if (!hasSharedStorageAccess()) {
                mutableUiState.value = mutableUiState.value.copy(errorMessage = "Shared-storage access is unavailable. Grant storage access and try again.")
                return
            }
            StorageAccessMode.SHIZUKU_STORAGE -> if (!shizukuStorageAdapter.status.value.isConnected) {
                mutableUiState.value = mutableUiState.value.copy(errorMessage = "Connect to Shizuku before opening this shortcut.")
                return
            }
            StorageAccessMode.DOCUMENT_TREE -> if (!hasPersistedTreeGrant(shortcut.treeUriString)) {
                mutableUiState.value = mutableUiState.value.copy(errorMessage = "The folder permission for this shortcut has expired. Choose the folder again.")
                return
            }
            StorageAccessMode.NONE -> return
        }
        operationJob?.cancel()
        mutableUiState.value = mutableUiState.value.copy(
            accessMode = shortcut.accessMode, treeUriString = shortcut.treeUriString,
            currentPath = shortcut.path, entries = emptyList(), isLoading = true, errorMessage = null
        )
        operationJob = viewModelScope.launch { loadChildren(shortcut.accessMode, shortcut.treeUriString, target) }
    }

    fun setCustomFolderIcon(entry: StorageEntry, state: FilesUiState, uri: Uri?) {
        if (!entry.isDirectory) return
        val key = folderIconKey(state, entry)
        val updated = if (uri == null) mutableCustomFolderIconUris.value - key else mutableCustomFolderIconUris.value + (key to uri.toString())
        mutableCustomFolderIconUris.value = updated
        val json = JSONObject()
        updated.forEach { (folderKey, value) -> json.put(folderKey, value) }
        preferences.edit().putString(KEY_CUSTOM_FOLDER_ICONS, json.toString()).apply()
    }

    private fun readCetShortcuts(): List<QuickAccessLocation> {
        val raw = preferences.getString(KEY_CET_SHORTCUTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val mode = runCatching { StorageAccessMode.valueOf(item.optString("accessMode")) }.getOrNull() ?: continue
                    if (mode == StorageAccessMode.NONE) continue
                    val savedPath = item.optJSONArray("path") ?: continue
                    val path = buildList {
                        for (p in 0 until savedPath.length()) {
                            val folder = savedPath.optJSONObject(p) ?: continue
                            val documentId = folder.optString("documentId")
                            if (documentId.isBlank()) continue
                            add(FolderLocation(
                                documentId = documentId,
                                displayName = folder.optString("displayName").ifBlank { documentId },
                                absolutePath = folder.optString("absolutePath").takeIf { it.isNotBlank() }
                            ))
                        }
                    }
                    if (path.isEmpty()) continue
                    add(QuickAccessLocation(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        label = item.optString("label").ifBlank { path.last().displayName },
                        accessMode = mode,
                        treeUriString = item.optString("treeUriString").takeIf { it.isNotBlank() },
                        path = path, isCustom = true
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeCetShortcuts(shortcuts: List<QuickAccessLocation>) {
        val array = JSONArray()
        shortcuts.forEach { shortcut ->
            val item = JSONObject().put("id", shortcut.id).put("label", shortcut.label)
                .put("accessMode", shortcut.accessMode.name).put("treeUriString", shortcut.treeUriString.orEmpty())
            val path = JSONArray()
            shortcut.path.forEach { folder ->
                path.put(JSONObject().put("documentId", folder.documentId).put("displayName", folder.displayName)
                    .put("absolutePath", folder.absolutePath.orEmpty()))
            }
            item.put("path", path)
            array.put(item)
        }
        preferences.edit().putString(KEY_CET_SHORTCUTS, array.toString()).apply()
    }

    private fun readCustomFolderIconUris(): Map<String, String> {
        val raw = preferences.getString(KEY_CUSTOM_FOLDER_ICONS, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    json.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) }
                }
            }
        }.getOrDefault(emptyMap())
    }

    override fun onCleared() {
        entryVisualLoader.clear()
        super.onCleared()
    }

    private companion object {
        const val PREFERENCES_NAME = ShizukuStorageAdapter.PREFERENCES_NAME
        const val KEY_TREE_URI = "selected_tree_uri"
        const val KEY_CET_SHORTCUTS = "custom_easy_tabs_v1"
        const val KEY_CUSTOM_FOLDER_ICONS = "custom_folder_icons_v1"
    }
}
