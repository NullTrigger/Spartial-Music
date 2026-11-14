/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nodes.R
import app.nodes.core.model.Track
import app.nodes.ui.theme.GenreGradients
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialType
import app.nodes.ui.theme.SpartialWhite

/**
 * A square artwork card with the title and artist underneath, as used by the
 * "New songs you may like" rail and the playlist grid.
 */
@Composable
fun ArtworkCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
    showMeta: Boolean = true,
    subtitle: String? = null,
    showPlay: Boolean = false,
) {
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp)),
        ) {
            Artwork(
                artworkUri = track.artworkUri,
                title = track.title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                targetPx = 512,
            )
            if (showPlay) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(CircleShape)
                        .background(SpartialBlack.copy(alpha = 0.58f))
                        .padding(7.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = SpartialWhite,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        if (showMeta) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = track.title,
                style = SpartialType.rowTitle,
                color = SpartialWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle ?: track.artist,
                style = SpartialType.rowMeta,
                color = SpartialGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A wide artwork card for "Continue Playing": art on the left, copy on the
 * right, on a quiet glass slab.
 */
@Composable
fun ContinuePlayingCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 270.dp,
) {
    Row(
        modifier = modifier
            .width(width)
            .glass(RoundedCornerShape(20.dp), scrim = 0.25f)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            artworkUri = track.artworkUri,
            title = track.title,
            modifier = Modifier
                .width(82.dp)
                .aspectRatio(1f),
            shape = RoundedCornerShape(15.dp),
            targetPx = 320,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = SpartialType.rowTitle,
                color = SpartialWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = SpartialType.rowMeta,
                color = SpartialGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.continue_playing),
                style = MaterialTheme.typography.labelSmall,
                color = SpartialGrey.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * A colourful browse tile: a gradient, an oversized initial and a label. The
 * palette is seeded by [index] so a grid has rhythm without shipping any
 * artwork.
 */
@Composable
fun GenreTile(
    title: String,
    onClick: () -> Unit,
    index: Int,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    artworkUri: String? = null,
) {
    val gradient = GenreGradients[Math.floorMod(index, GenreGradients.size)]
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        if (artworkUri != null) {
            Artwork(
                artworkUri = artworkUri,
                title = title,
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(16.dp),
                targetPx = 512,
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                gradient.first().copy(alpha = 0.28f),
                                Color.Black.copy(alpha = 0.58f),
                            ),
                        ),
                    ),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(gradient)),
            )
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = size.minDimension * 0.48f,
                    center = Offset(size.width * 0.84f, size.height * 0.18f),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.12f),
                    radius = size.minDimension * 0.38f,
                    center = Offset(size.width * 0.18f, size.height * 0.92f),
                )
            }
            Text(
                text = title.firstOrNull()?.uppercase() ?: "N",
                color = Color.White.copy(alpha = 0.17f),
                fontSize = 76.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 1.dp),
            )
        }
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SpartialWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = SpartialWhite.copy(alpha = 0.64f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Circular artist avatar used by the Library artist rail. */
@Composable
fun ArtistAvatar(
    artist: String,
    artworkUri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
) {
    Column(
        modifier = modifier
            .width(size + 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            artworkUri = artworkUri,
            title = artist,
            modifier = Modifier.size(size),
            shape = CircleShape,
            targetPx = 320,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist,
            style = SpartialType.rowMeta,
            color = SpartialWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
