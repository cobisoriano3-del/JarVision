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
import androidx.compose.material3.Icon
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
fun ProjectsScreen(searchQuery: String = "", gridView: Boolean = false) {
    val searching = searchQuery.isNotBlank()
    val emblemSize = if (gridView) 124.dp else 156.dp
    val emblemCoreSize = if (gridView) 92.dp else 116.dp
    val folderIconSize = if (gridView) 58.dp else 72.dp
    val badgeSize = if (gridView) 34.dp else 42.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF020B18), Color(0xFF03152A), Color(0xFF020B18))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
        ) {
            if (!searching) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, Color(0xFF1684F6).copy(alpha = 0.78f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF064A9B),
                                        Color(0xFF075EC7),
                                        Color(0xFF087DFA)
                                    )
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF087DFA),
                            border = BorderStroke(1.dp, Color(0xFF45A6FF)),
                            shadowElevation = 4.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF139AFF), Color(0xFF0563D9))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp),
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Create Project",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = "Start a new project and bring your ideas to life.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFC5E1FF),
                                maxLines = 2
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFFEAF5FF)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                ProjectsDivider(emphasized = true)
            } else {
                Spacer(Modifier.height(8.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(emblemSize)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0xFF0B3B70),
                                        Color(0xFF062648),
                                        Color(0xFF03152A)
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(emblemCoreSize),
                            shape = RoundedCornerShape(32.dp),
                            color = Color(0xFF06264A),
                            border = BorderStroke(1.dp, Color(0xFF2076C4)),
                            shadowElevation = 8.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF0B427A), Color(0xFF05213E))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(folderIconSize),
                                    tint = Color(0xFF2998FF)
                                )
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(3.dp)
                                        .size(badgeSize),
                                    shape = CircleShape,
                                    color = Color(0xFF087DFA),
                                    border = BorderStroke(2.dp, Color(0xFF06264A)),
                                    shadowElevation = 4.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(if (gridView) 20.dp else 26.dp),
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = if (searching) "No projects found" else "Projects",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF0F6FF),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (searching) {
                            "Try another search or clear the search field."
                        } else {
                            "Your projects will appear here after you create one."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFA8C8EB),
                        textAlign = TextAlign.Center
                    )
                }
            }

            ProjectsDivider()
            Spacer(Modifier.height(8.dp))
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
