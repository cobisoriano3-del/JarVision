package com.jarvision.app.ui.navigation

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jarvision.app.feature.access.AdvancedAccessScreen
import com.jarvision.app.feature.files.FilesScreen
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

@Composable
fun JarVisionApp() {
    val context = LocalContext.current
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
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            if (currentRoute != Routes.FILES) {
                val toolbarTitle = when (currentRoute) {
                    Routes.PROJECTS -> "Projects"
                    Routes.TOOLS -> "Tools"
                    Routes.EDITOR -> "Universal Editor"
                    Routes.ARCHIVES -> "Archive Tools"
                    Routes.DUAL_SPACE -> "Dual Space"
                    Routes.ADVANCED_ACCESS -> "Advanced Access"
                    Routes.SETTINGS -> "Settings"
                    else -> "JarVision"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D3159))
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .height(48.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentRoute !in primaryRoutes) {
                            IconButton(
                                onClick = {
                                    if (!navController.popBackStack()) {
                                        navController.navigate(Routes.FILES)
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color(0xFFE8EDF5),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = toolbarTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE8EDF5),
                            maxLines = 1
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (currentRoute != Routes.PROJECTS) {
                                    navController.navigate(Routes.PROJECTS) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = "Projects",
                                tint = Color(0xFFD9E9FB),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (currentRoute != Routes.SETTINGS) {
                                    navController.navigate(Routes.SETTINGS) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = Color(0xFFD9E9FB),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (currentRoute in primaryRoutes) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A1019))
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF101A27)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(62.dp)
                                .padding(horizontal = 5.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable {
                                            navController.navigate(tab.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) Color(0xFF3A3859) else Color.Transparent
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(vertical = 3.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.label,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (selected) Color(0xFFD9C8FF)
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(1.dp))
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = if (selected) Color(0xFF8DC4FF)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val contentModifier = if (currentRoute == Routes.FILES) {
            // The Files screen draws its own status-bar background and applies safe insets to controls.
            Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        } else {
            Modifier.padding(innerPadding)
        }
        NavHost(
            navController = navController,
            startDestination = Routes.FILES,
            modifier = contentModifier
        ) {
            composable(Routes.FILES) {
                FilesScreen(
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                    },
                    onExit = { (context as? Activity)?.finish() }
                )
            }
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
