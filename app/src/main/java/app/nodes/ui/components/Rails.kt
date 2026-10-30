/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nodes.core.model.Track
import app.nodes.core.util.formatDuration
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesTeal
import app.nodes.ui.theme.NodesWhite

/** "Continue Playing" style heading with an optional chevron. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = NodesWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NodesGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = NodesGrey,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Continue Playing card: square art on the left, two lines of type on the right,
 * on a quiet glass slab.
 */
@Composable
fun ContinuePlayingCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .width(272.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            artworkUri = track.artworkUri,
            title = track.title,
            modifier = Modifier.size(68.dp),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = NodesWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = NodesGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.durationMs > 0L) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatDuration(track.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = NodesGrey.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** Large rounded artwork card for "New songs you may like". */
@Composable
fun AlbumCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 248.dp,
    height: androidx.compose.ui.unit.Dp = 168.dp,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        Artwork(
            artworkUri = track.artworkUri,
            title = track.title,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(24.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = NodesWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = NodesWhite.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(34.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = NodesWhite,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A list row used by Trending, Library, Search and playlist screens. */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    position: Int? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (position != null) {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = NodesGrey,
                modifier = Modifier.width(28.dp),
            )
        }
        Artwork(
            artworkUri = track.artworkUri,
            title = track.title,
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NodesWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (track.sourceId == app.nodes.core.model.SourceId.YOUTUBE) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "YouTube",
                        style = MaterialTheme.typography.labelSmall,
                        color = NodesTeal,
                    )
                }
            }
            Text(
                text = buildString {
                    append(track.artist)
                    if (track.album != null) append(" · ${track.album}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = NodesGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        } else if (track.durationMs > 0L) {
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = NodesGrey,
            )
        }
    }
}

/**
 * The soft edge glow that hints at the next card in a horizontal rail.
 */
@Composable
fun RailEdgeGlow(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(36.dp)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    1f to NodesTeal.copy(alpha = 0.22f),
                ),
            ),
    )
}

/** Empty / status panel used across screens. */
@Composable
fun EmptyPanel(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .glass(shape = RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = NodesWhite,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = NodesGrey,
        )
    }
}
