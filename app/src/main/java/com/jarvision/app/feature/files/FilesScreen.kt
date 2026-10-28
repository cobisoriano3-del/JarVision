package com.jarvision.app.feature.files

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import java.io.IOException
import com.jarvision.app.data.storage.ZipArchiveTestResult
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.Dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jarvision.app.data.storage.SafStorageGateway
import com.jarvision.app.data.storage.SharedStorageGateway
import com.jarvision.app.data.storage.ShizukuStorageAdapter
import com.jarvision.app.data.storage.StorageEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PendingFileClipboard(
    val entries: List<StorageEntry>,
    val accessMode: StorageAccessMode,
    val treeUriString: String?,
    val sourceParentDocumentId: String,
    val sourceParentAbsolutePath: String?,
    val isCut: Boolean
)

private data class PasteConflict(
    val clipboard: PendingFileClipboard,
    val existing: List<StorageEntry>,
    val destination: FolderLocation,
    val sameSource: Boolean
)

private enum class BrowserViewMode { COMPACT, DETAILED, GRID }
private enum class BrowserSortOrder { NAME, SIZE, DATE, TYPE }
private enum class CreateItemKind { FOLDER, FILE, ZIP }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilesScreen(
    filesViewModel: FilesViewModel = viewModel(),
    onOpenSettings: () -> Unit = {},
    isActive: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by filesViewModel.uiState.collectAsState()
    val cetShortcuts by filesViewModel.cetShortcuts.collectAsState()
    val customFolderIconUris by filesViewModel.customFolderIconUris.collectAsState()
    val uiPreferences = remember(context) {
        context.getSharedPreferences("jarvision_file_ui", android.content.Context.MODE_PRIVATE)
    }
    var viewMode by remember(uiPreferences) {
        mutableStateOf(
            runCatching {
                BrowserViewMode.valueOf(uiPreferences.getString("view_mode", "COMPACT") ?: "COMPACT")
            }.getOrDefault(BrowserViewMode.COMPACT)
        )
    }
    var sortOrder by remember(uiPreferences) {
        mutableStateOf(
            runCatching {
                BrowserSortOrder.valueOf(uiPreferences.getString("sort_order", "NAME") ?: "NAME")
            }.getOrDefault(BrowserSortOrder.NAME)
        )
    }
    var sortDescending by remember(uiPreferences) {
        mutableStateOf(uiPreferences.getBoolean("sort_descending", false))
    }
    var showHiddenFiles by remember(uiPreferences) {
        mutableStateOf(uiPreferences.getBoolean("show_hidden_files", false))
    }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedEntryIds by filesViewModel.selectedEntryIds.collectAsState()
    var bulkDeleteConfirmation by remember { mutableStateOf(false) }
    var createKind by remember { mutableStateOf<CreateItemKind?>(null) }
    var createName by remember { mutableStateOf("") }
    var aboutDialogVisible by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(viewMode, sortOrder, sortDescending, showHiddenFiles) {
        uiPreferences.edit()
            .putString("view_mode", viewMode.name)
            .putString("sort_order", sortOrder.name)
            .putBoolean("sort_descending", sortDescending)
            .putBoolean("show_hidden_files", showHiddenFiles)
            .apply()
    }

    val shizukuAdapter = remember(context) { ShizukuStorageAdapter.get(context) }
    val fileOperations = remember(context, shizukuAdapter) {
        FileOperationCoordinator(context, shizukuAdapter)
    }
    val shizukuStatus by shizukuAdapter.status.collectAsState()
    val canMutateFiles = when (state.accessMode) {
        StorageAccessMode.SHARED_STORAGE -> true
        StorageAccessMode.DOCUMENT_TREE -> state.treeUriString?.let { value ->
            context.contentResolver.persistedUriPermissions.any {
                it.uri == Uri.parse(value) && it.isWritePermission
            }
        } == true
        else -> false
    }

    val shizukuCurrentPath = state.currentPath.lastOrNull()?.let {
        it.absolutePath ?: it.documentId
    }
    val hasAuthorizedShizukuCreationRoute =
        state.accessMode == StorageAccessMode.SHIZUKU_STORAGE &&
            shizukuStatus.isConnected &&
            shizukuStatus.permissionGranted &&
            (shizukuStatus.serviceUid == 0 || shizukuStatus.serviceUid == 2000) &&
            !shizukuCurrentPath.isNullOrBlank()
    val canCreateItems = canMutateFiles || hasAuthorizedShizukuCreationRoute

    // Collapse the speed dial when its directory, storage context, or tab changes.
    LaunchedEffect(
        isActive,
        state.accessMode,
        state.treeUriString,
        state.currentPath,
        canMutateFiles,
        canCreateItems
    ) {
        fabExpanded = false
    }

    val visibleEntries = remember(
        state.entries, searchQuery, showHiddenFiles, sortOrder, sortDescending
    ) {
        val comparator: Comparator<StorageEntry> = when (sortOrder) {
            BrowserSortOrder.NAME -> Comparator { left, right ->
                left.displayName.compareTo(right.displayName, ignoreCase = true)
            }
            BrowserSortOrder.SIZE -> Comparator { left, right ->
                (left.sizeBytes ?: -1L).compareTo(right.sizeBytes ?: -1L)
            }
            BrowserSortOrder.DATE -> Comparator { left, right ->
                (left.lastModifiedMillis ?: -1L).compareTo(right.lastModifiedMillis ?: -1L)
            }
            BrowserSortOrder.TYPE -> Comparator { left, right ->
                val leftType = left.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val rightType = right.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val byType = leftType.compareTo(rightType)
                if (byType != 0) byType else left.displayName.compareTo(right.displayName, ignoreCase = true)
            }
        }
        val orderedComparator = if (sortDescending) comparator.reversed() else comparator
        state.entries.asSequence()
            .filter { showHiddenFiles || !it.displayName.startsWith(".") }
            .filter { searchQuery.isBlank() || it.displayName.contains(searchQuery.trim(), ignoreCase = true) }
            .toList()
            .sortedWith(compareBy<StorageEntry> { !it.isDirectory }.then(orderedComparator))
    }
    val selectedEntries = remember(state.entries, selectedEntryIds) {
        state.entries.filter { it.documentId in selectedEntryIds }
    }
    val selectedItemsAreArchives = selectedEntries.isNotEmpty() && selectedEntries.all {
        !it.isDirectory && it.displayName.lowercase(Locale.ROOT).endsWith(".zip")
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var shouldOpenShizukuAfterConnect by remember { mutableStateOf(false) }
    var previewContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedEntry by remember { mutableStateOf<StorageEntry?>(null) }
    var folderIconTarget by remember { mutableStateOf<Pair<StorageEntry, FilesUiState>?>(null) }
    var informationEntry by remember { mutableStateOf<StorageEntry?>(null) }
    var renameEntry by remember { mutableStateOf<StorageEntry?>(null) }
    var renameName by remember { mutableStateOf("") }
    var compressEntries by remember { mutableStateOf<List<StorageEntry>?>(null) }
    var compressName by remember { mutableStateOf("") }
    var deleteEntry by remember { mutableStateOf<StorageEntry?>(null) }
    var clipboard by remember { mutableStateOf<PendingFileClipboard?>(null) }
    var pasteConflict by remember { mutableStateOf<PasteConflict?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) filesViewModel.selectTree(uri)
    }
    val folderIconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val target = folderIconTarget
        folderIconTarget = null
        if (uri != null && target != null) {
            val permissionSaved = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (permissionSaved) {
                filesViewModel.setCustomFolderIcon(target.first, target.second, uri)
                coroutineScope.launch { snackbarHostState.showSnackbar("Custom folder image saved.") }
            } else {
                coroutineScope.launch { snackbarHostState.showSnackbar("Android did not grant persistent access to that image. Choose another image provider.") }
            }
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        filesViewModel.refreshAccess()
    }
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        filesViewModel.refreshAccess()
    }

    DisposableEffect(lifecycleOwner, filesViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                filesViewModel.refreshAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun chooseFolder() {
        folderPicker.launch(state.treeUriString?.let { Uri.parse(it) })
    }

    fun openShizukuBrowser() {
        if (shizukuStatus.isConnected) {
            shouldOpenShizukuAfterConnect = false
            filesViewModel.openShizukuRoot()
        } else {
            shouldOpenShizukuAfterConnect = true
            shizukuAdapter.connect()
        }
    }

    LaunchedEffect(shizukuStatus.isConnected, shouldOpenShizukuAfterConnect) {
        if (shizukuStatus.isConnected && shouldOpenShizukuAfterConnect) {
            shouldOpenShizukuAfterConnect = false
            filesViewModel.openShizukuRoot()
        }
    }

    fun requestStorageAccess() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val appSettingsIntent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                try {
                    settingsLauncher.launch(appSettingsIntent)
                } catch (_: ActivityNotFoundException) {
                    try {
                        settingsLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        )
                    } catch (_: ActivityNotFoundException) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                "Android could not open storage access settings. Choose a folder instead."
                            )
                        }
                    }
                }
            }

            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> {
                legacyPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }

            else -> chooseFolder()
        }
    }

    fun openFile(entry: StorageEntry) {
        if (state.accessMode == StorageAccessMode.SHIZUKU_STORAGE) {
            val path = entry.absolutePath
            if (path.isNullOrBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Shizuku did not return a valid file path.")
                }
                return
            }
            coroutineScope.launch {
                try {
                    val contents = withContext(Dispatchers.IO) {
                        shizukuAdapter.readTextFile(path)
                    }
                    previewContent = entry.displayName to contents
                } catch (error: Exception) {
                    if (isLikelyTextFile(entry.displayName)) {
                        snackbarHostState.showSnackbar(
                            error.message ?: "Couldn't read this file through Shizuku."
                        )
                    } else {
                        try {
                            val cachedFile = withContext(Dispatchers.IO) {
                                fileOperations.stageShareCopy(state.accessMode, state.treeUriString, entry)
                            }
                            val uri = FileProvider.getUriForFile(
                                context,
                                context.packageName + ".fileprovider",
                                cachedFile
                            )
                            val intent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, bestMimeType(entry))
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            intent.clipData = ClipData.newUri(context.contentResolver, entry.displayName, uri)
                            context.startActivity(intent)
                        } catch (openError: Exception) {
                            snackbarHostState.showSnackbar(
                                openError.message ?: "No installed app could open this protected file."
                            )
                        }
                    }
                }
            }
            return
        }

        val fileUri = try {
            if (
                state.accessMode == StorageAccessMode.SHARED_STORAGE &&
                entry.absolutePath != null
            ) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(entry.absolutePath)
                )
            } else {
                val treeUriString = state.treeUriString ?: return
                DocumentsContract.buildDocumentUriUsingTree(
                    Uri.parse(treeUriString),
                    entry.documentId
                )
            }
        } catch (_: IllegalArgumentException) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("JarVision couldn't safely share this file.")
            }
            return
        }

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(fileUri, bestMimeType(entry))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newUri(context.contentResolver, entry.displayName, fileUri)

        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("No installed app can open this file.")
            }
        } catch (_: SecurityException) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Android denied access to this file.")
            }
        }
    }

    fun showMessage(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }


    fun stageClipboard(entries: List<StorageEntry>, cut: Boolean) {
        val parent = state.currentPath.lastOrNull() ?: return
        if (entries.isEmpty()) return
        if (state.accessMode == StorageAccessMode.SHIZUKU_STORAGE || state.accessMode == StorageAccessMode.NONE) {
            showMessage("Copy and cut are unavailable in this storage mode.")
            selectedEntry = null
            return
        }
        clipboard = PendingFileClipboard(entries.toList(), state.accessMode, state.treeUriString,
            parent.documentId, parent.absolutePath, cut)
        selectedEntry = null
        showMessage(if (cut) "${entries.size} item(s) marked for moving. Navigate to a destination and tap Paste."
            else "${entries.size} item(s) copied to JarVision's clipboard.")
    }

    fun executePaste(pending: PendingFileClipboard, keepBoth: Boolean, replaceExisting: Boolean) {
        val destination = state.currentPath.lastOrNull() ?: return
        if (pending.entries.isEmpty()) { showMessage("The clipboard is empty."); return }
        if (pending.accessMode != state.accessMode || pending.treeUriString != state.treeUriString ||
            state.accessMode == StorageAccessMode.SHIZUKU_STORAGE || !canMutateFiles
        ) {
            showMessage("Paste is limited to the same writable storage scope.")
            pasteConflict = null
            return
        }
        if (pending.entries.any { source -> source.isDirectory && state.currentPath.any { it.documentId == source.documentId } }) {
            showMessage("A folder can't be pasted into itself or one of its subfolders.")
            pasteConflict = null
            return
        }
        if (pending.isCut) {
            val sameParent = if (state.accessMode == StorageAccessMode.SHARED_STORAGE)
                pending.sourceParentAbsolutePath == destination.absolutePath
            else pending.sourceParentDocumentId == destination.documentId
            if (sameParent) {
                clipboard = null
                pasteConflict = null
                filesViewModel.clearSelection()
                showMessage("These items are already in that folder.")
                return
            }
        }
        coroutineScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val usedNames = mutableSetOf<String>()
                    pending.entries.map { source ->
                        source to runCatching {
                            var targetName = source.displayName
                            if (keepBoth) {
                                val dot = targetName.lastIndexOf('.')
                                val base = if (dot > 0) targetName.substring(0, dot) else targetName
                                val extension = if (dot > 0) targetName.substring(dot) else ""
                                var suffix = 1
                                while (
                                    fileOperations.findNamedChild(state.accessMode,state.treeUriString,
                                        destination.documentId,destination.absolutePath,targetName) != null ||
                                    targetName.lowercase(Locale.ROOT) in usedNames
                                ) {
                                    targetName = base + " (" + suffix + ")" + extension
                                    suffix++
                                    if (suffix > 10000) throw IOException("Couldn't find an unused copy name.")
                                }
                            }
                            if (!usedNames.add(targetName.lowercase(Locale.ROOT))) {
                                throw IOException("Selected items contain duplicate destination names.")
                            }
                            fileOperations.copyOrMove(state.accessMode,state.treeUriString,source,
                                destination.documentId,destination.absolutePath,targetName,
                                moveSource = pending.isCut, replaceExisting = replaceExisting)
                        }
                    }
                }
                val failures = results.filter { it.second.isFailure }
                clipboard = if (failures.isEmpty()) null else pending.copy(entries = failures.map { it.first })
                pasteConflict = null
                filesViewModel.setSelectedEntries(failures.map { it.first.documentId })
                filesViewModel.refresh()
                val succeeded = results.size - failures.size
                showMessage(if (failures.isEmpty()) "$succeeded item(s) pasted successfully."
                    else "$succeeded pasted; ${failures.size} failed. " +
                        (failures.first().second.exceptionOrNull()?.message ?: "Failed items remain on the clipboard."))
            } catch (error: Exception) { showMessage(error.message ?: "The file operation failed.") }
        }
    }

    fun requestPaste() {
        val pending = clipboard ?: return
        if (pending.entries.isEmpty()) { showMessage("The clipboard is empty."); return }
        val destination = state.currentPath.lastOrNull() ?: return
        if (pending.accessMode != state.accessMode || pending.treeUriString != state.treeUriString ||
            state.accessMode == StorageAccessMode.SHIZUKU_STORAGE || !canMutateFiles
        ) { showMessage("Paste is limited to the same writable storage scope."); return }
        if (pending.entries.any { source -> source.isDirectory && state.currentPath.any { it.documentId == source.documentId } }) {
            showMessage("A folder can't be pasted into itself or one of its subfolders.")
            return
        }
        if (pending.isCut) {
            val sameParent = if (state.accessMode == StorageAccessMode.SHARED_STORAGE)
                pending.sourceParentAbsolutePath == destination.absolutePath
            else pending.sourceParentDocumentId == destination.documentId
            if (sameParent) {
                clipboard = null
                filesViewModel.clearSelection()
                showMessage("These items are already in that folder.")
                return
            }
        }
        coroutineScope.launch {
            try {
                val conflicts = withContext(Dispatchers.IO) {
                    pending.entries.mapNotNull { source ->
                        fileOperations.findNamedChild(state.accessMode,state.treeUriString,
                            destination.documentId,destination.absolutePath,source.displayName)?.let { source to it }
                    }
                }
                if (conflicts.isNotEmpty()) {
                    val sameSource = conflicts.any { (source, existing) ->
                        if (state.accessMode == StorageAccessMode.SHARED_STORAGE) source.absolutePath == existing.absolutePath
                        else source.documentId == existing.documentId
                    }
                    pasteConflict = PasteConflict(pending, conflicts.map { it.second }, destination, sameSource)
                } else executePaste(pending, keepBoth = false, replaceExisting = false)
            } catch (error: Exception) { showMessage(error.message ?: "Couldn't check the paste destination.") }
        }
    }



    fun renameSelected(entry: StorageEntry, newName: String) {
        val parent = state.currentPath.lastOrNull() ?: return
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    fileOperations.rename(
                        state.accessMode, state.treeUriString, entry,
                        parent.documentId, parent.absolutePath, newName
                    )
                }
                renameEntry = null
                filesViewModel.refresh()
                showMessage("Renamed successfully.")
            } catch (error: Exception) {
                showMessage(error.message ?: "Couldn't rename this item.")
            }
        }
    }

    fun deleteSelected(entry: StorageEntry) {
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) { fileOperations.delete(state.accessMode, state.treeUriString, entry) }
                deleteEntry = null
                filesViewModel.refresh()
                showMessage("Deleted successfully.")
            } catch (error: Exception) {
                showMessage(error.message ?: "Couldn't delete this item.")
            }
        }
    }

    fun shareSelected(entry: StorageEntry) {
        selectedEntry = null
        if (entry.isDirectory) {
            showMessage("Select a file to share.")
            return
        }
        coroutineScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    when (state.accessMode) {
                        StorageAccessMode.SHARED_STORAGE -> FileProvider.getUriForFile(
                            context, context.packageName + ".fileprovider",
                            File(entry.absolutePath ?: entry.documentId)
                        )
                        StorageAccessMode.DOCUMENT_TREE -> DocumentsContract.buildDocumentUriUsingTree(
                            Uri.parse(state.treeUriString ?: throw IOException("No selected folder permission.")),
                            entry.documentId
                        )
                        StorageAccessMode.SHIZUKU_STORAGE -> FileProvider.getUriForFile(
                            context, context.packageName + ".fileprovider",
                            fileOperations.stageShareCopy(state.accessMode, state.treeUriString, entry)
                        )
                        StorageAccessMode.NONE -> throw IOException("Choose a storage location first.")
                    }
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = bestMimeType(entry)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, entry.displayName, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share " + entry.displayName))
            } catch (error: Exception) {
                showMessage(error.message ?: "Couldn't share this file.")
            }
        }
    }


    fun compressSelected(entries: List<StorageEntry>, requestedName: String) {
        val destination = state.currentPath.lastOrNull() ?: return
        if (entries.isEmpty()) return
        coroutineScope.launch {
            try {
                val outputName = withContext(Dispatchers.IO) {
                    fileOperations.compressEntriesToZip(state.accessMode,state.treeUriString,entries,
                        destination.documentId,destination.absolutePath,requestedName)
                }
                compressEntries = null
                filesViewModel.refresh()
                showMessage("Created " + outputName + " from ${entries.size} item(s).")
            } catch (error: Exception) { showMessage(error.message ?: "Couldn't compress the selected items.") }
        }
    }

    fun testSelectedArchives(entries: List<StorageEntry>) {
        if (entries.isEmpty()) return
        coroutineScope.launch {
            val results = withContext(Dispatchers.IO) {
                entries.map { entry -> entry to runCatching { fileOperations.testZip(state.accessMode,state.treeUriString,entry) } }
            }
            val failures = results.filter { it.second.isFailure }
            val passed = results.size - failures.size
            showMessage(if (failures.isEmpty()) "All $passed ZIP archive(s) passed testing."
                else "$passed passed; ${failures.size} failed. " +
                    (failures.first().second.exceptionOrNull()?.message ?: "One or more archives are invalid."))
        }
    }

    fun extractSelectedArchives(entries: List<StorageEntry>) {
        if (entries.isEmpty()) return
        if (!canMutateFiles) { showMessage("Extracting archives requires a writable shared-storage or SAF location."); return }
        val destination = state.currentPath.lastOrNull() ?: return
        coroutineScope.launch {
            val results = withContext(Dispatchers.IO) {
                entries.map { entry -> entry to runCatching {
                    fileOperations.extractZipToFolder(state.accessMode,state.treeUriString,entry,
                        destination.documentId,destination.absolutePath)
                } }
            }
            filesViewModel.refresh()
            val failures = results.filter { it.second.isFailure }
            val extracted = results.size - failures.size
            showMessage(if (failures.isEmpty()) "Extracted $extracted ZIP archive(s) to separate folders."
                else "$extracted extracted; ${failures.size} failed. " +
                    (failures.first().second.exceptionOrNull()?.message ?: "One or more archives could not be extracted."))
        }
    }

    fun toggleSelectedEntry(entry: StorageEntry) {
        filesViewModel.toggleEntrySelection(entry.documentId)
    }

    fun beginCreate(kind: CreateItemKind) {
        if (!canCreateItems) {
            showMessage("Choose a writable SAF/shared-storage location or connect and authorize Shizuku.")
            return
        }
        createName = when (kind) {
            CreateItemKind.FOLDER -> "New folder"
            CreateItemKind.FILE -> "New file.txt"
            CreateItemKind.ZIP -> "New archive.zip"
        }
        createKind = kind
        fabExpanded = false
    }

    fun createCurrentItem(kind: CreateItemKind, requestedName: String) {
        val parent = state.currentPath.lastOrNull() ?: return
        if (!canCreateItems) {
            showMessage("No writable storage route is available. Check SAF access or the Shizuku connection.")
            createKind = null
            return
        }
        coroutineScope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    when (kind) {
                        CreateItemKind.FOLDER -> fileOperations.createFolder(
                            state.accessMode, state.treeUriString,
                            parent.documentId, parent.absolutePath, requestedName
                        )
                        CreateItemKind.FILE -> fileOperations.createEmptyFile(
                            state.accessMode, state.treeUriString,
                            parent.documentId, parent.absolutePath, requestedName
                        )
                        CreateItemKind.ZIP -> fileOperations.createEmptyZip(
                            state.accessMode, state.treeUriString,
                            parent.documentId, parent.absolutePath, requestedName
                        )
                    }
                }
                createKind = null
                filesViewModel.refresh()
                showMessage(message)
            } catch (error: Exception) {
                showMessage(error.message ?: "Couldn't create this item.")
            }
        }
    }

    fun deleteSelectedBatch() {
        val selected = state.entries.filter { it.documentId in selectedEntryIds }
        if (selected.isEmpty()) {
            bulkDeleteConfirmation = false
            return
        }
        bulkDeleteConfirmation = false
        coroutineScope.launch {
            val results = withContext(Dispatchers.IO) {
                selected.map { entry ->
                    entry to runCatching {
                        fileOperations.delete(state.accessMode, state.treeUriString, entry)
                    }
                }
            }
            val failures = results.filter { it.second.isFailure }
            filesViewModel.setSelectedEntries(failures.map { it.first.documentId })
            if (failures.isEmpty()) multiSelectMode = false
            filesViewModel.refresh()
            val failedCount = failures.size
            val successCount = selected.size - failedCount
            val detail = failures.firstOrNull()?.second?.exceptionOrNull()?.message
            showMessage(
                if (failedCount == 0) "$successCount items deleted."
                else "$successCount deleted; $failedCount failed. " + (detail ?: "")
            )
        }
    }

    fun testArchive(entry: StorageEntry) {
        selectedEntry = null
        coroutineScope.launch {
            try {
                val result: ZipArchiveTestResult = withContext(Dispatchers.IO) {
                    fileOperations.testZip(state.accessMode, state.treeUriString, entry)
                }
                showMessage(
                    "ZIP test passed: " + result.fileCount + " files, " +
                        result.directoryCount + " folders, " + formatBytes(result.expandedBytes) + " expanded."
                )
            } catch (error: Exception) {
                showMessage(error.message ?: "ZIP test failed.")
            }
        }
    }

    fun extractArchiveHere(entry: StorageEntry) {
        selectedEntry = null
        val destination = state.currentPath.lastOrNull() ?: return
        if (state.accessMode == StorageAccessMode.SHIZUKU_STORAGE) {
            showMessage("Extraction is disabled in read-only Shizuku mode.")
            return
        }
        coroutineScope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    fileOperations.extractZipHere(
                        state.accessMode, state.treeUriString, entry,
                        destination.documentId, destination.absolutePath
                    )
                }
                filesViewModel.refresh()
                showMessage(message)
            } catch (error: Exception) {
                showMessage(error.message ?: "Couldn't extract this ZIP here.")
            }
        }
    }

    fun extractArchive(entry: StorageEntry) {
        selectedEntry = null
        val destination = state.currentPath.lastOrNull() ?: return
        if (state.accessMode == StorageAccessMode.SHIZUKU_STORAGE) {
            showMessage("Extraction is disabled in read-only Shizuku mode.")
            return
        }
        coroutineScope.launch {
            try {
                val folderName = withContext(Dispatchers.IO) {
                    fileOperations.extractZipToFolder(
                        state.accessMode, state.treeUriString, entry,
                        destination.documentId, destination.absolutePath
                    )
                }
                filesViewModel.refresh()
                showMessage("Extracted to " + folderName + "/")
            } catch (error: Exception) {
                showMessage(error.message ?: "Couldn't extract this ZIP.")
            }
        }
    }

    BackHandler(enabled = multiSelectMode || searchExpanded || state.currentPath.size > 1) {
        when {
            multiSelectMode -> {
                multiSelectMode = false
                filesViewModel.clearSelection()
            }
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            else -> filesViewModel.navigateBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF020B18), Color(0xFF03172D), Color(0xFF020B18))))
    ) {
        // Keep the blue toolbar background behind the invisible status-bar area.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility)
                .background(Brush.verticalGradient(listOf(Color(0xFF061B36), Color(0xFF020B18))))
        )

        // Reserve hidden system-bar dimensions for controls, without showing either system bar.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        ) {
        when {
            state.accessMode == StorageAccessMode.NONE -> {
                StorageAccessRequiredState(
                    onRequestStorageAccess = ::requestStorageAccess,
                    onChooseFolder = ::chooseFolder,
                    shizukuConnected = shizukuStatus.isConnected,
                    onOpenShizuku = ::openShizukuBrowser
                )
            }

            state.currentPath.isEmpty() && state.isLoading -> {
                LoadingState()
            }

            state.currentPath.isEmpty() -> {
                ErrorState(
                    message = state.errorMessage ?: "The selected location could not be opened.",
                    onRetry = filesViewModel::refresh,
                    onChooseFolder = ::chooseFolder,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                Column(Modifier.fillMaxSize()) {
                    val currentLocation = state.currentPath.last()
                    val pathText = currentLocation.absolutePath
                        ?: state.currentPath.joinToString("/") { it.displayName }
                    FileBrowserToolbar(
                        title = if (multiSelectMode) "${selectedEntryIds.size} selected" else currentLocation.displayName,
                        path = if (multiSelectMode) "Tap items to select or deselect" else pathText,
                        quickAccessLocations = buildQuickAccessLocations(state, cetShortcuts),
                        onSelectQuickAccess = filesViewModel::navigateToQuickAccess,
                        onRemoveCetShortcut = filesViewModel::removeCetShortcut,
                        onSaveCurrentFolderAsCet = {
                            val added = filesViewModel.saveCurrentFolderAsCet()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(if (added) "Saved to CET quick access." else "This folder is already in CET.")
                            }
                        },
                        searchExpanded = searchExpanded,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onToggleSearch = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) searchQuery = ""
                        },
                        viewMode = viewMode,
                        onViewModeChange = { viewMode = it },
                        showHiddenFiles = showHiddenFiles,
                        onToggleHiddenFiles = { showHiddenFiles = !showHiddenFiles },
                        sortOrder = sortOrder,
                        onSortOrderChange = { sortOrder = it },
                        sortDescending = sortDescending,
                        onToggleSortDirection = { sortDescending = !sortDescending },
                        selectionMode = multiSelectMode,
                        selectedCount = selectedEntries.size,
                        selectionHasItems = selectedEntries.isNotEmpty(),
                        selectedItemsAreArchives = selectedItemsAreArchives,
                        visibleItemCount = visibleEntries.size,
                        canMutateFiles = canMutateFiles,
                        clipboardPresent = clipboard != null,
                        canPaste = clipboard?.let {
                            it.entries.isNotEmpty() &&
                                it.accessMode == state.accessMode &&
                                it.treeUriString == state.treeUriString &&
                                state.accessMode != StorageAccessMode.SHIZUKU_STORAGE &&
                                canMutateFiles
                        } == true,
                        needsSafWriteGrant = state.accessMode == StorageAccessMode.DOCUMENT_TREE &&
                            state.treeUriString?.let { value ->
                                context.contentResolver.persistedUriPermissions.any {
                                    it.uri == Uri.parse(value) && it.isWritePermission
                                }
                            } != true,
                        shizukuConnected = shizukuStatus.isConnected,
                        canNavigateBack = state.currentPath.size > 1,
                        onBack = {
                            if (multiSelectMode) {
                                multiSelectMode = false
                                filesViewModel.clearSelection()
                            } else if (state.currentPath.size > 1) {
                                filesViewModel.navigateBack()
                            }
                        },
                        onToggleMultiSelect = {
                            multiSelectMode = !multiSelectMode
                            if (!multiSelectMode) filesViewModel.clearSelection()
                            fabExpanded = false
                        },
                        onDeleteSelected = { bulkDeleteConfirmation = true },
                        onCompressSelected = {
                            if (selectedEntries.isNotEmpty()) {
                                compressEntries = selectedEntries
                                compressName = if (selectedEntries.size == 1) defaultZipName(selectedEntries.first()) else "Selected items.zip"
                            }
                        },
                        onExtractSelected = { extractSelectedArchives(selectedEntries) },
                        onTestSelected = { testSelectedArchives(selectedEntries) },
                        onCopySelected = { stageClipboard(selectedEntries, cut = false) },
                        onCutSelected = { stageClipboard(selectedEntries, cut = true) },
                        onSelectAll = { filesViewModel.selectEntries(visibleEntries.map { it.documentId }) },
                        onInvertSelection = { filesViewModel.invertSelection(visibleEntries.map { it.documentId }) },
                        onDeselectAll = { filesViewModel.clearSelection() },
                        onPaste = ::requestPaste,
                        onGrantWriteAccess = ::chooseFolder,
                        onOpenShizuku = ::openShizukuBrowser,
                        onOpenSettings = onOpenSettings,
                        onAbout = { aboutDialogVisible = true }
                    )
                    BrowserState(
                        modifier = Modifier.weight(1f),
                        state = state,
                        filesViewModel = filesViewModel,
                        customFolderIconUris = customFolderIconUris,
                        onChooseFolder = ::chooseFolder,
                        entries = visibleEntries,
                        viewMode = viewMode,
                        selectedEntryIds = selectedEntryIds,
                        selectionMode = multiSelectMode,
                        onOpenEntry = { entry ->
                            if (multiSelectMode) toggleSelectedEntry(entry)
                            else if (entry.isDirectory) filesViewModel.navigateInto(entry)
                            else openFile(entry)
                        },
                        onLongPressEntry = { entry ->
                            if (multiSelectMode) toggleSelectedEntry(entry)
                            else selectedEntry = entry
                        },
                        onRetry = filesViewModel::refresh,
                        searchActive = searchQuery.isNotBlank() || (!showHiddenFiles && state.entries.any { it.displayName.startsWith(".") })
                    )
                }
            }
        }

        if (state.currentPath.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (fabExpanded && canCreateItems) {
                    FloatingActionButton(
                        onClick = { beginCreate(CreateItemKind.FOLDER) },
                        modifier = Modifier.size(48.dp),
                        containerColor = Color(0xFF2492F4),
                        contentColor = Color.White
                    ) {
                        Icon(
                            Icons.Filled.CreateNewFolder,
                            contentDescription = "Create folder"
                        )
                    }
                    FloatingActionButton(
                        onClick = { beginCreate(CreateItemKind.ZIP) },
                        modifier = Modifier.size(48.dp),
                        containerColor = Color(0xFF2492F4),
                        contentColor = Color.White
                    ) {
                        Icon(
                            Icons.Filled.Archive,
                            contentDescription = "Create ZIP archive"
                        )
                    }
                    FloatingActionButton(
                        onClick = { beginCreate(CreateItemKind.FILE) },
                        modifier = Modifier.size(48.dp),
                        containerColor = Color(0xFF2492F4),
                        contentColor = Color.White
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = "Create text file"
                        )
                    }
                }
                FloatingActionButton(
                    onClick = {
                        if (canCreateItems) {
                            fabExpanded = !fabExpanded
                        } else {
                            showMessage("Choose a writable SAF/shared-storage location or connect and authorize Shizuku.")
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    containerColor = Color(0xFF2492F4),
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = if (fabExpanded) "Close create actions" else "Create"
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        selectedEntry?.let { entry ->
            val canMutateFiles = when (state.accessMode) {
                StorageAccessMode.SHARED_STORAGE -> true
                StorageAccessMode.DOCUMENT_TREE -> state.treeUriString?.let { value ->
                    context.contentResolver.persistedUriPermissions.any {
                        it.uri == Uri.parse(value) && it.isWritePermission
                    }
                } == true
                else -> false
            }
            ModalBottomSheet(onDismissRequest = { selectedEntry = null }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        entry.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    ContextActionRow("Open / View", Icons.Filled.FolderOpen) {
                        selectedEntry = null
                        if (entry.isDirectory) filesViewModel.navigateInto(entry) else openFile(entry)
                    }
                    if (entry.isDirectory) {
                        ContextActionRow("Set custom folder image", Icons.Filled.Image) {
                            folderIconTarget = entry to state
                            selectedEntry = null
                            folderIconPicker.launch(arrayOf("image/*"))
                        }
                        if (customFolderIconUris.containsKey(folderIconKey(state, entry))) {
                            ContextActionRow("Reset custom folder image", Icons.Filled.Close) {
                                filesViewModel.setCustomFolderIcon(entry, state, null)
                                selectedEntry = null
                            }
                        }
                    }
                    ContextActionRow("Information", Icons.Filled.Info) {
                        selectedEntry = null
                        informationEntry = entry
                    }
                    if (!entry.isDirectory) {
                        ContextActionRow("Share", Icons.Filled.Share) { shareSelected(entry) }
                    }
                    if (canMutateFiles) {
                        ContextActionRow("Copy", Icons.Filled.ContentCopy) { stageClipboard(listOf(entry), false) }
                        ContextActionRow("Cut", Icons.Filled.ContentCut) { stageClipboard(listOf(entry), true) }
                        ContextActionRow("Rename", Icons.Filled.Edit) {
                            selectedEntry = null
                            renameName = entry.displayName
                            renameEntry = entry
                        }
                        ContextActionRow("Delete", Icons.Filled.Delete) {
                            selectedEntry = null
                            deleteEntry = entry
                        }
                        ContextActionRow("Compress…", Icons.Filled.Archive) {
                            selectedEntry = null
                            compressName = defaultZipName(entry)
                            compressEntries = listOf(entry)
                        }
                    }
                    if (!entry.isDirectory && entry.displayName.lowercase(Locale.ROOT).endsWith(".zip")) {
                        ContextActionRow("Test archive", Icons.Filled.Archive) { testArchive(entry) }
                        if (canMutateFiles) {
                            ContextActionRow("Extract here", Icons.Filled.Archive) { extractArchiveHere(entry) }
                            ContextActionRow("Extract to folder", Icons.Filled.FolderOpen) { extractArchive(entry) }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        informationEntry?.let { entry ->
            val info = buildString {
                append("Name: ").append(entry.displayName).append('\n')
                append("Type: ").append(if (entry.isDirectory) "Folder" else entry.mimeType ?: "Unknown").append('\n')
                append("Size: ").append(if (entry.isDirectory) "—" else entry.sizeBytes?.let { formatBytes(it) } ?: "Unknown").append('\n')
                entry.lastModifiedMillis?.let {
                    append("Modified: ").append(SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(it))).append('\n')
                }
                append("Location: ").append(entry.absolutePath ?: entry.documentId)
            }
            AlertDialog(
                onDismissRequest = { informationEntry = null },
                title = { Text("Information") },
                text = { Text(info) },
                confirmButton = {
                    TextButton(onClick = { informationEntry = null }) { Text("Close") }
                }
            )
        }

        compressEntries?.let { entries ->
            AlertDialog(
                onDismissRequest = { compressEntries = null },
                title = { Text("Compress to ZIP") },
                text = {
                    Column {
                        Text(
                            "Create a ZIP copy of " + entries.joinToString(", ") { it.displayName } + " in the current folder. The original items will not be changed.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = compressName,
                            onValueChange = { compressName = it },
                            label = { Text("Archive name") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { compressSelected(entries, compressName) },
                        enabled = compressName.isNotBlank()
                    ) { Text("Compress") }
                },
                dismissButton = {
                    TextButton(onClick = { compressEntries = null }) { Text("Cancel") }
                }
            )
        }

        renameEntry?.let { entry ->
            AlertDialog(
                onDismissRequest = { renameEntry = null },
                title = { Text("Rename item") },
                text = {
                    OutlinedTextField(
                        value = renameName,
                        onValueChange = { renameName = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = { renameSelected(entry, renameName) }) { Text("Rename") }
                },
                dismissButton = {
                    TextButton(onClick = { renameEntry = null }) { Text("Cancel") }
                }
            )
        }

        deleteEntry?.let { entry ->
            AlertDialog(
                onDismissRequest = { deleteEntry = null },
                title = { Text("Delete " + entry.displayName + "?") },
                text = {
                    Text(
                        if (entry.isDirectory) "This removes the folder and its contents. This cannot be undone."
                        else "This permanently deletes the selected file. This cannot be undone."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { deleteSelected(entry) }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteEntry = null }) { Text("Cancel") }
                }
            )
        }

        pasteConflict?.let { conflict ->
            AlertDialog(
                onDismissRequest = { pasteConflict = null },
                title = { Text(if (conflict.existing.size == 1) "An item already exists" else "Items already exist") },
                text = {
                    Text(
                        if (conflict.existing.size == 1) {
                        "A destination item named '" + conflict.existing.first().displayName +
                            "' already exists. Choose Keep both to create a unique name, or Replace to overwrite it."
                    } else {
                        "${conflict.existing.size} destination items already exist (for example, '" +
                            conflict.existing.take(3).joinToString("', '") { it.displayName } +
                            "'). Choose Keep both to create unique names, or Replace to overwrite the conflicts."
                    }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { executePaste(conflict.clipboard, keepBoth = true, replaceExisting = false) }) {
                        Text("Keep both")
                    }
                },
                dismissButton = {
                    Row {
                        if (!conflict.sameSource) {
                            TextButton(onClick = { executePaste(conflict.clipboard, keepBoth = false, replaceExisting = true) }) {
                                Text("Replace")
                            }
                        }
                        TextButton(onClick = { pasteConflict = null }) { Text("Cancel") }
                    }
                }
            )
        }

        aboutDialogVisible.let { show ->
            if (show) {
                AlertDialog(
                    onDismissRequest = { aboutDialogVisible = false },
                    title = { Text("JarVision") },
                    text = {
                        Text(
                            "JarVision 0.7.0\nA compact Android file manager with local file operations and ZIP tools. Shizuku supports creation of new folders, empty files, and empty ZIPs where permissions allow; existing items remain read-only."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { aboutDialogVisible = false }) { Text("Close") }
                    }
                )
            }
        }

        createKind?.let { kind ->
            AlertDialog(
                onDismissRequest = { createKind = null },
                title = {
                    Text(
                        when (kind) {
                            CreateItemKind.FOLDER -> "Create folder"
                            CreateItemKind.FILE -> "Create file"
                            CreateItemKind.ZIP -> "Create ZIP archive"
                        }
                    )
                },
                text = {
                    Column {
                        Text(
                            when (kind) {
                                CreateItemKind.FOLDER -> "Create a new folder in the current location."
                                CreateItemKind.FILE -> "Create an empty file in the current location."
                                CreateItemKind.ZIP -> "Create an empty ZIP archive in the current location."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(10.dp))
                        TextField(
                            value = createName,
                            onValueChange = { createName = it },
                            singleLine = true,
                            label = {
                                Text(
                                    when (kind) {
                                        CreateItemKind.FOLDER -> "Folder name"
                                        CreateItemKind.FILE -> "File name"
                                        CreateItemKind.ZIP -> "Archive name"
                                    }
                                )
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { createCurrentItem(kind, createName) },
                        enabled = createName.isNotBlank()
                    ) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { createKind = null }) { Text("Cancel") }
                }
            )
        }

        if (bulkDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { bulkDeleteConfirmation = false },
                title = { Text("Delete ${selectedEntries.size} items?") },
                text = {
                    Text("This permanently deletes the selected files and folders. This action cannot be undone.")
                },
                confirmButton = {
                    TextButton(onClick = ::deleteSelectedBatch) { Text("Delete selected") }
                },
                dismissButton = {
                    TextButton(onClick = { bulkDeleteConfirmation = false }) { Text("Cancel") }
                }
            )
        }

        previewContent?.let { (fileName, textContent) ->
            AlertDialog(
                onDismissRequest = { previewContent = null },
                title = { Text("$fileName · Read-only") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = textContent,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { previewContent = null }) {
                        Text("Close")
                    }
                }
            )
        }
        }
    }
}

@Composable
private fun StorageAccessRequiredState(
    onRequestStorageAccess: () -> Unit,
    onChooseFolder: () -> Unit,
    shizukuConnected: Boolean,
    onOpenShizuku: () -> Unit
) {
    val isManageAllFilesSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val needsLegacyRuntimePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    val primaryLabel = when {
        isManageAllFilesSupported -> "Allow access to all files"
        needsLegacyRuntimePermission -> "Grant storage permission"
        else -> "Choose storage folder"
    }
    val description = when {
        isManageAllFilesSupported ->
            "JarVision needs storage access to show your real files and folders. Android will open a system settings page where you can allow access to manage all files."
        needsLegacyRuntimePermission ->
            "Allow storage permission so JarVision can browse shared internal storage, or choose one folder instead."
        else ->
            "On this Android version, choose a folder to give JarVision access to its contents."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(68.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Storage access required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        Button(onClick = onRequestStorageAccess) {
            Text(primaryLabel)
        }
        if (isManageAllFilesSupported || needsLegacyRuntimePermission) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onChooseFolder) {
                Text("Choose a folder instead")
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onOpenShizuku) {
            Text(if (shizukuConnected) "Browse protected folders" else "Connect Shizuku")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Android still protects private data belonging to other apps. Storage access does not bypass those system restrictions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun buildQuickAccessLocations(
    state: FilesUiState,
    cetShortcuts: List<QuickAccessLocation>
): List<QuickAccessLocation> {
    val root = state.currentPath.firstOrNull() ?: return cetShortcuts
    val builtIns = when (state.accessMode) {
        StorageAccessMode.SHARED_STORAGE, StorageAccessMode.SHIZUKU_STORAGE -> {
            val rootPath = (root.absolutePath ?: root.documentId).trimEnd('/')
            val defaults = listOf(
                "" to "Device storage",
                "Download" to "Download"
            )
            defaults.map { (relative, label) ->
                val chain = mutableListOf(root)
                var current = rootPath
                if (relative.isNotBlank()) {
                    relative.split('/').forEach { part ->
                        current = "$current/$part"
                        chain += FolderLocation(documentId = current, displayName = part, absolutePath = current)
                    }
                }
                QuickAccessLocation(
                    id = "builtin:${state.accessMode.name}:$current",
                    label = label,
                    accessMode = state.accessMode,
                    treeUriString = state.treeUriString,
                    path = chain,
                    isCustom = false
                )
            }
        }
        StorageAccessMode.DOCUMENT_TREE -> listOf(
            QuickAccessLocation(
                id = "builtin:tree-root:${state.treeUriString.orEmpty()}",
                label = "Selected folder",
                accessMode = state.accessMode,
                treeUriString = state.treeUriString,
                path = listOf(root),
                isCustom = false
            )
        )
        StorageAccessMode.NONE -> emptyList()
    }
    return builtIns + cetShortcuts
}

private fun quickAccessIcon(label: String): ImageVector = when (label.lowercase(Locale.ROOT)) {
    "device storage", "selected folder", "internal storage" -> Icons.Filled.FolderOpen
    "download", "downloads" -> Icons.Filled.Download
    "music", "alarms", "notifications", "ringtones", "podcasts" -> Icons.Filled.MusicNote
    "dcim" -> Icons.Filled.CameraAlt
    "pictures", "photos", "screenshots" -> Icons.Filled.Image
    "movies", "videos" -> Icons.Filled.Movie
    "documents" -> Icons.Filled.Description
    else -> Icons.Filled.Folder
}

@Composable
private fun FileBrowserToolbar(
    title: String,
    path: String,
    quickAccessLocations: List<QuickAccessLocation>,
    onSelectQuickAccess: (QuickAccessLocation) -> Unit,
    onRemoveCetShortcut: (String) -> Unit,
    searchExpanded: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    viewMode: BrowserViewMode,
    onViewModeChange: (BrowserViewMode) -> Unit,
    showHiddenFiles: Boolean,
    onToggleHiddenFiles: () -> Unit,
    sortOrder: BrowserSortOrder,
    onSortOrderChange: (BrowserSortOrder) -> Unit,
    sortDescending: Boolean,
    onToggleSortDirection: () -> Unit,
    selectionMode: Boolean,
    selectedCount: Int,
    selectionHasItems: Boolean,
    selectedItemsAreArchives: Boolean,
    visibleItemCount: Int,
    canMutateFiles: Boolean,
    clipboardPresent: Boolean,
    canPaste: Boolean,
    needsSafWriteGrant: Boolean,
    shizukuConnected: Boolean,
    canNavigateBack: Boolean,
    onBack: () -> Unit,
    onToggleMultiSelect: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCompressSelected: () -> Unit,
    onExtractSelected: () -> Unit,
    onTestSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onCutSelected: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onDeselectAll: () -> Unit,
    onPaste: () -> Unit,
    onGrantWriteAccess: () -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenSettings: () -> Unit,
    onAbout: () -> Unit,
    onSaveCurrentFolderAsCet: () -> Unit
) {
    var pathMenuExpanded by remember { mutableStateOf(false) }
    var toolbarHeightPx by remember { mutableStateOf(0) }
    var viewMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { toolbarHeightPx = it.height }
    ) {
        val toolbarWidth = maxWidth
        val compactActions = maxWidth < 380.dp
        val veryCompact = maxWidth < 280.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF082447), Color(0xFF06172D), Color(0xFF082447))))
                .border(BorderStroke(1.dp, Color(0xFF1D4A78).copy(alpha = 0.78f)), RoundedCornerShape(20.dp))
                .padding(horizontal = 5.dp, vertical = 3.dp)
                .heightIn(min = 58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = selectionMode || canNavigateBack) {
                Icon(
                    imageVector = if (selectionMode) Icons.Filled.Close else Icons.Filled.ArrowBack,
                    contentDescription = if (selectionMode) "Exit selection mode" else "Navigate back",
                    tint = Color.White
                )
            }

            Column(modifier = Modifier.weight(1f).widthIn(min = 0.dp)) {
                if (searchExpanded) {
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 6.dp)
                            .heightIn(min = 48.dp, max = 56.dp),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        placeholder = { Text("Search this folder") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF9DCEFF),
                            focusedPlaceholderColor = Color(0xFFB6D7F4),
                            unfocusedPlaceholderColor = Color(0xFF9FBFE0),
                            focusedContainerColor = Color(0xFF174777),
                            unfocusedContainerColor = Color(0xFF10365F),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { pathMenuExpanded = true }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = path,
                            color = Color.White.copy(alpha = 0.76f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (clipboardPresent && !compactActions) {
                TextButton(onClick = onPaste, enabled = canPaste) {
                    Text("Paste", color = Color.White)
                }
            }

            if (!veryCompact) {
                IconButton(
                    onClick = onToggleSearch,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0B2C4D).copy(alpha = 0.86f))
                ) {
                    Icon(
                        imageVector = if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = if (searchExpanded) "Close search" else "Search",
                        tint = Color.White
                    )
                }
            }

            if (!veryCompact) {
            Box {
                IconButton(
                    onClick = { viewMenuExpanded = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0B2C4D).copy(alpha = 0.86f))
                ) {
                    Icon(Icons.Filled.ViewList, contentDescription = "View and sort options", tint = Color.White)
                }
                DropdownMenu(
                    expanded = viewMenuExpanded,
                    onDismissRequest = { viewMenuExpanded = false },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = Color(0xFF071B32),
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, Color(0xFF1D5A96).copy(alpha = 0.9f))
                ) {
                    DropdownMenuItem(
                        text = { Text("Compact") },
                        leadingIcon = { Icon(Icons.Filled.ViewList, contentDescription = null) },
                        trailingIcon = {
                            if (viewMode == BrowserViewMode.COMPACT) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            onViewModeChange(BrowserViewMode.COMPACT)
                            viewMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Detailed") },
                        leadingIcon = { Icon(Icons.Filled.ViewList, contentDescription = null) },
                        trailingIcon = {
                            if (viewMode == BrowserViewMode.DETAILED) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            onViewModeChange(BrowserViewMode.DETAILED)
                            viewMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Grid") },
                        leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null) },
                        trailingIcon = {
                            if (viewMode == BrowserViewMode.GRID) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            onViewModeChange(BrowserViewMode.GRID)
                            viewMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (showHiddenFiles) "Hide hidden files" else "Show hidden files") },
                        onClick = {
                            onToggleHiddenFiles()
                            viewMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Sort by…") },
                        leadingIcon = { Icon(Icons.Filled.Sort, contentDescription = null) },
                        onClick = {
                            viewMenuExpanded = false
                            sortMenuExpanded = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (sortDescending) "Order: Descending" else "Order: Ascending") },
                        onClick = {
                            onToggleSortDirection()
                            viewMenuExpanded = false
                        }
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = Color(0xFF071B32),
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, Color(0xFF1D5A96).copy(alpha = 0.9f))
                ) {
                    BrowserSortOrder.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }) },
                            trailingIcon = {
                                if (sortOrder == option) Icon(Icons.Filled.Check, contentDescription = null)
                            },
                            onClick = {
                                onSortOrderChange(option)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }

            }

            Box {
                IconButton(
                    onClick = { overflowExpanded = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0B2C4D).copy(alpha = 0.86f))
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = Color.White)
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = Color(0xFF071B32),
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, Color(0xFF1D5A96).copy(alpha = 0.9f))
                ) {
                    if (selectionMode) {
                        DropdownMenuItem(text = { Text("Compress…") },
                            leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                            enabled = selectionHasItems && canMutateFiles,
                            onClick = { overflowExpanded = false; onCompressSelected() })
                        if (selectedItemsAreArchives) {
                            DropdownMenuItem(text = { Text("Extract…") },
                                leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                                enabled = canMutateFiles,
                                onClick = { overflowExpanded = false; onExtractSelected() })
                            DropdownMenuItem(text = { Text("Test") },
                                leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                                onClick = { overflowExpanded = false; onTestSelected() })
                        }
                        DropdownMenuItem(text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            enabled = selectionHasItems && canMutateFiles,
                            onClick = { overflowExpanded = false; onCopySelected() })
                        DropdownMenuItem(text = { Text("Cut") },
                            leadingIcon = { Icon(Icons.Filled.ContentCut, contentDescription = null) },
                            enabled = selectionHasItems && canMutateFiles,
                            onClick = { overflowExpanded = false; onCutSelected() })
                        DropdownMenuItem(text = { Text("Paste") }, enabled = canPaste,
                            onClick = { overflowExpanded = false; onPaste() })
                        DropdownMenuItem(text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            enabled = selectionHasItems && canMutateFiles,
                            onClick = { overflowExpanded = false; onDeleteSelected() })
                        DropdownMenuItem(text = { Text("Select all") }, enabled = visibleItemCount > 0,
                            onClick = { overflowExpanded = false; onSelectAll() })
                        DropdownMenuItem(text = { Text("Invert selection") }, enabled = visibleItemCount > 0,
                            onClick = { overflowExpanded = false; onInvertSelection() })
                        DropdownMenuItem(text = { Text("Deselect all") }, enabled = selectedCount > 0,
                            onClick = { overflowExpanded = false; onDeselectAll() })
                    }
                    if (!selectionMode && clipboardPresent && compactActions) {
                        DropdownMenuItem(
                            text = { Text("Paste") },
                            enabled = canPaste,
                            onClick = {
                                overflowExpanded = false
                                onPaste()
                            }
                        )
                    }
                    if (veryCompact) {
                        DropdownMenuItem(
                            text = { Text(if (searchExpanded) "Close search" else "Search") },
                            leadingIcon = {
                                Icon(
                                    if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                onToggleSearch()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("View: Compact") },
                            leadingIcon = { Icon(Icons.Filled.ViewList, contentDescription = null) },
                            trailingIcon = {
                                if (viewMode == BrowserViewMode.COMPACT) Icon(Icons.Filled.Check, contentDescription = null)
                            },
                            onClick = {
                                overflowExpanded = false
                                onViewModeChange(BrowserViewMode.COMPACT)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("View: Detailed") },
                            leadingIcon = { Icon(Icons.Filled.ViewList, contentDescription = null) },
                            trailingIcon = {
                                if (viewMode == BrowserViewMode.DETAILED) Icon(Icons.Filled.Check, contentDescription = null)
                            },
                            onClick = {
                                overflowExpanded = false
                                onViewModeChange(BrowserViewMode.DETAILED)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("View: Grid") },
                            leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null) },
                            trailingIcon = {
                                if (viewMode == BrowserViewMode.GRID) Icon(Icons.Filled.Check, contentDescription = null)
                            },
                            onClick = {
                                overflowExpanded = false
                                onViewModeChange(BrowserViewMode.GRID)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (showHiddenFiles) "Hide hidden files" else "Show hidden files") },
                            onClick = {
                                overflowExpanded = false
                                onToggleHiddenFiles()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by: Name") },
                            onClick = {
                                overflowExpanded = false
                                onSortOrderChange(BrowserSortOrder.NAME)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by: Size") },
                            onClick = {
                                overflowExpanded = false
                                onSortOrderChange(BrowserSortOrder.SIZE)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by: Date") },
                            onClick = {
                                overflowExpanded = false
                                onSortOrderChange(BrowserSortOrder.DATE)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by: Type") },
                            onClick = {
                                overflowExpanded = false
                                onSortOrderChange(BrowserSortOrder.TYPE)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (sortDescending) "Order: Descending" else "Order: Ascending") },
                            onClick = {
                                overflowExpanded = false
                                onToggleSortDirection()
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text(if (selectionMode) "Stop multi-select" else "Multi-select") },
                        onClick = {
                            overflowExpanded = false
                            onToggleMultiSelect()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("CET · Save current folder") },
                        leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                        onClick = {
                            overflowExpanded = false
                            onSaveCurrentFolderAsCet()
                        }
                    )
                    if (needsSafWriteGrant) {
                        DropdownMenuItem(
                            text = { Text("Grant write access") },
                            onClick = {
                                overflowExpanded = false
                                onGrantWriteAccess()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (shizukuConnected) "Browse protected folders" else "Connect Shizuku") },
                        onClick = {
                            overflowExpanded = false
                            onOpenShizuku()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            overflowExpanded = false
                            onOpenSettings()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("About") },
                        onClick = {
                            overflowExpanded = false
                            onAbout()
                        }
                    )
                }
            }
        }

        if (pathMenuExpanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = 0, y = toolbarHeightPx),
                onDismissRequest = { pathMenuExpanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier.width(toolbarWidth),
                    shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                    color = Color(0xFF071B32),
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Color(0xFF397AB5))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        quickAccessLocations.forEach { location ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        pathMenuExpanded = false
                                        onSelectQuickAccess(location)
                                    }
                                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = quickAccessIcon(location.label),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = location.label,
                                    modifier = Modifier.weight(1f),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (location.isCustom) {
                                    IconButton(
                                        onClick = { onRemoveCetShortcut(location.id) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove CET shortcut",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserState(
    modifier: Modifier = Modifier,
    state: FilesUiState,
    filesViewModel: FilesViewModel,
    customFolderIconUris: Map<String, String>,
    onChooseFolder: () -> Unit,
    entries: List<StorageEntry>,
    viewMode: BrowserViewMode,
    selectedEntryIds: Set<String>,
    selectionMode: Boolean,
    onOpenEntry: (StorageEntry) -> Unit,
    onLongPressEntry: (StorageEntry) -> Unit,
    onRetry: () -> Unit,
    searchActive: Boolean
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        if (state.accessMode == StorageAccessMode.SHIZUKU_STORAGE) {
            Text(
                text = "CREATE-ONLY · Shizuku",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            state.errorMessage != null -> {
                ErrorState(
                    message = state.errorMessage,
                    onRetry = onRetry,
                    onChooseFolder = onChooseFolder,
                    modifier = Modifier.weight(1f)
                )
            }

            entries.isEmpty() && state.entries.isNotEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchActive) "No matching items" else "No visible items",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            entries.isEmpty() && state.currentPath.size <= 1 -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("This folder is empty", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Items you can access will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            viewMode == BrowserViewMode.GRID -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 76.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    gridItems(entries, key = { it.documentId }) { entry ->
                        GridFileEntry(
                            entry = entry,
                            state = state,
                            filesViewModel = filesViewModel,
                            customFolderIconUris = customFolderIconUris,
                            selected = entry.documentId in selectedEntryIds,
                            selectionMode = selectionMode,
                            onClick = { onOpenEntry(entry) },
                            onLongClick = { onLongPressEntry(entry) }
                        )
                    }
                }
                Text(
                    text = "${entries.size} items",
                    modifier = Modifier.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 76.dp, top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(if (viewMode == BrowserViewMode.COMPACT) 3.dp else 5.dp)
                ) {
                    items(entries, key = { it.documentId }) { entry ->
                        FileEntryRow(
                            entry = entry,
                            state = state,
                            filesViewModel = filesViewModel,
                            customFolderIconUris = customFolderIconUris,
                            viewMode = viewMode,
                            selected = entry.documentId in selectedEntryIds,
                            selectionMode = selectionMode,
                            onClick = { onOpenEntry(entry) },
                            onLongClick = { onLongPressEntry(entry) }
                        )
                    }
                }
                Text(
                    text = "${entries.size} items",
                    modifier = Modifier.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun FolderThumbnail(entry: StorageEntry, state: FilesUiState, thumbnailSize: Dp = 48.dp, customThumbnailUri: String? = null) {
    val context = LocalContext.current
    val thumbnailModel by produceState<Any?>(
        initialValue = null,
        key1 = listOf(entry.documentId, state.accessMode.name, state.treeUriString.orEmpty(), customThumbnailUri.orEmpty()).joinToString("|")
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (!customThumbnailUri.isNullOrBlank()) Uri.parse(customThumbnailUri)
                else when (state.accessMode) {
                    StorageAccessMode.SHARED_STORAGE -> entry.absolutePath?.let {
                        SharedStorageGateway().findFolderThumbnailFile(it)
                    }
                    StorageAccessMode.DOCUMENT_TREE -> {
                        val treeUri = state.treeUriString?.let { Uri.parse(it) }
                        if (treeUri == null) null else SafStorageGateway(context.contentResolver)
                            .findFolderThumbnailUri(treeUri, entry.documentId)
                    }
                    StorageAccessMode.SHIZUKU_STORAGE -> entry.absolutePath?.let {
                        ShizukuStorageAdapter.get(context).loadFolderThumbnail(it)
                    }
                    StorageAccessMode.NONE -> null
                }
            }.getOrNull()
        }
    }
    var imageFailed by remember(entry.documentId, thumbnailModel) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier.size(thumbnailSize).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailModel != null && !imageFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbnailModel).crossfade(true).build(),
                contentDescription = "Thumbnail for ${entry.displayName}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
                onError = { imageFailed = true }
            )
        } else {
            Icon(imageVector = fallbackFolderImage(entry.displayName, entry.absolutePath), contentDescription = "Folder",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (thumbnailSize <= 36.dp) 20.dp else 28.dp))
        }
    }
}

private fun fallbackFolderImage(folderName: String, absolutePath: String? = null): ImageVector {
    val name = folderName.trim().lowercase(Locale.ROOT)
    val path = absolutePath.orEmpty().replace('\\', '/').lowercase(Locale.ROOT)
    return when {
        name == "dcim" || path.endsWith("/dcim") -> Icons.Filled.CameraAlt
        "download" in name || name == "downloads" -> Icons.Filled.Download
        name == "music" || name == "podcasts" || name == "ringtones" || name == "alarms" || name == "notifications" -> Icons.Filled.MusicNote
        name == "movies" || name == "videos" || name == "video" -> Icons.Filled.Movie
        name == "pictures" || name == "photos" || name == "screenshots" || name == "wallpapers" || name == "images" -> Icons.Filled.Image
        name == "documents" || name == "document" || name == "books" -> Icons.Filled.Description
        name == "android" || name == "data" || name == "obb" -> Icons.Filled.Android
        name.endsWith(".zip") || name.endsWith(".7z") || name == "archives" -> Icons.Filled.Archive
        else -> Icons.Filled.Folder
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileEntryRow(
    entry: StorageEntry,
    state: FilesUiState,
    filesViewModel: FilesViewModel,
    customFolderIconUris: Map<String, String>,
    viewMode: BrowserViewMode,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val compact = viewMode == BrowserViewMode.COMPACT
    val thumbnailSize = if (compact) 32.dp else 38.dp
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(if (compact) 11.dp else 15.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.19f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (compact) 0.28f else 0.42f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 9.dp else 12.dp,
                vertical = if (compact) 5.dp else 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileEntryVisualIcon(
                entry = entry,
                state = state,
                filesViewModel = filesViewModel,
                customFolderIconUris = customFolderIconUris,
                thumbnailSize = thumbnailSize
            )
            Spacer(Modifier.size(if (compact) 10.dp else 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                val details = entryDetails(entry)
                if (details.isNotBlank()) {
                    Spacer(Modifier.height(if (compact) 0.dp else 2.dp))
                    Text(
                        text = details,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            when {
                selectionMode && selected -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp).size(22.dp)
                )
                selectionMode -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Not selected",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.padding(start = 6.dp).size(22.dp)
                )
                entry.isDirectory -> Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridFileEntry(
    entry: StorageEntry,
    state: FilesUiState,
    filesViewModel: FilesViewModel,
    customFolderIconUris: Map<String, String>,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.19f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FileEntryVisualIcon(
                entry = entry,
                state = state,
                filesViewModel = filesViewModel,
                customFolderIconUris = customFolderIconUris,
                thumbnailSize = 48.dp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (selectionMode && selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ContextActionRow(
    title: String,
    image: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Loading folder…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onChooseFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Can't access this folder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRetry) { Text("Retry") }
            Button(onClick = onChooseFolder) { Text("Choose folder") }
        }
    }
}

private fun defaultZipName(entry: StorageEntry): String {
    val name = entry.displayName
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val base = if (!entry.isDirectory && extension.isNotBlank()) {
        name.substringBeforeLast('.', name)
    } else {
        name
    }
    return if (!entry.isDirectory && extension == "zip") "$base-compressed.zip" else "$base.zip"
}

private fun bestMimeType(entry: StorageEntry): String {
    val existing = entry.mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
    if (existing != null) return existing
    val extension = entry.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
}

private fun isLikelyTextFile(name: String): Boolean {
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return extension in setOf(
        "txt", "md", "json", "xml", "yaml", "yml", "toml", "properties",
        "kt", "kts", "java", "py", "js", "ts", "html", "css", "log",
        "cfg", "ini", "csv", "sh", "gradle", "sql", "c", "h", "cpp", "rs"
    )
}

private fun entryDetails(entry: StorageEntry): String {
    val pieces = mutableListOf<String>()
    if (entry.isDirectory) {
        pieces += "Folder"
    } else {
        entry.sizeBytes?.let { pieces += formatBytes(it) }
        entry.mimeType?.takeIf { it.isNotBlank() }?.let { pieces += it }
    }
    entry.lastModifiedMillis?.let {
        pieces += SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it))
    }
    return pieces.joinToString(" · ")
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L ->
        String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    else ->
        String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
