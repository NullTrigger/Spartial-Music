/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.sessions

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.session.ListenTogetherServers
import app.nodes.core.session.RoomStatus
import app.nodes.core.session.RoomUiState
import app.nodes.ui.components.CircleIconButton
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.PillButton
import app.nodes.ui.components.PillStyle
import app.nodes.ui.components.glass
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesType
import app.nodes.ui.theme.NodesWhite

/**
 * Sessions - listen together with friends.
 *
 * One screen covers both roles. Hosting means this device's player drives
 * everyone else's; joining means a room code and the host drives this one. The
 * room code is the whole invite: it is typed, not linked, because a code that
 * has to be read aloud over a chat app is a code that stays six characters.
 */
@Composable
fun SessionsScreen(
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val code by viewModel.code.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()

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
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 180.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "notice") {
                state.notice?.let { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(NodesWhite.copy(alpha = 0.08f))
                            .clickable(viewModel::clearNotice)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = NodesWhite,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.sessions_dismiss),
                            style = MaterialTheme.typography.labelSmall,
                            color = NodesGrey,
                        )
                    }
                }
            }

            if (state.status == RoomStatus.IN_ROOM) {
                item(key = "live") {
                    LiveRoom(
                        state = state,
                        onLeave = viewModel::leave,
                        onApprove = viewModel::approve,
                        onDecline = viewModel::decline,
                    )
                }
            } else {
                item(key = "identity") {
                    FieldCard(
                        label = stringResource(R.string.sessions_name_label),
                        value = name,
                        placeholder = stringResource(R.string.sessions_name_hint),
                        onValueChange = viewModel::setName,
                    )
                }

                item(key = "server") {
                    ServerPicker(
                        selected = server,
                        onSelect = viewModel::setServer,
                    )
                }

                item(key = "create") {
                    PillButton(
                        text = stringResource(R.string.sessions_create),
                        onClick = viewModel::create,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item(key = "join") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(20.dp), scrim = 0.2f)
                            .padding(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.sessions_code_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = NodesGrey,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = code,
                                onValueChange = viewModel::setCode,
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.sessions_code_hint),
                                        color = NodesGrey,
                                    )
                                },
                                textStyle = TextStyle(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 4.sp,
                                    color = NodesWhite,
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NodesWhite.copy(alpha = 0.4f),
                                    unfocusedBorderColor = NodesWhite.copy(alpha = 0.14f),
                                    focusedLabelColor = NodesGrey,
                                ),
                            )
                            Spacer(Modifier.width(10.dp))
                            PillButton(
                                text = stringResource(R.string.sessions_join),
                                onClick = viewModel::join,
                                style = PillStyle.Glass,
                            )
                        }
                    }
                }

                item(key = "privacy") {
                    Text(
                        text = stringResource(R.string.sessions_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = NodesGrey,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveRoom(
    state: RoomUiState,
    onLeave: () -> Unit,
    onApprove: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), scrim = 0.28f)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                if (state.isHost) R.string.sessions_role_host else R.string.sessions_role_guest,
            ).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = NodesGrey,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = state.code,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 8.sp,
            color = NodesWhite,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.sessions_copy_code),
                tint = NodesGrey,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable {
                        clipboard.setText(AnnotatedString(state.code))
                    },
            )
            Text(
                text = stringResource(R.string.sessions_code_share),
                style = MaterialTheme.typography.bodySmall,
                color = NodesGrey,
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.sessions_people, state.members.size + 1),
            style = MaterialTheme.typography.titleMedium,
            color = NodesWhite,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        MemberRow(stringResource(R.string.sessions_you))
        state.members.forEach { member -> MemberRow(member) }

        if (state.pending.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.sessions_pending),
                style = MaterialTheme.typography.labelMedium,
                color = NodesGrey,
                modifier = Modifier.fillMaxWidth(),
            )
            state.pending.forEach { request ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = request.username,
                        style = NodesType.rowTitle,
                        color = NodesWhite,
                        modifier = Modifier.weight(1f),
                    )
                    PillButton(
                        text = stringResource(R.string.sessions_approve),
                        onClick = { onApprove(request.userId) },
                        style = PillStyle.Glass,
                    )
                    Spacer(Modifier.width(8.dp))
                    PillButton(
                        text = stringResource(R.string.sessions_decline),
                        onClick = { onDecline(request.userId) },
                        style = PillStyle.Glass,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(
                text = stringResource(if (state.isHost) R.string.sessions_stop else R.string.sessions_leave),
                onClick = onLeave,
                modifier = Modifier.weight(1f),
                style = PillStyle.Glass,
            )
            Spacer(Modifier.width(10.dp))
            CircleIconButton(
                icon = Icons.Rounded.Logout,
                contentDescription = stringResource(R.string.sessions_leave),
                onClick = onLeave,
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                if (state.isHost) R.string.sessions_host_hint else R.string.sessions_guest_hint,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = NodesGrey,
        )
    }
}

@Composable
private fun MemberRow(name: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(NodesWhite.copy(alpha = 0.55f)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            style = NodesType.rowMeta,
            color = NodesWhite.copy(alpha = 0.86f),
        )
    }
}

@Composable
private fun FieldCard(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), scrim = 0.2f)
            .padding(14.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NodesGrey,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(text = placeholder, color = NodesGrey) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NodesWhite.copy(alpha = 0.4f),
                unfocusedBorderColor = NodesWhite.copy(alpha = 0.14f),
                focusedTextColor = NodesWhite,
                unfocusedTextColor = NodesWhite,
                cursorColor = NodesWhite,
            ),
        )
    }
}

@Composable
private fun ServerPicker(selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = ListenTogetherServers.KNOWN.firstOrNull { it.first == selected }?.second
        ?: stringResource(R.string.sessions_custom_server)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), scrim = 0.2f)
            .padding(14.dp),
    ) {
        Text(
            text = stringResource(R.string.sessions_server_label),
            style = MaterialTheme.typography.labelMedium,
            color = NodesGrey,
        )
        Spacer(Modifier.height(6.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { open = true }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = NodesType.rowTitle,
                    color = NodesWhite,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.sessions_change),
                    style = MaterialTheme.typography.labelSmall,
                    color = NodesGrey,
                )
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
            ) {
                ListenTogetherServers.KNOWN.forEach { (url, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onSelect(url)
                            open = false
                        },
                    )
                }
            }
        }
    }
}
