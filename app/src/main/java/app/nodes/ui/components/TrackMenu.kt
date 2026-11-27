/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nodes.R
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialWhite
import app.nodes.core.model.Track
import app.nodes.core.model.SourceId
import app.nodes.playback.OfflineDownloadViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.delay

/**
 * The track "more" menu used by list rows and the queue.
 *
 * Play next and Add to queue are Glossy's two queue actions: the first inserts
 * after the playing track, the second appends. Neither replaces the queue with
 * the collection the row belongs to, which is what made picking a track look
 * like the same list was being loaded again.
 *
 * [onViewArtist] and [onViewAlbum] open the collection pages when the caller
 * can navigate there; both are optional so list-only screens keep the same
 * two-queue-actions menu.
 *
 * [onAddToPlaylist] opens the playlist picker; [onRemoveFromPlaylist] is only
 * supplied by a local playlist's own track list.
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
    onViewArtist: (() -> Unit)? = null,
    onViewAlbum: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null,
    removeEnabled: Boolean = true,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    moveUpEnabled: Boolean = true,
    moveDownEnabled: Boolean = true,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    downloadTrack: Track? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.player_queue_options),
                tint = SpartialGrey,
                modifier = Modifier.size(20.dp),
            )
        }
        TrackMenuDropdown(
            expanded = expanded,
            onDismiss = { expanded = false },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onViewArtist = onViewArtist,
            onViewAlbum = onViewAlbum,
            onAddToPlaylist = onAddToPlaylist,
            onRemoveFromQueue = onRemoveFromQueue,
            removeEnabled = removeEnabled,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            moveUpEnabled = moveUpEnabled,
            moveDownEnabled = moveDownEnabled,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            downloadTrack = downloadTrack,
        )
    }
}

/**
 * The menu itself, hostable without the trailing button - the mini player
 * opens it from a long-press instead.
 */
@Composable
fun TrackMenuDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onViewArtist: (() -> Unit)? = null,
    onViewAlbum: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null,
    removeEnabled: Boolean = true,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    moveUpEnabled: Boolean = true,
    moveDownEnabled: Boolean = true,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    downloadTrack: Track? = null,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
            if (onViewArtist != null) {
                QueueMenuItem(
                    label = stringResource(R.string.player_view_artist),
                    icon = Icons.Rounded.Person,
                ) {
                    onDismiss()
                    onViewArtist()
                }
            }
            if (onViewAlbum != null) {
                QueueMenuItem(
                    label = stringResource(R.string.player_view_album),
                    icon = Icons.Rounded.Album,
                ) {
                    onDismiss()
                    onViewAlbum()
                }
            }
            if (downloadTrack?.sourceId == SourceId.YOUTUBE_MUSIC && downloadTrack.videoId != null) {
                HorizontalDivider()
                OfflineDownloadMenuItem(track = downloadTrack)
            }
            QueueMenuItem(
                label = stringResource(R.string.player_play_next),
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            ) {
                onDismiss()
                onPlayNext()
            }
            QueueMenuItem(
                label = stringResource(R.string.player_add_to_queue),
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            ) {
                onDismiss()
                onAddToQueue()
            }
            if (onAddToPlaylist != null) {
                QueueMenuItem(
                    label = stringResource(R.string.player_add_to_playlist),
                    icon = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                ) {
                    onDismiss()
                    onAddToPlaylist()
                }
            }
            if (onMoveUp != null || onMoveDown != null) {
                HorizontalDivider()
                if (onMoveUp != null) {
                    QueueMenuItem(
                        label = stringResource(R.string.player_reorder_up),
                        icon = Icons.Rounded.ArrowUpward,
                        enabled = moveUpEnabled,
                    ) {
                        onDismiss()
                        onMoveUp()
                    }
                }
                if (onMoveDown != null) {
                    QueueMenuItem(
                        label = stringResource(R.string.player_reorder_down),
                        icon = Icons.Rounded.ArrowDownward,
                        enabled = moveDownEnabled,
                    ) {
                        onDismiss()
                        onMoveDown()
                    }
                }
            }
            if (onRemoveFromQueue != null) {
                HorizontalDivider()
                QueueMenuItem(
                    label = stringResource(R.string.player_remove_from_queue),
                    icon = Icons.Rounded.RemoveCircleOutline,
                    enabled = removeEnabled,
                ) {
                    onDismiss()
                    onRemoveFromQueue()
                }
            }
            if (onRemoveFromPlaylist != null) {
                HorizontalDivider()
                QueueMenuItem(
                    label = stringResource(R.string.playlist_remove_track),
                    icon = Icons.Rounded.DeleteOutline,
                ) {
                    onDismiss()
                    onRemoveFromPlaylist()
                }
            }
        }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(androidx.media3.common.util.UnstableApi::class)
private fun OfflineDownloadMenuItem(track: Track) {
    val viewModel: OfflineDownloadViewModel = hiltViewModel()
    val downloads by viewModel.downloads.downloads.collectAsStateWithLifecycle()
    val errors by viewModel.downloads.errors.collectAsStateWithLifecycle()
    val download = downloads[track.id]
    val completed = download?.state == Download.STATE_COMPLETED
    val failed = download?.state == Download.STATE_FAILED
    val pending = download != null && !completed && !failed
    // Tapping "Save offline" first resolves the stream - a network round trip
    // before any Download exists. Say so instead of sitting on a stale row.
    var starting by remember(track.id) { mutableStateOf(false) }
    // Media3 advances a download's percentage in place on the live Download
    // and only calls its listeners when its state changes; the map can even
    // hold a frozen copy of one. Re-read the manager's live download once a
    // second while the save is in flight.
    val downloadState = download?.state
    var percent by remember(download) {
        mutableIntStateOf(download?.percentDownloaded?.toInt()?.coerceIn(0, 100) ?: 0)
    }
    LaunchedEffect(download, downloadState) {
        if (download == null || downloadState == Download.STATE_COMPLETED ||
            downloadState == Download.STATE_FAILED
        ) return@LaunchedEffect
        while (true) {
            val live = viewModel.downloads.liveDownload(track.id) ?: download
            percent = live.percentDownloaded.toInt().coerceIn(0, 100)
            delay(1_000)
        }
    }
    val label = when {
        completed -> R.string.offline_download_remove
        pending -> R.string.offline_download_cancel
        else -> R.string.offline_download
    }
    QueueMenuItem(
        label = when {
            completed -> stringResource(R.string.offline_download_saved)
            download?.state == Download.STATE_DOWNLOADING -> stringResource(
                R.string.offline_download_progress,
                percent,
            )
            download?.state == Download.STATE_QUEUED -> stringResource(R.string.offline_download_queued)
            starting && download == null && errors[track.id] == null ->
                stringResource(R.string.offline_download_starting)
            else -> stringResource(label)
        },
        icon = if (completed) Icons.Rounded.DeleteOutline else Icons.Rounded.Download,
        onClick = {
            when {
                completed || pending -> viewModel.remove(track.id)
                else -> {
                    starting = true
                    viewModel.enqueue(track)
                }
            }
        },
    )
    errors[track.id]?.let { errorRes ->
        Text(
            text = stringResource(errorRes),
            style = MaterialTheme.typography.bodySmall,
            color = SpartialGrey,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
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
                color = if (enabled) SpartialWhite else SpartialGrey.copy(alpha = 0.5f),
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) SpartialGrey else SpartialGrey.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        },
        enabled = enabled,
        onClick = onClick,
    )
}
