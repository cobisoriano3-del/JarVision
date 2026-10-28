package com.jarvision.app.feature.files

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import java.util.Locale

@Composable
internal fun FileEntryVisualIcon(
    entry: com.jarvision.app.data.storage.StorageEntry,
    state: FilesUiState,
    filesViewModel: FilesViewModel,
    thumbnailSize: Dp
) {
    if (entry.isDirectory && !isAndroidDataPackageFolder(entry, state)) {
        FolderThumbnail(entry = entry, state = state, thumbnailSize = thumbnailSize)
        return
    }
    val initialKind = defaultEntryVisualKind(entry, state)
    val visualKey = listOf(
        state.accessMode.name,
        state.treeUriString.orEmpty(),
        state.currentPath.lastOrNull()?.documentId.orEmpty(),
        entry.absolutePath ?: entry.documentId,
        entry.displayName,
        entry.sizeBytes?.toString().orEmpty(),
        entry.lastModifiedMillis?.toString().orEmpty()
    ).joinToString("|")
    val visual by produceState(initialValue = EntryVisual(initialKind), key1 = visualKey) {
        try {
            value = filesViewModel.loadEntryVisual(entry, state)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            value = EntryVisual(initialKind)
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(thumbnailSize)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = visual.bitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Preview for ${entry.displayName}",
                contentScale = if (visual.kind == EntryVisualKind.IMAGE || visual.kind == EntryVisualKind.VIDEO) {
                    ContentScale.Crop
                } else ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
        } else {
            Icon(
                imageVector = iconForEntryVisual(visual.kind),
                contentDescription = visual.kind.name.lowercase(Locale.ROOT),
                tint = tintForEntryVisual(visual.kind),
                modifier = Modifier.size(if (thumbnailSize <= 36.dp) 20.dp else 30.dp)
            )
        }
        if (visual.kind == EntryVisualKind.ZIP_UNKNOWN) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(1.dp)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEAF2FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    color = Color(0xFF153A60),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }
    }
}

private fun iconForEntryVisual(kind: EntryVisualKind): ImageVector = when (kind) {
    EntryVisualKind.FOLDER, EntryVisualKind.APP_FOLDER -> Icons.Filled.Folder
    EntryVisualKind.TEXT -> Icons.Filled.Description
    EntryVisualKind.PDF -> Icons.Filled.PictureAsPdf
    EntryVisualKind.APK -> Icons.Filled.Android
    EntryVisualKind.IMAGE -> Icons.Filled.Image
    EntryVisualKind.VIDEO -> Icons.Filled.Movie
    EntryVisualKind.AUDIO -> Icons.Filled.MusicNote
    EntryVisualKind.ARCHIVE, EntryVisualKind.ZIP_UNKNOWN -> Icons.Filled.Archive
    EntryVisualKind.GENERIC -> Icons.Filled.InsertDriveFile
}

@Composable
private fun tintForEntryVisual(kind: EntryVisualKind): Color = when (kind) {
    EntryVisualKind.PDF -> Color(0xFFE57373)
    EntryVisualKind.IMAGE -> Color(0xFF65C985)
    EntryVisualKind.VIDEO -> Color(0xFFB39DDB)
    EntryVisualKind.AUDIO, EntryVisualKind.ARCHIVE, EntryVisualKind.ZIP_UNKNOWN -> Color(0xFFFFB74D)
    EntryVisualKind.GENERIC -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.primary
}
