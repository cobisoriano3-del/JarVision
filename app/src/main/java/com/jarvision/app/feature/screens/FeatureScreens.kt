package com.jarvision.app.feature.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.runtime.Composable
import com.jarvision.app.ui.components.FeatureStatusScreen

@Composable
fun FilesScreen() {
    FeatureStatusScreen(
        title = "Files",
        description = "A clean, single-pane file manager is the default. The next phase will implement Android folder access, real directory listings, search, selection, and safe file operations.",
        status = "Storage layer next",
        icon = Icons.Filled.FolderOpen
    )
}

@Composable
fun ProjectsScreen() {
    FeatureStatusScreen(
        title = "Projects",
        description = "A universal workspace for any language or file type. Projects will be saved locally inside JarVision and exported explicitly to a folder or archive when ready.",
        status = "Workspace phase next",
        icon = Icons.Filled.Workspaces
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
fun AdvancedAccessScreen() {
    FeatureStatusScreen(
        title = "Advanced Access",
        description = "Shizuku support will be optional and capability-checked. JarVision will not claim access to protected app-private data unless the device's actual permission level allows the operation.",
        status = "Permission layer pending",
        icon = Icons.Filled.Security
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
