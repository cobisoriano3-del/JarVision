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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    filesViewModel: FilesViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by filesViewModel.uiState.collectAsState()
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

    BackHandler(enabled = state.currentPath.size > 1) {
        filesViewModel.navigateBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                BrowserState(
                    state = state,
                    onNavigateBack = filesViewModel::navigateBack,
                    onChooseFolder = ::chooseFolder,
                    shizukuConnected = shizukuStatus.isConnected,
                    onOpenShizuku = ::openShizukuBrowser,
                    onOpenEntry = { entry ->
                        if (entry.isDirectory) filesViewModel.navigateInto(entry)
                        else openFile(entry)
                    },
                    onLongPressEntry = { entry -> selectedEntry = entry },
                    clipboardPresent = clipboard != null,
                    canPaste = clipboard?.let {
                        it.accessMode == state.accessMode &&
                            it.treeUriString == state.treeUriString &&
                            state.accessMode != StorageAccessMode.SHIZUKU_STORAGE
                    } == true,
                    onPaste = ::requestPaste,
                    onRetry = filesViewModel::refresh
                )
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
private fun BrowserState(
    state: FilesUiState,
    onNavigateBack: () -> Unit,
    onChooseFolder: () -> Unit,
    shizukuConnected: Boolean,
    onOpenShizuku: () -> Unit,
    onOpenEntry: (StorageEntry) -> Unit,
    onLongPressEntry: (StorageEntry) -> Unit,
    clipboardPresent: Boolean,
    canPaste: Boolean,
    onPaste: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val hasSafWriteGrant = state.treeUriString?.let { value ->
        context.contentResolver.persistedUriPermissions.any {
            it.uri == Uri.parse(value) && it.isWritePermission
        }
    } == true
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (clipboardPresent) {
                TextButton(onClick = onPaste, enabled = canPaste) {
                    Text(if (canPaste) "Paste" else "Paste unavailable")
                }
            }
            if (state.accessMode == StorageAccessMode.DOCUMENT_TREE && !hasSafWriteGrant) {
                TextButton(onClick = onChooseFolder) { Text("Grant write access") }
            }
            TextButton(onClick = onOpenShizuku) {
                Text(if (shizukuConnected) "Browse protected folders" else "Connect Shizuku")
            }
        }
        if (state.accessMode == StorageAccessMode.SHIZUKU_STORAGE) {
            Text(
                text = "Read-only Shizuku browsing · No file changes are made",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.errorMessage != null -> {
                ErrorState(
                    message = state.errorMessage,
                    onRetry = onRetry,
                    onChooseFolder = onChooseFolder,
                    modifier = Modifier.weight(1f)
                )
            }

            state.entries.isEmpty() && state.currentPath.size <= 1 -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
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

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.currentPath.size > 1) {
                        item(key = "__jarvision_parent_folder__") {
                            ParentFolderRow(onClick = onNavigateBack)
                        }
                    }
                    items(state.entries, key = { it.documentId }) { entry ->
                        FileEntryRow(
                            entry = entry,
                            state = state,
                            onClick = { onOpenEntry(entry) },
                            onLongClick = { onLongPressEntry(entry) }
                        )
                    }
                }
                Text(
                    text = "${state.entries.size} items",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ParentFolderRow(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(14.dp))
            Text("Parent folder", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FolderThumbnail(entry: StorageEntry, state: FilesUiState) {
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
        modifier = Modifier.size(48.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
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
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
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
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (entry.isDirectory) {
                FolderThumbnail(entry = entry, state = state)
            } else {
                Icon(imageVector = Icons.Filled.InsertDriveFile, contentDescription = "File",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val details = entryDetails(entry)
                if (details.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (entry.isDirectory) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
