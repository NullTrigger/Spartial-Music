/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.model.Track
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialWhite

/**
 * The one picker behind every "add to playlist" surface: rows, the queue, the
 * player, the mini player. Tapping a playlist adds there and reports the
 * outcome through [PlaylistPickerViewModel.notice]; "New playlist" creates and
 * adds in one transaction.
 */
@Composable
fun PlaylistPickerDialog(
    track: Track,
    onDismiss: () -> Unit,
    viewModel: PlaylistPickerViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val containing by remember(track.id) {
        viewModel.containingPlaylists(track.id)
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF15171B),
        title = {
            Text(stringResource(R.string.playlist_picker_title), color = SpartialWhite)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (playlists.isEmpty()) {
                    Text(
                        text = stringResource(R.string.library_empty_playlists),
                        style = MaterialTheme.typography.bodySmall,
                        color = SpartialGrey,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                playlists.forEach { playlist ->
                    val alreadyThere = playlist.id in containing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                viewModel.add(track, playlist)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = playlist.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = SpartialWhite,
                            )
                            Text(
                                text = stringResource(R.string.playlist_track_count, playlist.trackCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = SpartialGrey,
                            )
                        }
                        if (alreadyThere) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                                contentDescription = stringResource(R.string.playlist_already_in, playlist.title),
                                tint = SpartialWhite,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                if (creating) {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text(stringResource(R.string.library_playlist_name)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = SpartialWhite,
                            unfocusedTextColor = SpartialWhite,
                            cursorColor = SpartialWhite,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    onClick = {
                        viewModel.createAndAdd(name, track)
                        onDismiss()
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_create))
                }
            } else {
                TextButton(onClick = { creating = true }) {
                    Text(stringResource(R.string.library_new_playlist))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (creating) {
                        creating = false
                        name = ""
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text(stringResource(if (creating) R.string.action_cancel else R.string.action_close))
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}
