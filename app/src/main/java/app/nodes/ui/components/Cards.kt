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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nodes.R
import app.nodes.core.model.Track
import app.nodes.ui.theme.GenreGradients
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialType
import app.nodes.ui.theme.SpartialWhite
import kotlin.math.abs

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
    // The rounded clip belongs to the artwork alone. Clipping the whole column
    // turned the card's bottom corners into a mask over the two text lines,
    // which shaved the first letter off anything short ("Ella Langley" drew as
    // "lla Langley", "Playlist" as "laylist").
    Column(
        modifier = modifier
            .width(width)
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
 * A colourful browse tile: a gradient, a procedurally drawn motif and a
 * label. The palette is seeded by [index] so a grid has rhythm without
 * shipping any artwork.
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
            // The motif in place of a flat initial: a little spectrum, drawn
            // from the title so every tile has its own shape.
            TileSpectrum(title = title, modifier = Modifier.matchParentSize())
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

/**
 * The decoration on a browse tile: a small spectrum analyser drawn from the
 * title, so every tile has its own shape instead of a flat initial.
 *
 * The bars are seeded by the title's hash, so the same genre always draws the
 * same pattern and a grid of tiles never repeats itself.
 */
@Composable
private fun TileSpectrum(title: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val seed = abs(title.hashCode())
        val barCount = 7
        val minDimension = size.minDimension
        val barWidth = minDimension * 0.052f
        val gap = barWidth * 0.62f
        val totalWidth = barCount * barWidth + (barCount - 1) * gap
        val bottom = size.height * 0.88f
        val maxHeight = minDimension * 0.44f
        var x = size.width - totalWidth - minDimension * 0.07f

        for (index in 0 until barCount) {
            // A cheap deterministic spread of heights from the seed.
            val noise = ((seed shr (index * 3)) and 0x3F) / 63f
            // The middle of the spectrum is the tallest, like a real one.
            val shape = 1f - abs(index - barCount / 2f) / (barCount / 2f)
            val height = maxHeight * (0.22f + 0.78f * (noise * 0.55f + shape * 0.45f))
            drawRoundRect(
                color = Color.White.copy(alpha = 0.16f),
                topLeft = Offset(x, bottom - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
            x += barWidth + gap
        }

        // A thin ring inside the soft circle above, like a record's groove.
        drawCircle(
            color = Color.White.copy(alpha = 0.10f),
            radius = minDimension * 0.30f,
            center = Offset(size.width * 0.84f, size.height * 0.18f),
            style = Stroke(width = minDimension * 0.012f),
        )
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
    // As in ArtworkCard: no clip across the label, or the rounded corner eats
    // the first letter of the artist's name.
    Column(
        modifier = modifier
            .width(size + 16.dp)
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
