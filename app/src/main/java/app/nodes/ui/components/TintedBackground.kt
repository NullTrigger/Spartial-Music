/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * The player backdrop: a pure, smooth colour gradient derived from the
 * artwork's dominant tint.
 *
 * There is no stretched copy of the cover behind the controls, lyrics or queue
 * any more. A pixel-scaled artwork wash reads as a dirty blur on real devices
 * and cuts the cover in half on the controls page; the gradient does the same
 * job cleanly on every API level, with no RenderEffect, no decoding and no
 * visible seam. The cover itself is shown sharp and whole as a rounded card by
 * the controls page.
 */
@Composable
fun PlayerBackdrop(
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val wash = remember(tint) { tint.toPlayerWash() }
    val deep = remember(tint) { tint.toPlayerDeep() }
    Box(modifier.fillMaxSize().background(deep)) {
        // The main wash: saturated colour up top, fading into the deep base.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to wash.copy(alpha = 0.62f),
                        0.30f to wash.copy(alpha = 0.34f),
                        0.58f to tint.copy(alpha = 0.14f),
                        0.80f to deep.copy(alpha = 0.92f),
                        1f to deep,
                    ),
                ),
        )

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    // A warm glow from the top-right, and a cooler counter-glow
                    // at the bottom-left, so the gradient has depth rather than
                    // reading as one flat colour band.
                    val topCentre = Offset(size.width * 0.84f, size.height * 0.06f)
                    val topRadius = size.width * 0.95f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(tint.copy(alpha = 0.40f), Color.Transparent),
                            center = topCentre,
                            radius = topRadius,
                        ),
                        radius = topRadius,
                        center = topCentre,
                    )
                    val lowCentre = Offset(size.width * 0.08f, size.height * 0.94f)
                    val lowRadius = size.width * 0.72f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(wash.copy(alpha = 0.20f), Color.Transparent),
                            center = lowCentre,
                            radius = lowRadius,
                        ),
                        radius = lowRadius,
                        center = lowCentre,
                    )
                },
        )

        // A light scrim under the status bar so the top buttons stay readable
        // whatever colour the artwork happens to be.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.34f),
                        0.16f to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                ),
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
