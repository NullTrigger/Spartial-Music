/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nodes.R
import app.nodes.core.model.Track
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesType
import app.nodes.ui.theme.NodesWhite

/**
 * Compact now-playing bar that sits above the floating tab bar. The whole
 * capsule opens the full player; the transport controls remain separate touch
 * targets. A hairline at the top carries playback progress without adding a
 * second row of chrome.
 */
@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .glass(RoundedCornerShape(22.dp), strong = true, scrim = 0.82f)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        if (progress > 0f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(NodesWhite.copy(alpha = 0.8f)),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                artworkUri = track.artworkUri,
                title = track.title,
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(13.dp),
                targetPx = 256,
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = NodesType.rowTitle,
                    color = NodesWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = NodesType.rowMeta,
                    color = NodesGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CircleIconButton(
                icon = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.action_previous),
                onClick = onPrevious,
                size = 40.dp,
                iconSize = 20.dp,
                tint = NodesGrey,
            )
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = NodesWhite,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(NodesWhite)
                            .clickable(role = Role.Button, onClick = onToggle)
                            .semantics { role = Role.Button },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.action_pause else R.string.action_play,
                            ),
                            tint = app.nodes.ui.theme.NodesBlack,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            CircleIconButton(
                icon = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.action_next),
                onClick = onNext,
                size = 40.dp,
                iconSize = 20.dp,
                tint = NodesWhite,
            )
        }
    }
}
