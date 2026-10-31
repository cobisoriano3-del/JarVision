package com.jarvision.app.ui.navigation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
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
import com.jarvision.app.feature.projects.ProjectsViewModel
import com.jarvision.app.feature.projects.WorkspaceViewModel
import com.jarvision.app.feature.projects.WorkspaceScreen
import com.jarvision.app.feature.projects.WorkspaceTransferViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
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
    const val WORKSPACE = "workspace/{projectId}"
}

private data class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JarVisionApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.FILES
    val primaryRoutes = setOf(Routes.FILES, Routes.PROJECTS, Routes.TOOLS)
    val isPrimaryDashboard = currentRoute == Routes.PROJECTS || currentRoute == Routes.TOOLS
    val tabs = listOf(
        MainTab(Routes.FILES, "Files", Icons.Filled.FolderOpen),
        MainTab(Routes.PROJECTS, "Projects", Icons.Filled.Workspaces),
        MainTab(Routes.TOOLS, "Tools", Icons.Filled.Build)
    )

    val projectsViewModel: ProjectsViewModel = viewModel()
    val workspaceViewModel: WorkspaceViewModel = viewModel()
    val transferViewModel: WorkspaceTransferViewModel = viewModel()
    val transferState by transferViewModel.state.collectAsState()
    val projects by projectsViewModel.projects.collectAsState()
    val creatingProject by projectsViewModel.isCreating.collectAsState()
    val projectError by projectsViewModel.errorMessage.collectAsState()

    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var gridView by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(currentRoute) {
        if (!isPrimaryDashboard) {
            searchExpanded = false
            searchQuery = ""
            overflowExpanded = false
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
            if (route in primaryRoutes) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                restoreState = true
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            when {
                isPrimaryDashboard -> {
                    val title = if (currentRoute == Routes.PROJECTS) "Projects" else "Tools"
                    val subtitle = if (currentRoute == Routes.PROJECTS) "Organize your work in one place." else "Powerful tools for your files."
                    val titleIcon = if (currentRoute == Routes.PROJECTS) Icons.Filled.FolderOpen else Icons.Filled.Build
                    Column(Modifier.fillMaxWidth().background(Color(0xFF020B18))) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility)
                                .background(Brush.verticalGradient(listOf(Color(0xFF061A33), Color(0xFF020B18))))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFF082447), Color(0xFF06182D), Color(0xFF082447))))
                                .border(BorderStroke(1.dp, Color(0xFF163F6A)), RoundedCornerShape(22.dp))
                                .padding(horizontal = 9.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF0A3767),
                                border = BorderStroke(1.dp, Color(0xFF18518A))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(titleIcon, contentDescription = null, modifier = Modifier.size(25.dp), tint = Color(0xFFCAE5FF))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            if (searchExpanded) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    placeholder = { Text(if (currentRoute == Routes.TOOLS) "Search tools" else "Search projects", maxLines = 1) },
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = Color(0xFF79BDFF),
                                        focusedContainerColor = Color(0xFF0A2B4B),
                                        unfocusedContainerColor = Color(0xFF0A2B4B),
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedPlaceholderColor = Color(0xFF8FB6DC),
                                        unfocusedPlaceholderColor = Color(0xFF8FB6DC)
                                    )
                                )
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFF0F6FF), maxLines = 1)
                                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFFA8C0DE), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            ToolbarIconButton(
                                icon = if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                                description = if (searchExpanded) "Close search" else "Search",
                                active = searchExpanded,
                                onClick = { if (searchExpanded) { searchExpanded = false; searchQuery = "" } else searchExpanded = true }
                            )
                            ToolbarIconButton(
                                icon = Icons.Filled.GridView,
                                description = if (gridView) "Switch to list view" else "Switch to grid view",
                                active = gridView,
                                onClick = { gridView = !gridView }
                            )
                            Box {
                                IconButton(
                                    onClick = { overflowExpanded = true },
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B2C4D))
                                ) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = Color(0xFFD9E9FF))
                                }
                                DropdownMenu(
                                    expanded = overflowExpanded,
                                    onDismissRequest = { overflowExpanded = false },
                                    shape = RoundedCornerShape(18.dp),
                                    containerColor = Color(0xFF071B32),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 18.dp
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Browse files") },
                                        leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                                        onClick = { overflowExpanded = false; navigateTo(Routes.FILES) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                        onClick = { overflowExpanded = false; navigateTo(Routes.SETTINGS) }
                                    )
                                }
                            }
                        }
                    }
                }
                currentRoute != Routes.FILES && currentRoute != Routes.WORKSPACE -> {
                    val toolbarTitle = when (currentRoute) {
                        Routes.EDITOR -> "Universal Editor"
                        Routes.ARCHIVES -> "Archive Tools"
                        Routes.DUAL_SPACE -> "Dual Space"
                        Routes.ADVANCED_ACCESS -> "Advanced Access"
                        Routes.SETTINGS -> "Settings"
                        else -> "JarVision"
                    }
                    val toolbarSubtitle = when (currentRoute) {
                        Routes.EDITOR -> "Multi-tab text and code editing."
                        Routes.ARCHIVES -> "Create, browse, test, and extract archives."
                        Routes.DUAL_SPACE -> "Optional side-by-side folder windows."
                        Routes.ADVANCED_ACCESS -> "Shizuku capability and permission status."
                        Routes.SETTINGS -> "Customize your workspace."
                        else -> ""
                    }
                    Column(Modifier.fillMaxWidth().background(Color(0xFF020B18))) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility)
                                .background(Brush.verticalGradient(listOf(Color(0xFF061A33), Color(0xFF020B18))))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFF082447), Color(0xFF06182D), Color(0xFF082447))))
                                .border(BorderStroke(1.dp, Color(0xFF163F6A)), RoundedCornerShape(22.dp))
                                .padding(horizontal = 7.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (!navController.popBackStack()) navigateTo(Routes.FILES) },
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B2C4D))
                            ) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFD9E9FF))
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(toolbarTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFF0F6FF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(toolbarSubtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFFA8C0DE), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            ToolbarIconButton(Icons.Filled.Workspaces, "Projects", false) { navigateTo(Routes.PROJECTS) }
                            ToolbarIconButton(Icons.Filled.Settings, "Settings", currentRoute == Routes.SETTINGS) { navigateTo(Routes.SETTINGS) }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (currentRoute in primaryRoutes) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF020B18))
                        .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF06172A),
                        border = BorderStroke(1.dp, Color(0xFF173A60))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 5.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                Surface(
                                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { navigateTo(tab.route) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) Color(0xFF087DFA) else Color.Transparent
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(vertical = 3.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(20.dp), tint = if (selected) Color.White else Color(0xFF91B5DC))
                                        Spacer(Modifier.height(2.dp))
                                        Text(tab.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = if (selected) Color.White else Color(0xFF91B5DC), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val horizontalCutoutInsets =
            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
        val contentModifier = when {
            currentRoute == Routes.FILES -> {
                // FilesScreen reserves its own hidden status-bar inset; Scaffold still reserves the bottom bar.
                Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            }
            currentRoute in primaryRoutes -> {
                // The custom bottom navigation already includes the hidden navigation-bar inset.
                Modifier.padding(innerPadding).windowInsetsPadding(horizontalCutoutInsets)
            }
            else -> {
                // Secondary destinations have no bottom bar, so reserve the hidden navigation-bar area here.
                Modifier
                    .padding(innerPadding)
                    .windowInsetsPadding(horizontalCutoutInsets)
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            }
        }
        NavHost(
            navController = navController,
            startDestination = Routes.FILES,
            modifier = contentModifier
        ) {
            composable(Routes.FILES) {
                FilesScreen(
                    isActive = currentRoute == Routes.FILES,
                    onOpenSettings = { navigateTo(Routes.SETTINGS) },
                    transferViewModel = transferViewModel
                )
            }
            composable(Routes.PROJECTS) {
                ProjectsScreen(
                    searchQuery = searchQuery,
                    gridView = gridView,
                    projects = projects,
                    isCreating = creatingProject,
                    errorMessage = projectError,
                    onCreateProject = projectsViewModel::createProject,
                    onOpenProject = { project -> navigateTo("workspace/${project.id}") },
                    onDeleteProject = { project -> projectsViewModel.deleteProject(project.id) },
                    onBeginPublishProject = { project ->
                        transferViewModel.openPublishFolderPicker(project.id)
                    },
                    publishFolderPicker = transferState.publishFolderPicker,
                    onNavigatePublishFolder = transferViewModel::navigatePublishFolderInto,
                    onNavigatePublishParent = transferViewModel::navigatePublishFolderUp,
                    onConfirmPublishFolder = transferViewModel::publishProjectToInternalStorage,
                    onCancelPublishFolder = transferViewModel::closePublishFolderPicker,
                    transferMessage = transferState.message,
                    transferWorking = transferState.isWorking
                )
            }
            composable(Routes.TOOLS) {
                ToolsScreen(
                    onOpenEditor = { navigateTo(Routes.EDITOR) },
                    onOpenArchives = { navigateTo(Routes.ARCHIVES) },
                    onOpenDualSpace = { navigateTo(Routes.DUAL_SPACE) },
                    onOpenAdvancedAccess = { navigateTo(Routes.ADVANCED_ACCESS) },
                    searchQuery = searchQuery,
                    gridView = gridView
                )
            }
            composable(
                route = Routes.WORKSPACE,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { workspaceEntry ->
                val projectId = workspaceEntry.arguments?.getString("projectId")
                val project = projects.firstOrNull { it.id == projectId }
                if (project != null) {
                    WorkspaceScreen(
                        project = project,
                        viewModel = workspaceViewModel,
                        transferViewModel = transferViewModel,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Project workspace not found.", color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            composable(Routes.EDITOR) { EditorScreen() }
            composable(Routes.ARCHIVES) { ArchivesScreen() }
            composable(Routes.DUAL_SPACE) { DualSpaceScreen() }
            composable(Routes.ADVANCED_ACCESS) { AdvancedAccessScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
            .background(if (active) Color(0xFF0B4380) else Color(0xFF0B2C4D))
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(21.dp), tint = if (active) Color(0xFF8CC9FF) else Color(0xFFD9E9FF))
    }
}
