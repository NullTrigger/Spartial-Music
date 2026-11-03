/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.library

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.ui.components.ArtworkSwatch
import app.nodes.ui.components.EmptyPanel
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.TrackMenuButton
import app.nodes.ui.components.TrackRow
import app.nodes.ui.components.glass
import app.nodes.ui.components.glassPill
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesWhite

private enum class LibraryTab(val labelRes: Int) {
    PLAYLISTS(R.string.library_playlists),
    LIKED(R.string.library_liked),
    DOWNLOADS(R.string.library_downloads),
}

@Composable
fun LibraryScreen(
    onOpenPlaylist: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenStats: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val liked by viewModel.likedTracks.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(LibraryTab.PLAYLISTS) }
    var showCreate by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        PageHeader(title = stringResource(R.string.library_title))

        // Listening stats lives beside Sessions as a push-entry from the
        // library; it has no tab of its own.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp)
                .glass(RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenStats)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.stats_entry_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = NodesWhite,
                )
                Text(
                    text = stringResource(R.string.stats_entry_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = NodesGrey,
                )
            }
            Text(
                text = stringResource(R.string.stats_entry_open),
                style = MaterialTheme.typography.labelMedium,
                color = NodesGrey,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Sessions lost its own tab when the bar made room for Search. It lives
        // here so the feature keeps a way in instead of disappearing.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp)
                .glass(RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenSessions)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sessions_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = NodesWhite,
                )
                Text(
                    text = stringResource(R.string.sessions_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = NodesGrey,
                )
            }
            Text(
                text = stringResource(R.string.sessions_open),
                style = MaterialTheme.typography.labelMedium,
                color = NodesGrey,
            )
        }

        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryTab.entries.forEach { entry ->
                val selected = tab == entry
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) Color.White.copy(alpha = 0.16f)
                            else Color.White.copy(alpha = 0.05f),
                        )
                        .clickable { tab = entry }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(entry.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) NodesWhite else NodesGrey,
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 176.dp),
        ) {
            when (tab) {
                LibraryTab.PLAYLISTS -> {
                    item(key = "new") {
                        Row(
                            Modifier
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                .glassPill()
                                .clickable { showCreate = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = NodesWhite,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.library_new_playlist),
                                style = MaterialTheme.typography.labelLarge,
                                color = NodesWhite,
                            )
                        }
                    }

                    items(items = playlists, key = { it.id }) { playlist ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPlaylist(playlist.id) }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtworkSwatch(title = playlist.title, size = 56.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = playlist.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NodesWhite,
                                )
                                Text(
                                    text = "${playlist.trackCount} tracks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NodesGrey,
                                )
                            }
                        }
                    }

                    if (playlists.isEmpty()) {
                        item(key = "empty-playlists") {
                            EmptyPanel(
                                title = stringResource(R.string.library_playlists),
                                description = stringResource(R.string.search_idle),
                            )
                        }
                    }
                }

                LibraryTab.LIKED -> {
                    item(key = "liked-heading") {
                        Text(
                            text = "${liked.size} songs",
                            style = MaterialTheme.typography.labelMedium,
                            color = NodesGrey,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                    items(items = liked, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            onClick = { playerViewModel.play(track, liked) },
                            trailing = {
                                TrackMenuButton(
                                    onPlayNext = { playerViewModel.playNext(track) },
                                    onAddToQueue = { playerViewModel.addToQueue(track) },
                                )
                            },
                        )
                    }
                    if (liked.isEmpty()) {
                        item(key = "empty-liked") {
                            EmptyPanel(
                                title = stringResource(R.string.library_liked),
                                description = stringResource(R.string.library_empty_liked),
                            )
                        }
                    }
                }

                LibraryTab.DOWNLOADS -> {
                    item(key = "downloads") {
                        EmptyPanel(
                            title = stringResource(R.string.library_downloads),
                            description = stringResource(R.string.library_empty_downloads),
                        )
                    }
                    item(key = "downloads-note") {
                        Text(
                            text = stringResource(R.string.library_download_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = NodesGrey,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = Color(0xFF15171B),
            title = { Text(stringResource(R.string.library_new_playlist), color = NodesWhite) },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.library_playlist_name)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = NodesWhite,
                        unfocusedTextColor = NodesWhite,
                        cursorColor = NodesWhite,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createPlaylist(name)
                        showCreate = false
                    },
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
