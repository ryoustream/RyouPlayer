package com.ryoustream.player.presentation.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ryoustream.player.domain.model.MediaItem

/**
 * 3-dot context menu for a video card.
 */
@Composable
fun VideoOptionsMenu(
    item: MediaItem,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onProperties: () -> Unit,
) {
    val context = LocalContext.current

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text        = { Text("Play") },
            leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
            onClick     = { onPlay(); onDismiss() },
        )
        DropdownMenuItem(
            text        = { Text(if (item.isFavorite) "Remove from Favorites" else "Add to Favorites") },
            leadingIcon = {
                Icon(
                    if (item.isFavorite) Icons.Outlined.HeartBroken
                    else Icons.Outlined.FavoriteBorder, null
                )
            },
            onClick = { onToggleFavorite(); onDismiss() },
        )
        DropdownMenuItem(
            text        = { Text("Add to Playlist") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null) },
            onClick     = { onAddToPlaylist(); onDismiss() },
        )
        DropdownMenuItem(
            text        = { Text("Share") },
            leadingIcon = { Icon(Icons.Outlined.Share, null) },
            onClick     = {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "video/*"
                    putExtra(Intent.EXTRA_STREAM, item.uri)
                    putExtra(Intent.EXTRA_TITLE, item.displayName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(i, "Share Video"))
                onDismiss()
            },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text        = { Text("Properties") },
            leadingIcon = { Icon(Icons.Outlined.Info, null) },
            onClick     = { onProperties(); onDismiss() },
        )
    }
}

/**
 * Properties dialog showing video metadata.
 */
@Composable
fun VideoPropertiesDialog(item: MediaItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "Name"       to item.displayName,
                    "Duration"   to item.durationFormatted,
                    "Size"       to item.sizeFormatted,
                    "Resolution" to if (item.width > 0) "${item.width}×${item.height}" else "–",
                    "Format"     to item.mimeType,
                    "Folder"     to item.folderName,
                    "Path"       to item.path,
                    "Plays"      to "${item.playCount}×",
                ).forEach { (label, value) ->
                    if (value.isNotEmpty() && value != "–" || label == "Resolution") {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "$label: ",
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(80.dp),
                            )
                            Text(
                                value,
                                style    = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
