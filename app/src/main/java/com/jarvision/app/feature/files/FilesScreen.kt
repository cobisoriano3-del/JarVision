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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvision.app.data.storage.StorageEntry
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilesScreen(
    filesViewModel: FilesViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by filesViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
            .setDataAndType(fileUri, entry.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setClipData(ClipData.newUri(context.contentResolver, entry.displayName, fileUri))

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

    BackHandler(enabled = state.currentPath.size > 1) {
        filesViewModel.navigateBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.accessMode == StorageAccessMode.NONE -> {
                StorageAccessRequiredState(
                    onRequestStorageAccess = ::requestStorageAccess,
                    onChooseFolder = ::chooseFolder
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
                    onRefresh = filesViewModel::refresh,
                    onChooseFolder = ::chooseFolder,
                    onOpenEntry = { entry ->
                        if (entry.isDirectory) filesViewModel.navigateInto(entry)
                        else openFile(entry)
                    },
                    onRetry = filesViewModel::refresh
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun StorageAccessRequiredState(
    onRequestStorageAccess: () -> Unit,
    onChooseFolder: () -> Unit
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
    onRefresh: () -> Unit,
    onChooseFolder: () -> Unit,
    onOpenEntry: (StorageEntry) -> Unit,
    onRetry: () -> Unit
) {
    val currentLocation = state.currentPath.last()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.currentPath.size > 1) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Parent folder")
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentLocation.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.currentPath.joinToString(" / ") { it.displayName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh folder")
            }
            TextButton(onClick = onChooseFolder) {
                Text("Change")
            }
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

            state.entries.isEmpty() -> {
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
                    items(state.entries, key = { it.documentId }) { entry ->
                        FileEntryRow(entry = entry, onClick = { onOpenEntry(entry) })
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
private fun FileEntryRow(entry: StorageEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                contentDescription = if (entry.isDirectory) "Folder" else "File",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
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
