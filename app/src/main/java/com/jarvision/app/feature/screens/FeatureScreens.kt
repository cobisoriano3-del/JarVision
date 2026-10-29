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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvision.app.ui.components.FeatureStatusScreen

@Composable
fun ProjectsScreen(
    searchQuery: String = "",
    gridView: Boolean = false,
    projects: List<com.jarvision.app.data.projects.Project> = emptyList(),
    isCreating: Boolean = false,
    errorMessage: String? = null,
    onCreateProject: (String) -> Unit = {}
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
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
            if (visibleProjects.isNotEmpty()) {
                if (gridView) {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) { items(visibleProjects, key = { it.id }) { ProjectCard(it) } }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(visibleProjects, key = { it.id }) { ProjectCard(it) }
                    }
                }
            } else {
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

@Composable
private fun ProjectCard(project: com.jarvision.app.data.projects.Project) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color(0xFF091D33), border = BorderStroke(1.dp, Color(0xFF284967).copy(alpha = 0.8f))) {
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
