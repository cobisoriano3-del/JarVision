package com.jarvision.app.feature.projects

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvision.app.data.projects.Project
import com.jarvision.app.data.projects.ProjectWorkspaceEntry
import kotlinx.coroutines.launch
import java.util.Locale

private object WorkspacePalette {
    val Background = Color(0xFF020B18)
    val TopBar = Color(0xFF06172A)
    val Card = Color(0xFF071B30)
    val CardBorder = Color(0xFF164B7B)
    val IconTile = Color(0xFF0A2D50)
    val Accent = Color(0xFF087FF5)
    val AccentLight = Color(0xFF25A0FF)
    val Text = Color(0xFFF3F7FF)
    val Muted = Color(0xFFA7BEDB)
    val Error = Color(0xFFFF9696)
}

private enum class WorkspaceNameDialogType {
    Folder,
    TextFile,
    Rename
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkspaceScreen(
    project: Project,
    viewModel: WorkspaceViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    var multiSelect by remember(project.id) { mutableStateOf(false) }
    var selectedPaths by remember(project.id) { mutableStateOf(emptySet<String>()) }
    var nameDialogType by remember { mutableStateOf<WorkspaceNameDialogType?>(null) }
    var nameDialogValue by remember { mutableStateOf("") }
    var renameEntry by remember { mutableStateOf<ProjectWorkspaceEntry?>(null) }

    var editingEntry by remember { mutableStateOf<ProjectWorkspaceEntry?>(null) }
    var editorText by remember { mutableStateOf("") }
    var originalEditorText by remember { mutableStateOf("") }
    var editorLoading by remember { mutableStateOf(false) }
    var editorSaving by remember { mutableStateOf(false) }
    var editorError by remember { mutableStateOf<String?>(null) }
    var showDiscardEditorChanges by remember { mutableStateOf(false) }

    LaunchedEffect(project.id) {
        multiSelect = false
        selectedPaths = emptySet()
        viewModel.openProject(project.id)
    }

    LaunchedEffect(state.relativeDirectory) {
        selectedPaths = emptySet()
    }

    BackHandler(enabled = multiSelect || state.relativeDirectory.isNotEmpty()) {
        if (multiSelect) {
            multiSelect = false
            selectedPaths = emptySet()
        } else {
            viewModel.navigateUp()
        }
    }

    fun toggleSelected(path: String) {
        selectedPaths = if (path in selectedPaths) {
            selectedPaths - path
        } else {
            selectedPaths + path
        }
    }

    fun openEntry(entry: ProjectWorkspaceEntry) {
        if (multiSelect) {
            toggleSelected(entry.relativePath)
        } else if (entry.isDirectory) {
            viewModel.openDirectory(entry.relativePath)
        } else {
            editingEntry = entry
            editorText = ""
            originalEditorText = ""
            editorLoading = true
            editorSaving = false
            editorError = null
            showDiscardEditorChanges = false
            scope.launch {
                try {
                    editorText = viewModel.readTextFile(project.id, entry.relativePath)
                    originalEditorText = editorText
                } catch (error: Exception) {
                    editorError = error.message ?: "Could not open this text file."
                } finally {
                    editorLoading = false
                }
            }
        }
    }

    fun requestCloseEditor() {
        if (editorSaving) return
        if (!editorLoading && editorError == null && editorText != originalEditorText) {
            showDiscardEditorChanges = true
        } else {
            editingEntry = null
            editorError = null
        }
    }

    fun showNameDialog(type: WorkspaceNameDialogType, entry: ProjectWorkspaceEntry? = null) {
        nameDialogType = type
        renameEntry = entry
        nameDialogValue = if (type == WorkspaceNameDialogType.Rename) entry?.name.orEmpty() else ""
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = WorkspacePalette.Background,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WorkspacePalette.TopBar)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility)
                        .background(WorkspacePalette.TopBar)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                when {
                                    multiSelect -> {
                                        multiSelect = false
                                        selectedPaths = emptySet()
                                    }
                                    state.relativeDirectory.isNotEmpty() -> viewModel.navigateUp()
                                    else -> onBack()
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = WorkspacePalette.AccentLight,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.name,
                                color = WorkspacePalette.Text,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Workspace",
                                color = WorkspacePalette.Muted,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1
                            )
                        }

                        Surface(
                            onClick = {
                                multiSelect = !multiSelect
                                selectedPaths = emptySet()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (multiSelect) Color(0xFF0C3D70) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.TouchApp,
                                    contentDescription = null,
                                    tint = WorkspacePalette.AccentLight,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .rotate(-35f)
                                )
                                Text(
                                    text = "Multi select",
                                    color = WorkspacePalette.AccentLight,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = WorkspacePalette.CardBorder, thickness = 1.dp)
            }
        },
        floatingActionButton = {
            WorkspaceSpeedDialFab(
                onNewTxt = { showNameDialog(WorkspaceNameDialogType.TextFile) },
                onNewFolder = { showNameDialog(WorkspaceNameDialogType.Folder) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(WorkspacePalette.Background, Color(0xFF031326), WorkspacePalette.Background)
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.relativeDirectory.isBlank()) "Project files"
                        else state.relativeDirectory.substringAfterLast('/'),
                        color = WorkspacePalette.Text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.relativeDirectory.isNotBlank()) {
                        Text(
                            text = state.relativeDirectory,
                            color = WorkspacePalette.Muted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = "${state.entries.size} items",
                    color = WorkspacePalette.Muted,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage.orEmpty(),
                    color = WorkspacePalette.Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            if (multiSelect) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedPaths.size} selected",
                        color = WorkspacePalette.AccentLight,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { selectedPaths = state.entries.map { it.relativePath }.toSet() },
                        enabled = state.entries.isNotEmpty()
                    ) {
                        Text("Select all", color = WorkspacePalette.AccentLight)
                    }
                    TextButton(
                        onClick = { selectedPaths = emptySet() },
                        enabled = selectedPaths.isNotEmpty()
                    ) {
                        Text("Clear", color = WorkspacePalette.Muted)
                    }
                }
            }

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = WorkspacePalette.AccentLight)
                    }
                }

                state.entries.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = WorkspacePalette.AccentLight,
                                modifier = Modifier.size(60.dp)
                            )
                            Text(
                                text = "This workspace is empty",
                                color = WorkspacePalette.Text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Create a folder or text file to get started.",
                                color = WorkspacePalette.Muted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 3.dp,
                            bottom = 112.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.entries, key = { it.relativePath }) { entry ->
                            WorkspaceFileCard(
                                entry = entry,
                                selected = entry.relativePath in selectedPaths,
                                multiSelect = multiSelect,
                                onClick = { openEntry(entry) },
                                onOpen = { openEntry(entry) },
                                onRename = { showNameDialog(WorkspaceNameDialogType.Rename, entry) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (nameDialogType != null) {
        val type = nameDialogType!!
        val title = when (type) {
            WorkspaceNameDialogType.Folder -> "New Folder"
            WorkspaceNameDialogType.TextFile -> "New Text File"
            WorkspaceNameDialogType.Rename -> "Rename"
        }
        val supportingText = when (type) {
            WorkspaceNameDialogType.Folder -> "Choose a folder name."
            WorkspaceNameDialogType.TextFile -> "The .txt extension is added automatically."
            WorkspaceNameDialogType.Rename -> "Enter the new item name."
        }
        AlertDialog(
            onDismissRequest = {
                if (!state.isWorking) {
                    nameDialogType = null
                    renameEntry = null
                    nameDialogValue = ""
                }
            },
            containerColor = WorkspacePalette.Card,
            title = {
                Text(title, color = WorkspacePalette.Text, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(supportingText, color = WorkspacePalette.Muted)
                    OutlinedTextField(
                        value = nameDialogValue,
                        onValueChange = { nameDialogValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !state.isWorking,
                        placeholder = { Text("Name", color = WorkspacePalette.Muted) },
                        colors = workspaceTextFieldColors()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isWorking && nameDialogValue.isNotBlank(),
                    onClick = {
                        when (type) {
                            WorkspaceNameDialogType.Folder -> viewModel.createFolder(nameDialogValue)
                            WorkspaceNameDialogType.TextFile -> viewModel.createTextFile(nameDialogValue)
                            WorkspaceNameDialogType.Rename -> renameEntry?.let {
                                viewModel.renameEntry(it, nameDialogValue)
                            }
                        }
                        nameDialogType = null
                        renameEntry = null
                        nameDialogValue = ""
                    }
                ) {
                    Text(if (state.isWorking) "Working…" else if (type == WorkspaceNameDialogType.Rename) "Rename" else "Create", color = WorkspacePalette.AccentLight)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isWorking,
                    onClick = {
                        nameDialogType = null
                        renameEntry = null
                        nameDialogValue = ""
                    }
                ) {
                    Text("Cancel", color = WorkspacePalette.Muted)
                }
            }
        )
    }

    editingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { requestCloseEditor() },
            containerColor = WorkspacePalette.Card,
            title = {
                Text(
                    text = entry.name,
                    color = WorkspacePalette.Text,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editorLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = WorkspacePalette.AccentLight)
                        }
                    } else {
                        editorError?.let {
                            Text(it, color = WorkspacePalette.Error, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedTextField(
                            value = editorText,
                            onValueChange = { editorText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp, max = 420.dp),
                            enabled = !editorSaving && !editorLoading &&
                                (editorError == null || editorText != originalEditorText),
                            maxLines = 16,
                            colors = workspaceTextFieldColors()
                        )
                    }
                    Text(
                        text = "Workspace text editor • 1 MiB limit",
                        color = WorkspacePalette.Muted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !editorLoading && !editorSaving &&
                        (editorError == null || editorText != originalEditorText),
                    onClick = {
                        editorSaving = true
                        scope.launch {
                            try {
                                viewModel.saveTextFile(project.id, entry.relativePath, editorText)
                                originalEditorText = editorText
                                editingEntry = null
                                editorError = null
                            } catch (error: Exception) {
                                editorError = error.message ?: "Could not save this text file."
                            } finally {
                                editorSaving = false
                            }
                        }
                    }
                ) {
                    Text(if (editorSaving) "Saving…" else "Save", color = WorkspacePalette.AccentLight)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !editorSaving,
                    onClick = { requestCloseEditor() }
                ) {
                    Text("Close", color = WorkspacePalette.Muted)
                }
            }
        )
    }

    if (showDiscardEditorChanges) {
        AlertDialog(
            onDismissRequest = { showDiscardEditorChanges = false },
            containerColor = WorkspacePalette.Card,
            title = {
                Text("Discard unsaved changes?", color = WorkspacePalette.Text, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "Your edits to this text file have not been saved.",
                    color = WorkspacePalette.Muted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardEditorChanges = false
                        editingEntry = null
                        editorError = null
                    }
                ) {
                    Text("Discard", color = WorkspacePalette.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardEditorChanges = false }) {
                    Text("Keep editing", color = WorkspacePalette.AccentLight)
                }
            }
        )
    }
}

@Composable
private fun WorkspaceFileCard(
    entry: ProjectWorkspaceEntry,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit
) {
    var menuExpanded by remember(entry.relativePath) { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            1.dp,
            if (selected) WorkspacePalette.AccentLight else WorkspacePalette.CardBorder
        ),
        colors = CardDefaults.cardColors(containerColor = WorkspacePalette.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(WorkspacePalette.IconTile)
                    .border(1.dp, WorkspacePalette.CardBorder, RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = WorkspacePalette.AccentLight,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = entry.name,
                    color = WorkspacePalette.Text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.isDirectory) {
                        "Folder • ${entry.childCount} item${if (entry.childCount == 1) "" else "s"}"
                    } else {
                        "Text file • ${formatWorkspaceBytes(entry.sizeBytes)}"
                    },
                    color = WorkspacePalette.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }

            if (multiSelect) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) WorkspacePalette.AccentLight else WorkspacePalette.Muted,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(28.dp)
                )
            } else {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Workspace item actions",
                            tint = WorkspacePalette.AccentLight,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = WorkspacePalette.Card,
                        tonalElevation = 0.dp
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open", color = WorkspacePalette.Text) },
                            onClick = {
                                menuExpanded = false
                                onOpen()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename", color = WorkspacePalette.Text) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSpeedDialFab(
    onNewTxt: () -> Unit,
    onNewFolder: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    BackHandler(enabled = expanded) {
        expanded = false
    }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.88f, animationSpec = tween(160)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.88f, animationSpec = tween(120))
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                WorkspaceSpeedDialAction(
                    label = "New Txt",
                    icon = Icons.Outlined.NoteAdd,
                    onClick = {
                        expanded = false
                        onNewTxt()
                    }
                )
                WorkspaceSpeedDialAction(
                    label = "New Folder",
                    icon = Icons.Outlined.CreateNewFolder,
                    onClick = {
                        expanded = false
                        onNewFolder()
                    }
                )
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            shape = CircleShape,
            containerColor = WorkspacePalette.Accent,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 10.dp,
                pressedElevation = 13.dp
            )
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = if (expanded) "Close create menu" else "Create workspace item",
                modifier = Modifier.size(29.dp)
            )
        }
    }
}

@Composable
private fun WorkspaceSpeedDialAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = WorkspacePalette.Card,
        border = BorderStroke(1.dp, WorkspacePalette.CardBorder),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .height(58.dp)
                .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = label,
                color = WorkspacePalette.Text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(WorkspacePalette.IconTile),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = WorkspacePalette.AccentLight,
                    modifier = Modifier.size(27.dp)
                )
            }
        }
    }
}

@Composable
private fun workspaceTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = WorkspacePalette.Text,
    unfocusedTextColor = WorkspacePalette.Text,
    focusedBorderColor = WorkspacePalette.AccentLight,
    unfocusedBorderColor = WorkspacePalette.CardBorder,
    focusedLabelColor = WorkspacePalette.AccentLight,
    unfocusedLabelColor = WorkspacePalette.Muted,
    cursorColor = WorkspacePalette.AccentLight
)

private fun formatWorkspaceBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}
