package com.jarvision.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jarvision.app.feature.files.FilesScreen
import com.jarvision.app.feature.screens.AdvancedAccessScreen
import com.jarvision.app.feature.screens.ArchivesScreen
import com.jarvision.app.feature.screens.DualSpaceScreen
import com.jarvision.app.feature.screens.EditorScreen
import com.jarvision.app.feature.screens.ProjectsScreen
import com.jarvision.app.feature.screens.SettingsScreen
import com.jarvision.app.feature.tools.ToolsScreen

private object Routes {
    const val FILES = "files"
    const val PROJECTS = "projects"
    const val TOOLS = "tools"
    const val EDITOR = "editor"
    const val ARCHIVES = "archives"
    const val DUAL_SPACE = "dual-space"
    const val ADVANCED_ACCESS = "advanced-access"
    const val SETTINGS = "settings"
}

private data class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarVisionApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.FILES
    val primaryRoutes = setOf(Routes.FILES, Routes.PROJECTS, Routes.TOOLS)
    val tabs = listOf(
        MainTab(Routes.FILES, "Files", Icons.Filled.FolderOpen),
        MainTab(Routes.PROJECTS, "Projects", Icons.Filled.Workspaces),
        MainTab(Routes.TOOLS, "Tools", Icons.Filled.Build)
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("JarVision") },
                navigationIcon = {
                    if (currentRoute !in primaryRoutes) {
                        IconButton(onClick = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Routes.FILES)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (currentRoute != Routes.PROJECTS) {
                            navController.navigate(Routes.PROJECTS) {
                                launchSingleTop = true
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = "Projects menu"
                        )
                    }
                    IconButton(onClick = {
                        if (currentRoute != Routes.SETTINGS) {
                            navController.navigate(Routes.SETTINGS) {
                                launchSingleTop = true
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (currentRoute in primaryRoutes) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.FILES,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.FILES) { FilesScreen() }
            composable(Routes.PROJECTS) { ProjectsScreen() }
            composable(Routes.TOOLS) {
                ToolsScreen(
                    onOpenEditor = { navController.navigate(Routes.EDITOR) },
                    onOpenArchives = { navController.navigate(Routes.ARCHIVES) },
                    onOpenDualSpace = { navController.navigate(Routes.DUAL_SPACE) },
                    onOpenAdvancedAccess = { navController.navigate(Routes.ADVANCED_ACCESS) }
                )
            }
            composable(Routes.EDITOR) { EditorScreen() }
            composable(Routes.ARCHIVES) { ArchivesScreen() }
            composable(Routes.DUAL_SPACE) { DualSpaceScreen() }
            composable(Routes.ADVANCED_ACCESS) { AdvancedAccessScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}
