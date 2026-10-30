/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nodes.R
import app.nodes.ui.components.EmptyPanel
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.glass
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesTeal
import app.nodes.ui.theme.NodesWhite

private data class SessionRoom(
    val id: String,
    val name: String,
    val host: String,
    val listeners: Int,
    val nowPlaying: String,
)

/**
 * Sessions - live listen-together rooms.
 *
 * This screen is a designed stub: the room list, creation flow and join
 * affordances are real UI, but there is no shared playback backend yet. Keeping
 * the stub honest is better than shipping a fake "connected" state.
 */
@Composable
fun SessionsScreen() {
    val rooms = remember {
        mutableStateListOf(
            SessionRoom("stub-1", "Late night drive", "Nadia", 7, "Midnight Static"),
            SessionRoom("stub-2", "Kitchen radio", "Ivo", 3, "Paper Lanterns"),
            SessionRoom("stub-3", "Sunday slow", "Ari", 12, "Low Tide"),
        )
    }
    var showCreate by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        PageHeader(
            title = stringResource(R.string.sessions_title),
            subtitle = stringResource(R.string.sessions_subtitle),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 150.dp),
        ) {
            item(key = "notice") {
                EmptyPanel(
                    title = stringResource(R.string.sessions_title),
                    description = stringResource(R.string.sessions_stub_notice),
                )
            }

            item(key = "create") {
                Row(
                    Modifier
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .glass(CircleShape)
                        .clickable { showCreate = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = NodesWhite,
                        modifier = Modifier.height(18.dp).width(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.sessions_create),
                        style = MaterialTheme.typography.labelLarge,
                        color = NodesWhite,
                    )
                }
            }

            items(items = rooms, key = { it.id }) { room ->
                SessionCard(
                    room = room,
                    onJoin = { notice = "Joining rooms is not wired up yet." },
                )
            }

            if (rooms.isEmpty()) {
                item(key = "empty") {
                    EmptyPanel(
                        title = stringResource(R.string.sessions_title),
                        description = stringResource(R.string.sessions_empty),
                    )
                }
            }

            notice?.let { message ->
                item(key = "notice-join") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = NodesTeal,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = Color(0xFF15171B),
            title = { Text(stringResource(R.string.sessions_create), color = NodesWhite) },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Session name") },
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
                        rooms.add(
                            0,
                            SessionRoom(
                                id = "local-${rooms.size}",
                                name = name.ifBlank { "Untitled session" },
                                host = "You",
                                listeners = 1,
                                nowPlaying = "Nothing yet",
                            ),
                        )
                        showCreate = false
                        notice = "Session created locally. Shared playback is not implemented yet."
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

@Composable
private fun SessionCard(room: SessionRoom, onJoin: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .glass(RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = room.name,
                style = MaterialTheme.typography.titleMedium,
                color = NodesWhite,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.session_listeners, room.listeners),
                    style = MaterialTheme.typography.labelSmall,
                    color = NodesTeal,
                )
            }
        }
        Text(
            text = "Hosted by ${room.host}",
            style = MaterialTheme.typography.bodySmall,
            color = NodesGrey,
        )
        Text(
            text = stringResource(R.string.session_now_playing, room.nowPlaying),
            style = MaterialTheme.typography.bodySmall,
            color = NodesWhite.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .glass(CircleShape)
                .clickable(onClick = onJoin)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.sessions_join),
                style = MaterialTheme.typography.labelLarge,
                color = NodesWhite,
            )
        }
    }
}
