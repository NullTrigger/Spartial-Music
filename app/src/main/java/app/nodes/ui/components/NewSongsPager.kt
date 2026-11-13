/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nodes.R
import app.nodes.core.model.Track
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesRadius
import app.nodes.ui.theme.NodesSpacing
import app.nodes.ui.theme.NodesType
import app.nodes.ui.theme.NodesWhite
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * "New songs you may like": one suggestion per page, the next one peeking from
 * the right edge.
 *
 * The artwork is never cropped: the card is a fixed-height stage, the artwork
 * scales to fit inside it, and whatever area it does not cover shows a very
 * dark, heavily dimmed copy of the same image rather than a flat colour or a
 * bright blur.
 */
@Composable
fun NewSongsPager(
    songs: List<Track>,
    loading: Boolean,
    onPlay: (Track, List<Track>) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NodesSpacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_new_songs_title),
                style = NodesType.sectionTitle.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = NodesWhite,
            )
            Spacer(Modifier.width(6.dp))
            // The chevron rides right after the heading text, not at the far
            // edge: it opens the full suggestions list, and is inert when the
            // list is empty.
            val enabled = songs.isNotEmpty()
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = if (enabled) {
                    stringResource(R.string.home_new_songs_open)
                } else {
                    null
                },
                tint = if (enabled) NodesWhite else NodesWhite.copy(alpha = 0.25f),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onSeeAll)
                    .padding(2.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        if (songs.isEmpty()) {
            // Loading: one dark stage the size of a card, no fake rows.
            Box(
                Modifier
                    .padding(horizontal = NodesSpacing.gutter)
                    .fillMaxWidth()
                    .height(CARD_MAX_HEIGHT)
                    .clip(NodesRadius.artwork)
                    .background(if (loading) NodesWhite.copy(alpha = 0.03f) else Color.Transparent),
            )
            return@Column
        }

        val pagerState = rememberPagerState(pageCount = { songs.size })
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = NodesSpacing.gutter),
            pageSpacing = 12.dp,
        ) { page ->
            val track = songs[page]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NodesRadius.artwork)
                    .background(NodesBlack)
                    .clickable(role = Role.Button) { onPlay(track, songs) },
            ) {
                ArtworkStage(track = track)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = NodesType.rowTitle.copy(fontSize = 16.sp),
                            color = NodesWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            style = NodesType.rowMeta,
                            color = NodesWhite.copy(alpha = 0.70f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.action_play),
                        tint = NodesWhite.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** Card stage: 16:9 of the card width, never taller than the design cap. */
private val CARD_MAX_HEIGHT = 230.dp

/**
 * One card: a fixed 16:9 stage (never taller than [CARD_MAX_HEIGHT]), with the
 * whole artwork fit inside, centred, aspect preserved - nothing is cropped.
 * The area the artwork does not cover shows a heavily dimmed, blurred copy of
 * the same image (the blur needs API 31; below it the dim alone carries it).
 */
@Composable
private fun ArtworkStage(track: Track) {
    val context = LocalContext.current
    // 16:9 of the card width, bounded by the design's maximum height; the
    // ratio is computed inside the constraint scope, which is where maxWidth
    // lives.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stageHeight = minOf(maxWidth * 9f / 16f, CARD_MAX_HEIGHT)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .height(stageHeight),
        ) {
        // The dimmed, blurred backdrop: the same artwork scaled to cover.
        if (!track.artworkUri.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(track.artworkUri)
                    .size(400)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // A no-op below API 31, where the dim alone carries it.
                    .blur(24.dp),
                alpha = 0.16f,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(NodesBlack.copy(alpha = 0.55f)),
            )
        }
        // The artwork itself: fit, never cropped, centred.
        Artwork(
            artworkUri = track.artworkUri,
            title = track.title,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(10.dp),
            contentScale = ContentScale.Fit,
            targetPx = 900,
        )
        }
    }
}
