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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val entry: StorageEntry,
    val accessMode: StorageAccessMode,
    val treeUriString: String?,
    val sourceParentDocumentId: String,
    val sourceParentAbsolutePath: String?,
    val isCut: Boolean
)

private data class PasteConflict(
    val clipboard: PendingFileClipboard,
    val existing: StorageEntry,
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
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by filesViewModel.uiState.collectAsState()
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
    var selectedEntryIds by remember { mutableStateOf(setOf<String>()) }
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

    val canMutateFiles = when (state.accessMode) {
        StorageAccessMode.SHARED_STORAGE -> true
        StorageAccessMode.DOCUMENT_TREE -> state.treeUriString?.let { value ->
            context.contentResolver.persistedUriPermissions.any {
                it.uri == Uri.parse(value) && it.isWritePermission
            }
        } == true
        else -> false
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
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val shizukuAdapter = remember(context) { ShizukuStorageAdapter.get(context) }
    val fileOperations = remember(context, shizukuAdapter) {
        FileOperationCoordinator(context, shizukuAdapter)
    }
    val shizukuStatus by shizukuAdapter.status.collectAsState()
    var shouldOpenShizukuAfterConnect by remember { mutableStateOf(false) }
    var previewContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedEntry by remember { mutableStateOf<StorageEntry?>(null) }
    var informationEntry by remember { mutableStateOf<StorageEntry?>(null) }
    var renameEntry by remember { mutableStateOf<StorageEntry?>(null) }
    var renameName by remember { mutableStateOf("") }
    var compressEntry by remember { mutableStateOf<StorageEntry?>(null) }
    var compressName by remember { mutableStateOf("") }
    var deleteEntry by remember { mutableStateOf<StorageEntry?>(null) }
    var clipboard by remember { mutableStateOf<PendingFileClipboard?>(null) }
    var pasteConflict by remember { mutableStateOf<PasteConflict?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) filesViewModel.selectTree(uri)
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

    fun stageClipboard(entry: StorageEntry, cut: Boolean) {
        val parent = state.currentPath.lastOrNull() ?: return
        if (state.accessMode == StorageAccessMode.SHIZUKU_STORAGE ||
            state.accessMode == StorageAccessMode.NONE
        ) {
            showMessage("Copy and cut are unavailable in this storage mode.")
            selectedEntry = null
            return
        }
        clipboard = PendingFileClipboard(
            entry = entry,
            accessMode = state.accessMode,
            treeUriString = state.treeUriString,
            sourceParentDocumentId = parent.documentId,
            sourceParentAbsolutePath = parent.absolutePath,
            isCut = cut
        )
        selectedEntry = null
        showMessage(if (cut) "Item marked for moving. Navigate to a destination and tap Paste."
            else "Item copied to JarVision's clipboard. Navigate to a destination and tap Paste.")
    }

    fun executePaste(
        pending: PendingFileClipboard,
        keepBoth: Boolean,
        replaceExisting: Boolean
    ) {
        val destination = state.currentPath.lastOrNull() ?: return
        if (pending.accessMode != state.accessMode ||
            pending.treeUriString != state.treeUriString ||
            state.accessMode == StorageAccessMode.SHIZUKU_STORAGE
        ) {
            showMessage("Paste is limited to the same writable storage scope.")
            pasteConflict = null
            return
        }
        if (pending.entry.isDirectory && state.currentPath.any {
                it.documentId == pending.entry.documentId
            }
        ) {
            showMessage("A folder can't be pasted into itself or one of its subfolders.")
            pasteConflict = null
            return
        }
        if (pending.isCut) {
            val sameParent = if (state.accessMode == StorageAccessMode.SHARED_STORAGE) {
                pending.sourceParentAbsolutePath == destination.absolutePath
            } else {
                pending.sourceParentDocumentId == destination.documentId
            }
            if (sameParent) {
                clipboard = null
                pasteConflict = null
                showMessage("This item is already in that folder.")
                return
            }
        }
        coroutineScope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    var targetName = pending.entry.displayName
                    if (keepBoth) {
                        val original = pending.entry.displayName
                        val dot = original.lastIndexOf('.')
                        val base = if (dot > 0) original.substring(0, dot) else original
                        val extension = if (dot > 0) original.substring(dot) else ""
                        var suffix = 1
                        while (fileOperations.findNamedChild(
                                state.accessMode, state.treeUriString,
                                destination.documentId, destination.absolutePath, targetName
                            ) != null
                        ) {
                            targetName = base + " (" + suffix + ")" + extension
                            suffix++
                            if (suffix > 10000) throw IOException("Couldn't find an unused copy name.")
                        }
                    }
                    fileOperations.copyOrMove(
                        mode = state.accessMode,
                        treeUriString = state.treeUriString,
                        source = pending.entry,
                        destinationParentDocumentId = destination.documentId,
                        destinationParentAbsolutePath = destination.absolutePath,
                        targetName = targetName,
                        moveSource = pending.isCut,
                        replaceExisting = replaceExisting
                    )
                }
                clipboard = null
                pasteConflict = null
                filesViewModel.refresh()
                showMessage(message)
            } catch (error: Exception) {
                showMessage(error.message ?: "The file operation failed.")
            }
        }
    }

    fun requestPaste() {
        val pending = clipboard ?: return
        val destination = state.currentPath.lastOrNull() ?: return
        if (pending.accessMode != state.accessMode || pending.treeUriString != state.treeUriString ||
            state.accessMode == StorageAccessMode.SHIZUKU_STORAGE
        ) {
            showMessage("Paste is limited to the same writable storage scope.")
            return
        }
        if (pending.entry.isDirectory && state.currentPath.any { it.documentId == pending.entry.documentId }) {
            showMessage("A folder can't be pasted into itself or one of its subfolders.")
            return
        }
        coroutineScope.launch {
            try {
                val conflict = withContext(Dispatchers.IO) {
                    fileOperations.findNamedChild(
                        state.accessMode, state.treeUriString,
                        destination.documentId, destination.absolutePath,
                        pending.entry.displayName
                    )
                }
                if (conflict != null) {
                    val sameSource = if (state.accessMode == StorageAccessMode.SHARED_STORAGE) {
                        pending.entry.absolutePath == conflict.absolutePath
                    } else {
                        pending.entry.documentId == conflict.documentId
                    }
                    pasteConflict = PasteConflict(
                        clipboard = pending,
                        existing = conflict,
                        destination = destination,
                        sameSource = sameSource
                    )
                } else {
                    executePaste(pending, keepBoth = false, replaceExisting = false)
                }
            } catch (error: Exception) {
                showMessage(error.message ?: "Couldn't check the paste destination.")
            }
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

    fun compressSelected(entry: StorageEntry, requestedName: String) {
        val destination = state.currentPath.lastOrNull() ?: return
        coroutineScope.launch {
            try {
                val outputName = withContext(Dispatchers.IO) {
                    fileOperations.compressToZip(
                        state.accessMode,
                        state.treeUriString,
                        entry,
                        destination.documentId,
                        destination.absolutePath,
                        requestedName
                    )
                }
                compressEntry = null
                filesViewModel.refresh()
                showMessage("Created " + outputName)
            } catch (error: Exception) {
                showMessage(error.message ?: "Couldn't compress this item.")
            }
        }
    }

    fun toggleSelectedEntry(entry: StorageEntry) {
        selectedEntryIds = if (entry.documentId in selectedEntryIds) {
            selectedEntryIds - entry.documentId
        } else {
            selectedEntryIds + entry.documentId
        }
    }

    fun beginCreate(kind: CreateItemKind) {
        if (!canMutateFiles) {
            showMessage("Choose a writable storage location to create items.")
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
        if (!canMutateFiles) {
            showMessage("Creating items is unavailable in this storage mode.")
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
            selectedEntryIds = failures.map { it.first.documentId }.toSet()
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
                selectedEntryIds = emptySet()
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
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Keep the blue toolbar background behind the invisible status-bar area.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility)
                .background(Color(0xFF082D59))
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
                        selectedCount = selectedEntryIds.size,
                        canMutateFiles = canMutateFiles,
                        clipboardPresent = clipboard != null,
                        canPaste = clipboard?.let {
                            it.accessMode == state.accessMode &&
                                it.treeUriString == state.treeUriString &&
                                state.accessMode != StorageAccessMode.SHIZUKU_STORAGE
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
                                selectedEntryIds = emptySet()
                            } else if (state.currentPath.size > 1) {
                                filesViewModel.navigateBack()
                            }
                        },
                        onToggleMultiSelect = {
                            multiSelectMode = !multiSelectMode
                            if (!multiSelectMode) selectedEntryIds = emptySet()
                            fabExpanded = false
                        },
                        onDeleteSelected = { bulkDeleteConfirmation = true },
                        onPaste = ::requestPaste,
                        onGrantWriteAccess = ::chooseFolder,
                        onOpenShizuku = ::openShizukuBrowser,
                        onOpenSettings = onOpenSettings,
                        onAbout = { aboutDialogVisible = true },
                        onCreateFolder = { beginCreate(CreateItemKind.FOLDER) },
                        onCreateFile = { beginCreate(CreateItemKind.FILE) },
                        onCreateArchive = { beginCreate(CreateItemKind.ZIP) }
                    )
                    BrowserState(
                        modifier = Modifier.weight(1f),
                        state = state,
                        onChooseFolder = ::chooseFolder,
                        entries = visibleEntries,
                        viewMode = viewMode,
                        selectedEntryIds = selectedEntryIds,
                        selectionMode = multiSelectMode,
                        onNavigateBack = filesViewModel::navigateBack,
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

        if (canMutateFiles && state.currentPath.isNotEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (fabExpanded) {
                    FloatingActionButton(
                        onClick = { beginCreate(CreateItemKind.FOLDER) },
                        modifier = Modifier.size(48.dp),
                        containerColor = Color(0xFF2492F4),
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "Create folder")
                    }
                    FloatingActionButton(
                        onClick = { beginCreate(CreateItemKind.ZIP) },
                        modifier = Modifier.size(48.dp),
                        containerColor = Color(0xFF2492F4),
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Filled.Archive, contentDescription = "Create ZIP archive")
                    }
                    FloatingActionButton(
                        onClick = { beginCreate(CreateItemKind.FILE) },
                        modifier = Modifier.size(48.dp),
                        containerColor = Color(0xFF2492F4),
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = "Create file")
                    }
                }
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
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
                    ContextActionRow("Information", Icons.Filled.Info) {
                        selectedEntry = null
                        informationEntry = entry
                    }
                    if (!entry.isDirectory) {
                        ContextActionRow("Share", Icons.Filled.Share) { shareSelected(entry) }
                    }
                    if (canMutateFiles) {
                        ContextActionRow("Copy", Icons.Filled.ContentCopy) { stageClipboard(entry, false) }
                        ContextActionRow("Cut", Icons.Filled.ContentCut) { stageClipboard(entry, true) }
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
                            compressEntry = entry
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

        compressEntry?.let { entry ->
            AlertDialog(
                onDismissRequest = { compressEntry = null },
                title = { Text("Compress to ZIP") },
                text = {
                    Column {
                        Text(
                            "Create a ZIP copy of " + entry.displayName + " in the current folder. The original item will not be changed.",
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
                        onClick = { compressSelected(entry, compressName) },
                        enabled = compressName.isNotBlank()
                    ) { Text("Compress") }
                },
                dismissButton = {
                    TextButton(onClick = { compressEntry = null }) { Text("Cancel") }
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
                title = { Text("An item already exists") },
                text = {
                    Text(
                        "A destination item named '" + conflict.existing.displayName +
                            "' already exists. Choose Keep both to create a unique name, or Replace to overwrite it."
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
                            "JarVision 0.7.0\nA compact Android file manager with local file operations and ZIP tools. Shizuku browsing remains read-only."
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
                title = { Text("Delete ${selectedEntryIds.size} items?") },
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

@Composable
private fun FileBrowserToolbar(
    title: String,
    path: String,
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
    canMutateFiles: Boolean,
    clipboardPresent: Boolean,
    canPaste: Boolean,
    needsSafWriteGrant: Boolean,
    shizukuConnected: Boolean,
    canNavigateBack: Boolean,
    onBack: () -> Unit,
    onToggleMultiSelect: () -> Unit,
    onDeleteSelected: () -> Unit,
    onPaste: () -> Unit,
    onGrantWriteAccess: () -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenSettings: () -> Unit,
    onAbout: () -> Unit,
    onCreateFolder: () -> Unit,
    onCreateFile: () -> Unit,
    onCreateArchive: () -> Unit
) {
    var viewMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var createMenuExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactActions = maxWidth < 380.dp
        val veryCompact = maxWidth < 280.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .background(Color(0xFF082D59))
                .padding(horizontal = 4.dp, vertical = 3.dp),
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

            if (clipboardPresent && !compactActions) {
                TextButton(onClick = onPaste, enabled = canPaste) {
                    Text("Paste", color = Color.White)
                }
            }

            if (selectionMode && selectedCount > 0 && canMutateFiles && !compactActions) {
                IconButton(onClick = onDeleteSelected) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete selected items", tint = Color.White)
                }
            }

            if (!veryCompact) {
                IconButton(
                    onClick = onToggleSearch,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF174777).copy(alpha = 0.38f))
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
                        .background(Color(0xFF174777).copy(alpha = 0.38f))
                ) {
                    Icon(Icons.Filled.ViewList, contentDescription = "View and sort options", tint = Color.White)
                }
                DropdownMenu(
                    expanded = viewMenuExpanded,
                    onDismissRequest = { viewMenuExpanded = false },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = Color(0xFF10345D),
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, Color(0xFF397AB5).copy(alpha = 0.9f))
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
                    containerColor = Color(0xFF10345D),
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, Color(0xFF397AB5).copy(alpha = 0.9f))
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
                        .background(Color(0xFF174777).copy(alpha = 0.38f))
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = Color.White)
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = Color(0xFF10345D),
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, Color(0xFF397AB5).copy(alpha = 0.9f))
                ) {
                    if (clipboardPresent && compactActions) {
                        DropdownMenuItem(
                            text = { Text("Paste") },
                            enabled = canPaste,
                            onClick = {
                                overflowExpanded = false
                                onPaste()
                            }
                        )
                    }
                    if (selectionMode && selectedCount > 0 && canMutateFiles && compactActions) {
                        DropdownMenuItem(
                            text = { Text("Delete selected items") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                overflowExpanded = false
                                onDeleteSelected()
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
                        text = { Text("Create  ›") },
                        enabled = canMutateFiles,
                        onClick = {
                            overflowExpanded = false
                            createMenuExpanded = true
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
                DropdownMenu(
                    expanded = createMenuExpanded,
                    onDismissRequest = { createMenuExpanded = false },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = Color(0xFF10345D),
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, Color(0xFF397AB5).copy(alpha = 0.9f))
                ) {
                    DropdownMenuItem(
                        text = { Text("Folder") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                        onClick = {
                            createMenuExpanded = false
                            onCreateFolder()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("File") },
                        leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                        onClick = {
                            createMenuExpanded = false
                            onCreateFile()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("ZIP archive") },
                        leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                        onClick = {
                            createMenuExpanded = false
                            onCreateArchive()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserState(
    modifier: Modifier = Modifier,
    state: FilesUiState,
    onChooseFolder: () -> Unit,
    entries: List<StorageEntry>,
    viewMode: BrowserViewMode,
    selectedEntryIds: Set<String>,
    selectionMode: Boolean,
    onNavigateBack: () -> Unit,
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
                text = "READ-ONLY · Shizuku",
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
                    if (state.currentPath.size > 1) {
                        item(key = "__jarvision_parent_folder__") {
                            ParentFolderGridTile(onClick = onNavigateBack)
                        }
                    }
                    gridItems(entries, key = { it.documentId }) { entry ->
                        GridFileEntry(
                            entry = entry,
                            state = state,
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
                    if (state.currentPath.size > 1) {
                        item(key = "__jarvision_parent_folder__") {
                            ParentFolderRow(onClick = onNavigateBack)
                        }
                    }
                    items(entries, key = { it.documentId }) { entry ->
                        FileEntryRow(
                            entry = entry,
                            state = state,
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
private fun ParentFolderGridTile(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Parent folder", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
            Spacer(Modifier.height(4.dp))
            Text("Parent", maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ParentFolderRow(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        )
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.size(10.dp))
            Text(
                "Parent folder",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FolderThumbnail(entry: StorageEntry, state: FilesUiState, thumbnailSize: Dp = 48.dp) {
    val context = LocalContext.current
    val thumbnailModel by produceState<Any?>(
        initialValue = null,
        key1 = entry.documentId,
        key2 = state.accessMode,
        key3 = state.treeUriString
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                when (state.accessMode) {
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
            Icon(imageVector = fallbackFolderImage(entry.displayName), contentDescription = "Folder",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (thumbnailSize <= 36.dp) 20.dp else 28.dp))
        }
    }
}

private fun fallbackFolderImage(folderName: String): ImageVector {
    val name = folderName.lowercase(Locale.ROOT)
    return when {
        "android" in name || name == "data" || name == "obb" -> Icons.Filled.Android
        "picture" in name || "image" in name || "dcim" in name -> Icons.Filled.Image
        "download" in name -> Icons.Filled.Download
        "music" in name || "audio" in name -> Icons.Filled.MusicNote
        "movie" in name || "video" in name -> Icons.Filled.Movie
        "archive" in name || name.endsWith(".zip") || name.endsWith(".7z") -> Icons.Filled.Archive
        else -> Icons.Filled.Folder
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileEntryRow(
    entry: StorageEntry,
    state: FilesUiState,
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
        shape = RoundedCornerShape(if (compact) 9.dp else 12.dp),
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
            if (entry.isDirectory) {
                FolderThumbnail(entry = entry, state = state, thumbnailSize = thumbnailSize)
            } else {
                Icon(
                    imageVector = Icons.Filled.InsertDriveFile,
                    contentDescription = "File",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(thumbnailSize)
                )
            }
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
            if (entry.isDirectory) {
                FolderThumbnail(entry = entry, state = state, thumbnailSize = 48.dp)
            } else {
                Icon(
                    Icons.Filled.InsertDriveFile,
                    contentDescription = "File",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
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
