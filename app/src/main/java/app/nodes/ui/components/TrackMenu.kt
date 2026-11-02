/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nodes.R
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesWhite

/**
 * The track "more" menu used by list rows and the queue.
 *
 * Play next and Add to queue are Glossy's two queue actions: the first inserts
 * after the playing track, the second appends. Neither replaces the queue with
 * the collection the row belongs to, which is what made picking a track look
 * like the same list was being loaded again.
 *
 * [onRemoveFromQueue] is only supplied by the queue itself; [removeEnabled] is
 * false for the playing row because removing it would leave the player without
 * an item.
 */
@Composable
fun TrackMenuButton(
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveFromQueue: (() -> Unit)? = null,
    removeEnabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.player_queue_options),
                tint = NodesGrey,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            QueueMenuItem(
                label = stringResource(R.string.player_play_next),
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            ) {
                expanded = false
                onPlayNext()
            }
            QueueMenuItem(
                label = stringResource(R.string.player_add_to_queue),
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            ) {
                expanded = false
                onAddToQueue()
            }
            if (onRemoveFromQueue != null) {
                HorizontalDivider()
                QueueMenuItem(
                    label = stringResource(R.string.player_remove_from_queue),
                    icon = Icons.Rounded.RemoveCircleOutline,
                    enabled = removeEnabled,
                ) {
                    expanded = false
                    onRemoveFromQueue()
                }
            }
        }
    }
}

@Composable
private fun QueueMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) NodesWhite else NodesGrey.copy(alpha = 0.5f),
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) NodesGrey else NodesGrey.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        },
        enabled = enabled,
        onClick = onClick,
    )
}
