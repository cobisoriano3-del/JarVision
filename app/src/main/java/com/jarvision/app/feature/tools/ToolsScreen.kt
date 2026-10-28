package com.jarvision.app.feature.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class ToolDestination(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun ToolsScreen(
    onOpenEditor: () -> Unit,
    onOpenArchives: () -> Unit,
    onOpenDualSpace: () -> Unit,
    onOpenAdvancedAccess: () -> Unit,
    searchQuery: String = "",
    gridView: Boolean = false
) {
    val allTools = listOf(
        ToolDestination("Universal Editor", "Multi-tab text and code editing.", Icons.Filled.Code, onOpenEditor),
        ToolDestination("Archive Tools", "Create, browse, test, and extract archives.", Icons.Filled.Archive, onOpenArchives),
        ToolDestination("Dual Space", "Optional side-by-side folder windows.", Icons.Filled.ViewWeek, onOpenDualSpace),
        ToolDestination("Advanced Access", "Shizuku capability and permission status.", Icons.Filled.Security, onOpenAdvancedAccess)
    )
    val query = searchQuery.trim()
    val visibleTools = allTools.filter {
        query.isEmpty() || it.title.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
    }
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF020B18), Color(0xFF03172D), Color(0xFF020B18)))
        )
    ) {
        if (visibleTools.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No matching tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Try another search term.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (gridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 158.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleTools, key = { it.title }) { tool -> ToolCard(tool, gridStyle = true) }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                visibleTools.forEach { tool -> ToolCard(tool, gridStyle = false) }
            }
        }
    }
}

@Composable
private fun ToolCard(tool: ToolDestination, gridStyle: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = if (gridStyle) 176.dp else 88.dp).clickable(onClick = tool.onClick),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF123D68)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF051B32)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (gridStyle) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.Start) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolIcon(icon = tool.icon, size = 46.dp)
                    PlannedPill()
                }
                Spacer(Modifier.height(14.dp))
                Text(tool.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolIcon(icon = tool.icon, size = 52.dp)
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(tool.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(tool.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    PlannedPill()
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF83BDF6), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, size: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(15.dp),
        color = Color(0xFF0A3767),
        border = BorderStroke(1.dp, Color(0xFF1A5A96))
    ) {
        Box(
            modifier = Modifier.background(Brush.verticalGradient(listOf(Color(0xFF104982), Color(0xFF06284D)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (size < 50.dp) 24.dp else 27.dp), tint = Color(0xFFD7EBFF))
        }
    }
}

@Composable
private fun PlannedPill() {
    Surface(shape = CircleShape, color = Color(0xFF0A3767), border = BorderStroke(1.dp, Color(0xFF1B5B92))) {
        Text("Planned", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF9BCFFF), maxLines = 1)
    }
}
