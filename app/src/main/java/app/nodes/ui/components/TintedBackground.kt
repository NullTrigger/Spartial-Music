/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.toBackdrop
import app.nodes.ui.theme.toPlayerDeep
import app.nodes.ui.theme.toPlayerWash

/**
 * Full-screen artwork tint: a saturated wash at the top that fades into the
 * near-black base. This is the defining surface of Now Playing, Lyrics,
 * Playlist, Album and Artist.
 */
@Composable
fun TintedBackdrop(
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val wash = tint.toBackdrop()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NodesBlack)
            .drawWithContent {
                drawRect(NodesBlack)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to wash.copy(alpha = 0.92f),
                        0.34f to wash.copy(alpha = 0.74f),
                        0.62f to tint.copy(alpha = 0.28f),
                        1f to NodesBlack,
                    ),
                    size = size,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.32f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.82f, size.height * 0.06f),
                        radius = size.width * 0.9f,
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.82f, size.height * 0.06f),
                )
                drawContent()
            },
        content = content,
    )
}

/**
 * The player backdrop from the reference design.
 *
 * A low-resolution copy of the cover is stretched over the whole screen. That
 * is a real blur, not a translucent black rectangle: the image is decoded at
 * roughly 160px and then scaled to the device width, so it is soft on every API
 * level (including 26, where RenderEffect does not exist). On the controls page
 * a sharp copy of the same cover sits on the top two thirds, and a tinted
 * gradient carries it into the controls at the bottom. Lyrics and Queue use only
 * the soft copy, which is why they read as one continuous wash of the artwork.
 */
@Composable
fun PlayerBackdrop(
    artworkUri: String?,
    title: String,
    tint: Color,
    showArtwork: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val wash = remember(tint) { tint.toPlayerWash() }
    val deep = remember(tint) { tint.toPlayerDeep() }
    Box(modifier.fillMaxSize().background(deep)) {
        if (!artworkUri.isNullOrBlank()) {
            Artwork(
                artworkUri = artworkUri,
                title = title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.16f
                        scaleY = 1.16f
                        alpha = if (showArtwork) 0.92f else 0.58f
                    },
                shape = RectangleShape,
                contentScale = ContentScale.Crop,
                targetPx = 160,
            )
        }

        if (showArtwork && !artworkUri.isNullOrBlank()) {
            Artwork(
                artworkUri = artworkUri,
                title = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.68f)
                    .align(Alignment.TopCenter),
                shape = RectangleShape,
                contentScale = ContentScale.Crop,
                targetPx = 1280,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.68f)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.56f to Color.Transparent,
                            0.78f to wash.copy(alpha = 0.58f),
                            1f to wash.copy(alpha = 0.96f),
                        ),
                    ),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        0.18f to Color.Transparent,
                        0.46f to wash.copy(alpha = 0.16f),
                        0.62f to wash.copy(alpha = 0.54f),
                        0.80f to wash.copy(alpha = 0.84f),
                        1f to deep,
                    ),
                ),
        )

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val centre = Offset(size.width * 0.86f, size.height * 0.04f)
                    val radius = size.width * 0.86f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(wash.copy(alpha = 0.24f), Color.Transparent),
                            center = centre,
                            radius = radius,
                        ),
                        radius = radius,
                        center = centre,
                    )
                },
        )
        content()
    }
}

/**
 * Blurred artwork backdrop for album pages. API 26 cannot use a real backdrop
 * blur, so the artwork is scaled past the edges, dimmed and washed with its own
 * dominant colour; the result reads as a soft, out-of-focus cover.
 */
@Composable
fun BlurredArtworkBackdrop(
    artworkUri: String?,
    title: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(NodesBlack)) {
        Artwork(
            artworkUri = artworkUri,
            title = title,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.28f
                    scaleY = 1.28f
                    alpha = 0.52f
                },
            shape = RectangleShape,
            contentScale = ContentScale.Crop,
            targetPx = 1280,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.44f),
                        0.45f to tint.toBackdrop().copy(alpha = 0.62f),
                        0.78f to NodesBlack.copy(alpha = 0.94f),
                        1f to NodesBlack,
                    ),
                ),
        )
    }
}
