/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.sessions

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.session.ListenTogetherServers
import app.nodes.core.session.RoomStatus
import app.nodes.core.session.RoomUiState
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.PillButton
import app.nodes.ui.components.PillStyle
import app.nodes.ui.components.glass
import app.nodes.ui.screens.onboarding.OnboardingAvatar
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialType
import app.nodes.ui.theme.SpartialWhite
import java.text.DateFormat
import java.util.Date

/**
 * Sessions - listen together with friends.
 *
 * One screen covers both roles and both states. Out of a room it asks for two
 * things and offers two ways in: a name, and either a code plus Join or Create
 * Session. In a room it shows the same ticket to the host and to everyone who
 * joined it, because the room is the same room whichever end you hold: the
 * code is the invite, printed on the stub.
 *
 * The room code is typed, never linked - a code that has to survive being read
 * aloud over a chat app is a code that stays six characters.
 */
@Composable
fun SessionsScreen(
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val avatarId by viewModel.profileAvatarId.collectAsStateWithLifecycle()
    val code by viewModel.code.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    val pendingConnect by viewModel.pendingConnect.collectAsStateWithLifecycle()

    // One-time, before the first connection: what crosses the socket, and who
    // runs the server. Asked again never; answered "not now", nothing connects.
    if (pendingConnect != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConnect,
            containerColor = Color(0xFF15171B),
            title = {
                Text(stringResource(R.string.sessions_consent_title), color = SpartialWhite)
            },
            text = {
                Text(
                    text = stringResource(R.string.sessions_consent_body),
                    color = SpartialGrey,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmConnect) {
                    Text(stringResource(R.string.sessions_consent_accept), color = SpartialWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConnect) {
                    Text(stringResource(R.string.sessions_consent_decline), color = SpartialGrey)
                }
            },
            shape = RoundedCornerShape(28.dp),
        )
    }

    val inRoom = state.status == RoomStatus.IN_ROOM && state.code.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .background(SpartialBlack)
            .imePadding(),
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
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "notice") {
                state.notice?.let { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SpartialWhite.copy(alpha = 0.08f))
                            .clickable(onClick = viewModel::clearNotice)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = SpartialWhite,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.sessions_dismiss),
                            style = MaterialTheme.typography.labelSmall,
                            color = SpartialGrey,
                        )
                    }
                }
            }

            if (inRoom) {
                item(key = "ticket") {
                    SessionTicket(state = state, avatarId = avatarId, name = name)
                }

                item(key = "leave") {
                    PillButton(
                        text = stringResource(
                            if (state.isHost) R.string.sessions_stop else R.string.sessions_leave_session,
                        ),
                        onClick = viewModel::leave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }

                if (state.isHost && state.pending.isNotEmpty()) {
                    item(key = "pending") {
                        PendingCard(
                            names = state.pending.map { it.username },
                            onApprove = { index -> viewModel.approve(state.pending[index].userId) },
                            onDecline = { index -> viewModel.decline(state.pending[index].userId) },
                        )
                    }
                }

                item(key = "role-hint") {
                    Text(
                        text = stringResource(
                            if (state.isHost) R.string.sessions_host_hint else R.string.sessions_guest_hint,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = SpartialGrey,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                item(key = "art") {
                    // The three listeners. Decorative: the name field right
                    // below says who you are in the room.
                    Image(
                        painter = painterResource(R.drawable.sessions_group),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                    )
                }

                item(key = "name") {
                    PillField(
                        value = name,
                        onValueChange = viewModel::setName,
                        placeholder = stringResource(R.string.sessions_name_placeholder),
                        avatarId = avatarId,
                        imeAction = ImeAction.Next,
                    )
                }

                item(key = "code-join") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PillField(
                            value = code,
                            onValueChange = viewModel::setCode,
                            placeholder = stringResource(R.string.sessions_code_placeholder),
                            imeAction = ImeAction.Done,
                            onDone = viewModel::join,
                            letterSpacing = 4.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        PillButton(
                            text = stringResource(R.string.sessions_join),
                            onClick = viewModel::join,
                        )
                    }
                }

                item(key = "create") {
                    PillButton(
                        text = stringResource(R.string.sessions_create),
                        onClick = viewModel::create,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }

                item(key = "help") {
                    Text(
                        text = stringResource(R.string.sessions_ticket_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = SpartialGrey,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                item(key = "server") {
                    ServerPicker(
                        selected = server,
                        onSelect = viewModel::setServer,
                    )
                }

                item(key = "privacy") {
                    Text(
                        text = stringResource(R.string.sessions_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = SpartialGrey,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * The ticket. Same card for the host and for everyone who joined with the code,
 * because both ends hold the same room: who is playing, how many are in it,
 * when this device got in, what is on now, and the code on the stub.
 */
@Composable
private fun SessionTicket(
    state: RoomUiState,
    avatarId: Int,
    name: String,
) {
    val hostName = state.hostName.ifBlank { name }
    val from = remember(state.startedAtMs) {
        if (state.startedAtMs <= 0L) "" else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.startedAtMs))
    }
    val track = state.playingTrack

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpartialWhite, RoundedCornerShape(30.dp))
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Image(
            painter = painterResource(R.drawable.sessions_ticket_mark),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(20.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.sessions_ticket_title, hostName),
            style = TextStyle(
                fontFamily = SpartialType.rowTitle.fontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = TicketInk,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Perforation()

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TicketLabel(stringResource(R.string.sessions_ticket_members, state.members.size + 1))
            Spacer(Modifier.width(16.dp))
            TicketLabel(stringResource(R.string.sessions_ticket_from, from))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.sessions_ticket_playing),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = TicketLabelColor,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = track?.let { "${it.title}${if (it.artist.isBlank()) "" else " (${it.artist})"}" }
                ?: stringResource(R.string.sessions_ticket_none),
            style = TextStyle(
                fontFamily = SpartialType.rowTitle.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = TicketInk,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Perforation()

        Text(
            text = state.code,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = SpartialType.rowTitle.fontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                letterSpacing = 6.sp,
                color = TicketInk,
            ),
        )
        Spacer(Modifier.height(8.dp))
        TicketBarcode(code = state.code, modifier = Modifier.fillMaxWidth().height(48.dp))
        Spacer(Modifier.height(18.dp))
    }
}

/** The dashed cut line, with the punch holes that bite into its ends. */
@Composable
private fun Perforation() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val inset = 18.dp.toPx()
            val y = size.height / 2f
            drawLine(
                color = Color(0xFFB9B9BE),
                start = Offset(inset, y),
                end = Offset(size.width - inset, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 9f)),
            )
        }
        // The holes are the screen showing through the card, so they wear the
        // page colour rather than a fake shadow.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-11).dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(SpartialBlack),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 11.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(SpartialBlack),
        )
    }
}

/**
 * The stub's barcode.
 *
 * Decorative: the pattern is derived from the code so the same room always
 * prints the same bars, but it is deliberately not a scannable symbology -
 * nobody should be aiming a camera at a phone to join a listening room when
 * they can type six characters.
 */
@Composable
private fun TicketBarcode(code: String, modifier: Modifier = Modifier) {
    val bars = remember(code) { barcodeModules(code) }
    val total = bars.sum().coerceAtLeast(1)
    Canvas(modifier) {
        val unit = size.width / total
        var x = 0f
        var black = true
        for (module in bars) {
            if (black) {
                drawRect(color = TicketInk, topLeft = Offset(x, 0f), size = Size(unit * module, size.height))
            }
            x += unit * module
            black = !black
        }
    }
}

private fun barcodeModules(code: String): List<Int> {
    val out = mutableListOf(2, 1, 2) // quiet start + guard
    code.forEachIndexed { index, char ->
        val seed = char.code + index * 31
        repeat(4) { bit -> out += 1 + ((seed shr bit) and 3) }
    }
    out += 2
    out += 1
    out += 2
    return out
}

/** The grey pill the reference uses for both typed fields. */
@Composable
private fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
    avatarId: Int? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF6E6E73))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (avatarId != null) {
                OnboardingAvatar(
                    id = avatarId,
                    diameter = 30.dp,
                    contentDescription = stringResource(R.string.sessions_you_avatar),
                )
                Spacer(Modifier.width(10.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontFamily = SpartialType.rowTitle.fontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    letterSpacing = letterSpacing,
                    color = SpartialWhite,
                ),
                cursorBrush = SolidColor(SpartialWhite),
                keyboardOptions = KeyboardOptions(imeAction = imeAction, autoCorrectEnabled = false),
                // Only a field with an action overrides the keyboard: "Next"
                // on the name field must move focus, not create a room the
                // moment a listener finishes typing their name.
                keyboardActions = onDone?.let { done ->
                    KeyboardActions(onDone = { done() })
                } ?: KeyboardActions(),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(
                                    fontFamily = SpartialType.rowTitle.fontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = Color(0xFFE2E2E7),
                                ),
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/** Host-only: the people waiting at the door. */
@Composable
private fun PendingCard(
    names: List<String>,
    onApprove: (Int) -> Unit,
    onDecline: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), scrim = 0.2f)
            .padding(14.dp),
    ) {
        Text(
            text = stringResource(R.string.sessions_pending),
            style = MaterialTheme.typography.labelMedium,
            color = SpartialGrey,
        )
        names.forEachIndexed { index, who ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = who,
                    style = SpartialType.rowTitle,
                    color = SpartialWhite,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = stringResource(R.string.sessions_approve),
                    onClick = { onApprove(index) },
                    style = PillStyle.Glass,
                )
                Spacer(Modifier.width(8.dp))
                PillButton(
                    text = stringResource(R.string.sessions_decline),
                    onClick = { onDecline(index) },
                    style = PillStyle.Glass,
                )
            }
        }
    }
}

@Composable
private fun TicketLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = TicketLabelColor,
    )
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
            color = SpartialGrey,
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
                    style = SpartialType.rowTitle,
                    color = SpartialWhite,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.sessions_change),
                    style = MaterialTheme.typography.labelSmall,
                    color = SpartialGrey,
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

private val TicketInk = Color(0xFF0B0B0C)
private val TicketLabelColor = Color(0xFF8B7629)
