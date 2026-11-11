/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.toBackdrop

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
