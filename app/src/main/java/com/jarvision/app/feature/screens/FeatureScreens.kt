package com.jarvision.app.feature.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvision.app.ui.components.FeatureStatusScreen
import com.jarvision.app.data.storage.StorageEntry
import com.jarvision.app.feature.projects.PublishFolderPickerState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectsScreen(
    searchQuery: String = "",
    gridView: Boolean = false,
    projects: List<com.jarvision.app.data.projects.Project> = emptyList(),
    isCreating: Boolean = false,
    errorMessage: String? = null,
    onCreateProject: (String) -> Unit = {},
    onOpenProject: (com.jarvision.app.data.projects.Project) -> Unit = {},
    onDeleteProject: (com.jarvision.app.data.projects.Project) -> Unit = {},
    onBeginPublishProject: (com.jarvision.app.data.projects.Project) -> Unit = {},
    publishFolderPicker: PublishFolderPickerState? = null,
    onNavigatePublishFolder: (StorageEntry) -> Unit = {},
    onNavigatePublishParent: () -> Unit = {},
    onConfirmPublishFolder: (String, String) -> Unit = { _, _ -> },
    onCancelPublishFolder: () -> Unit = {},
    transferMessage: String? = null,
    transferWorking: Boolean = false
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var selectedProject by remember { mutableStateOf<com.jarvision.app.data.projects.Project?>(null) }
    var deleteConfirmationProject by remember { mutableStateOf<com.jarvision.app.data.projects.Project?>(null) }
    val searching = searchQuery.isNotBlank()
    val visibleProjects = projects.filter { it.name.contains(searchQuery, ignoreCase = true) }
    val emblemSize = if (gridView) 116.dp else 138.dp
    val folderIconSize = if (gridView) 68.dp else 84.dp
    val badgeSize = if (gridView) 30.dp else 36.dp
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020B18), Color(0xFF03152A), Color(0xFF020B18))))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            if (!searching) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showCreateDialog = true },
                    shape = RoundedCornerShape(20.dp), color = Color(0xFF091D33),
                    border = BorderStroke(1.dp, Color(0xFF284967).copy(alpha = 0.9f)), shadowElevation = 1.dp
                ) {
                    Row(Modifier.background(Color(0xFF091D33)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(56.dp), shape = RoundedCornerShape(17.dp), color = Color(0xFF091B30), border = BorderStroke(1.dp, Color(0xFF254260))) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, null, Modifier.size(32.dp), tint = Color(0xFF278BEE))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Create Project", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFFF4F7FF), maxLines = 1)
                            Text("Start a new project.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA6BBD5), maxLines = 1)
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.ArrowForwardIos, "Create project", Modifier.size(17.dp), tint = Color(0xFF459DF5))
                    }
                }
                Spacer(Modifier.height(14.dp)); ProjectsDivider()
            } else Spacer(Modifier.height(8.dp))
            if (transferWorking || transferMessage != null) {
                Text(
                    text = if (transferWorking) "Transfer in progress…" else transferMessage.orEmpty(),
                    color = if (transferMessage?.contains("failed", true) == true ||
                        transferMessage?.contains("could not", true) == true
                    ) Color(0xFFFF9A9A) else Color(0xFFA7BEDB),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            if (visibleProjects.isNotEmpty())
                if (gridView) {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) { items(visibleProjects, key = { it.id }) { project -> ProjectCard(project, onClick = { onOpenProject(project) }, onLongClick = { selectedProject = project }) } }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(visibleProjects, key = { it.id }) { project -> ProjectCard(project, onClick = { onOpenProject(project) }, onLongClick = { selectedProject = project }) }
                    }
                }
            else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Surface(Modifier.size(emblemSize), shape = CircleShape, color = Color(0xFF0A1C33), border = BorderStroke(1.dp, Color(0xFF122D49))) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.FolderOpen, null, Modifier.size(folderIconSize), tint = Color(0xFF369AFF))
                                Surface(Modifier.align(Alignment.BottomEnd).padding(if (gridView) 13.dp else 15.dp).size(badgeSize), shape = CircleShape, color = Color(0xFF087FF0), border = BorderStroke(1.dp, Color(0xFF0D2D4D))) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, null, Modifier.size(if (gridView) 19.dp else 23.dp), tint = Color(0xFF061A2D)) }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(if (searching) "No projects found" else "Projects", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFF4F7FF), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(when { searching -> "Try another search or clear the search field."; errorMessage != null -> errorMessage; isCreating -> "Creating project…"; else -> "Your projects will appear here." }, style = MaterialTheme.typography.bodyLarge, color = if (errorMessage != null) Color(0xFFFF9A9A) else Color(0xFFA7BEDB), textAlign = TextAlign.Center)
                    }
                }
            }
            ProjectsDivider(); Spacer(Modifier.height(8.dp))
        }
    }

    selectedProject?.let { project ->
        ModalBottomSheet(
            onDismissRequest = { selectedProject = null },
            containerColor = Color(0xFF071B30),
            contentColor = Color(0xFFF4F7FF),
            scrimColor = Color.Black.copy(alpha = 0.68f),
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(vertical = 10.dp).size(width = 38.dp, height = 4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF4778A6)
                ) {}
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    project.name,
                    color = Color(0xFFF4F7FF),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text("Project actions", color = Color(0xFFA7BEDB), style = MaterialTheme.typography.bodySmall)
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !transferWorking) {
                        selectedProject = null
                        onBeginPublishProject(project)
                    },
                    color = Color(0xFF0A2B4B),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF1A5489))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Publish, null, tint = Color(0xFF65B7FF))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Publish", color = Color(0xFFF4F7FF), fontWeight = FontWeight.SemiBold)
                            Text("Export a copy to a selected storage folder.", color = Color(0xFFA7BEDB), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !transferWorking && !isCreating) {
                        selectedProject = null
                        deleteConfirmationProject = project
                    },
                    color = Color(0xFF2A1820),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF78404C))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF9696))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Delete", color = Color(0xFFFFB7B7), fontWeight = FontWeight.SemiBold)
                            Text("Remove this project and its private workspace.", color = Color(0xFFC6AAB0), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }


    publishFolderPicker?.let { picker ->
        ModalBottomSheet(
            onDismissRequest = onCancelPublishFolder,
            containerColor = Color(0xFF071B30),
            contentColor = Color(0xFFF4F7FF),
            scrimColor = Color.Black.copy(alpha = 0.68f),
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(vertical = 10.dp).size(width = 38.dp, height = 4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF4778A6)
                ) {}
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 660.dp)
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigatePublishParent,
                        enabled = picker.pathStack.size > 1 && !picker.isLoading && !transferWorking
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Parent folder", tint = Color(0xFF65B7FF))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Publish project",
                            color = Color(0xFFF4F7FF),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            picker.currentPath,
                            color = Color(0xFFA7BEDB),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                    }
                }
                Text(
                    "Choose an internal-storage folder. JarVision will export a copy and keep the original project.",
                    color = Color(0xFFA7BEDB),
                    style = MaterialTheme.typography.bodySmall
                )
                when {
                    picker.isLoading -> Box(
                        Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Color(0xFF65B7FF)) }
                    picker.errorMessage != null -> Box(
                        Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(picker.errorMessage, color = Color(0xFFFF9A9A), style = MaterialTheme.typography.bodySmall)
                    }
                    picker.folders.isEmpty() -> Box(
                        Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No subfolders in this location.", color = Color(0xFFA7BEDB))
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(picker.folders, key = { it.absolutePath ?: it.documentId }) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(13.dp))
                                    .clickable(enabled = !transferWorking) { onNavigatePublishFolder(folder) }
                                    .background(Color(0xFF0A2745))
                                    .padding(horizontal = 13.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.FolderOpen, null, tint = Color(0xFF65B7FF))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    folder.displayName,
                                    color = Color(0xFFF4F7FF),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFA7BEDB))
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = !transferWorking,
                        onClick = onCancelPublishFolder
                    ) { Text("Cancel", color = Color(0xFFA7BEDB)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = picker.canWriteCurrentFolder && !picker.isLoading &&
                            picker.errorMessage == null && !transferWorking,
                        onClick = { onConfirmPublishFolder(picker.projectId, picker.currentPath) }
                    ) {
                        if (transferWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Publish here")
                    }
                }
            }
        }
    }

    deleteConfirmationProject?.let { project ->
        AlertDialog(
            onDismissRequest = { if (!isCreating) deleteConfirmationProject = null },
            containerColor = Color(0xFF091D33),
            title = { Text("Delete ${project.name}?", color = Color(0xFFF4F7FF), fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "This permanently removes the project workspace and its metadata. This cannot be undone.",
                    color = Color(0xFFA7BEDB)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isCreating,
                    onClick = {
                        onDeleteProject(project)
                        deleteConfirmationProject = null
                    }
                ) { Text(if (isCreating) "Deleting…" else "Delete", color = Color(0xFFFF9696)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !isCreating,
                    onClick = { deleteConfirmationProject = null }
                ) { Text("Cancel", color = Color(0xFFA7BEDB)) }
            }
        )
    }

    if (showCreateDialog) AlertDialog(
        onDismissRequest = { if (!isCreating) { showCreateDialog = false; projectName = "" } },
        containerColor = Color(0xFF091D33),
        title = { Text("Create project", color = Color(0xFFF4F7FF), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose a name for your project workspace.", color = Color(0xFFA7BEDB))
                OutlinedTextField(
                    value = projectName, onValueChange = { projectName = it }, singleLine = true, enabled = !isCreating,
                    label = { Text("Project name") }, placeholder = { Text("My project") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF278BEE), unfocusedBorderColor = Color(0xFF284967), focusedLabelColor = Color(0xFF65B7FF), unfocusedLabelColor = Color(0xFFA7BEDB), cursorColor = Color(0xFF65B7FF))
                )
                if (errorMessage != null) Text(errorMessage, color = Color(0xFFFF9A9A), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = !isCreating && projectName.isNotBlank(), onClick = { onCreateProject(projectName); showCreateDialog = false; projectName = "" }) { Text(if (isCreating) "Creating…" else "Create", color = Color(0xFF65B7FF)) } },
        dismissButton = { TextButton(enabled = !isCreating, onClick = { showCreateDialog = false; projectName = "" }) { Text("Cancel", color = Color(0xFFA7BEDB)) } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
    project: com.jarvision.app.data.projects.Project,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), shape = RoundedCornerShape(18.dp), color = Color(0xFF091D33), border = BorderStroke(1.dp, Color(0xFF284967).copy(alpha = 0.8f))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(54.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFF0A2745), border = BorderStroke(1.dp, Color(0xFF20476B))) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.FolderOpen, null, Modifier.size(28.dp), tint = Color(0xFF369AFF)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, color = Color(0xFFF4F7FF), fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Text("Project workspace", color = Color(0xFFA7BEDB), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProjectsDivider(emphasized: Boolean = false) {
    val lineAlpha = if (emphasized) 0.72f else 0.42f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFF0B477C).copy(alpha = lineAlpha),
                        Color(0xFF1684F6).copy(alpha = lineAlpha),
                        Color(0xFF0B477C).copy(alpha = lineAlpha),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
fun EditorScreen() {
    FeatureStatusScreen(
        title = "Universal Editor",
        description = "The editor engine is not integrated yet. Its planned foundation includes multiple tabs, syntax highlighting, search and replace, undo and redo, safe saving, and large-file performance.",
        status = "Editor engine pending",
        icon = Icons.Filled.Code
    )
}

@Composable
fun ArchivesScreen() {
    FeatureStatusScreen(
        title = "Archive Tools",
        description = "Archive browsing, creation, extraction, integrity checks, password handling, and format-specific capabilities will be added behind one consistent operation layer.",
        status = "Archive engine pending",
        icon = Icons.Filled.Archive
    )
}

@Composable
fun DualSpaceScreen() {
    FeatureStatusScreen(
        title = "Dual Space",
        description = "Optional side-by-side folder windows with independent navigation. Standard single-pane browsing remains the default and will never be forced into a dual-window layout.",
        status = "Optional feature planned",
        icon = Icons.Filled.ViewWeek
    )
}

@Composable
fun SettingsScreen() {
    FeatureStatusScreen(
        title = "Settings",
        description = "Appearance, editor preferences, file handling, archive behavior, and advanced-access controls will be added alongside the features they configure.",
        status = "Preferences pending",
        icon = Icons.Filled.Settings
    )
}
