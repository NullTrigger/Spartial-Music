/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import app.nodes.ui.theme.SpartialBlack
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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
            .background(SpartialBlack)
            .drawWithContent {
                drawRect(SpartialBlack)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to wash.copy(alpha = 0.92f),
                        0.34f to wash.copy(alpha = 0.74f),
                        0.62f to tint.copy(alpha = 0.28f),
                        1f to SpartialBlack,
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
 * Gradient-only backdrop used on player modes that should not show the cover.
 * The whole-screen tint remains shared with the full-bleed controls and
 * playlist surfaces.
 */
@Composable
fun PlayerBackdrop(
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PlayerBackdrop(
        artworkUri = null,
        title = "",
        tint = tint,
        showArtwork = false,
        modifier = modifier,
        content = content,
    )
}

/**
 * The full-bleed player backdrop from the reference screen.
 *
 * The whole surface is a rich, saturated colour wash derived from the cover's
 * dominant tint - no stretched low-res copy of the artwork anywhere, so Lyrics
 * and Queue sit on a completely clean gradient. When [showArtwork] is true,
 * the cover itself fills the top half of the screen edge-to-edge and is
 * dissolved into that wash with an alpha mask (`BlendMode.DstIn` inside an
 * offscreen layer), so its bottom edge melts smoothly into the colour instead
 * of ending at a horizontal line.
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
        // The full-screen colour wash: saturated up top, holding its hue all
        // the way down behind the dock.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to wash,
                        0.46f to wash.copy(alpha = 0.92f),
                        0.76f to deep.copy(alpha = 0.96f),
                        1f to deep,
                    ),
                ),
        )

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val topCentre = Offset(size.width * 0.50f, size.height * 0.42f)
                    val topRadius = size.width * 0.95f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(wash.copy(alpha = 0.55f), Color.Transparent),
                            center = topCentre,
                            radius = topRadius,
                        ),
                        radius = topRadius,
                        center = topCentre,
                    )
                },
        )

        if (showArtwork && !artworkUri.isNullOrBlank()) {
            Artwork(
                artworkUri = artworkUri,
                title = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        // Alpha mask: opaque over the portrait, then a long
                        // smooth S-curve fade to transparent so the cover
                        // dissolves into the wash with no visible bottom edge.
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                0.54f to Color.Black,
                                0.72f to Color.Black.copy(alpha = 0.78f),
                                0.86f to Color.Black.copy(alpha = 0.32f),
                                0.95f to Color.Black.copy(alpha = 0.06f),
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                shape = RectangleShape,
                contentScale = ContentScale.Crop,
                targetPx = 1280,
            )
        }

        // A soft haze across the seam where the title sits, so the transition
        // reads as mist rather than as an edge.
        if (showArtwork) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.18f),
                            0.14f to Color.Transparent,
                            0.44f to Color.Transparent,
                            0.58f to wash.copy(alpha = 0.72f),
                            0.68f to wash.copy(alpha = 0.94f),
                            1f to deep,
                        ),
                    ),
            )
        }
        content()
    }
}

/**
 * The player backdrop built on the artwork's own bottom-edge colour: the
 * artwork fills the screen width (aspect preserved, letterbox filled with the
 * edge colour), an eased fade dissolves only its bottom quarter into that
 * colour, and the area below is a gradient from the edge colour to the same
 * colour darkened. No hue shift anywhere, and never dark text: the colour is
 * deepened at sampling time until white text clears 4.5:1.
 */
@Composable
fun PlayerEdgeBackdrop(
    artworkUri: String?,
    edgeColour: Color,
    showArtwork: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to edgeColour,
                    1f to edgeColour.copy(
                        red = edgeColour.red * 0.85f,
                        green = edgeColour.green * 0.85f,
                        blue = edgeColour.blue * 0.85f,
                    ),
                ),
            ),
    ) {
        if (showArtwork && !artworkUri.isNullOrBlank()) {
            val stageHeight = (LocalConfiguration.current.screenHeightDp * 0.62f)
                .dp
                .coerceIn(420.dp, 560.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(stageHeight)
                    .align(Alignment.TopCenter)
                    // The artwork + fade is what the glass controls read.
                    .hazeSource(hazeState),
            ) {
                // Fit to width: the aspect ratio survives, nothing zooms; the
                // letterbox above and below is the edge colour already.
                Artwork(
                    artworkUri = artworkUri,
                    title = "",
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    contentScale = ContentScale.FillWidth,
                    targetPx = 1280,
                )
                // Eased (smoothstep-like) fade over the bottom ~25% only; the
                // rest of the artwork is never tinted.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.75f to Color.Transparent,
                                0.85f to edgeColour.copy(alpha = 0.25f),
                                0.93f to edgeColour.copy(alpha = 0.65f),
                                1f to edgeColour,
                            ),
                        ),
                )
            }
        }
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
    Box(modifier.fillMaxSize().background(SpartialBlack)) {
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
                        0.78f to SpartialBlack.copy(alpha = 0.94f),
                        1f to SpartialBlack,
                    ),
                ),
        )
    }
}
