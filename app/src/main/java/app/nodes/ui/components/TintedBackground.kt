/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import android.os.Build
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import app.nodes.ui.theme.EDGE_FADE_MS
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.ArtworkPalette
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
 * The Now Playing backdrop: the reference layout, in layers.
 *
 *  - **The blurred bed.** A heavily blurred, slightly enlarged copy of the
 *    artwork, drained of most of its saturation and covered by the palette's
 *    dominant colour. It fills the whole screen, so the whole lower half
 *    reads as one soft, monochrome-tinted wash.
 *  - **The sharp art.** The cover itself, edge to edge, behind the status bar,
 *    with the top corners rounded like a phone frame. An alpha mask dissolves
 *    it into the bed, so there is no hard edge where the art ends.
 *
 * The two layers live in one captured [hazeState] source and sit beside the
 * controls rather than above them, so the glass buttons read the artwork and
 * the gradient they actually stand on.
 */
@Composable
fun PlayerEdgeBackdrop(
    artworkUri: String?,
    title: String,
    palette: ArtworkPalette,
    showArtwork: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    // The sharp art reaches past the 60% mark so its fade can finish at 70%
    // of the screen, with the visible, undissolved cover filling the top 52%.
    val artHeight = (screenHeightDp * ART_FADE_END).dp
    val fadeStart = (FADE_START / ART_FADE_END).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize()) {
        // One captured layer: the blurred bed and the sharp art, so the glass
        // controls blur the artwork and the gradient behind them.
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            // The bed: blurred, desaturated art under a dominant-colour wash.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(palette.deep),
            ) {
                if (!artworkUri.isNullOrBlank()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            // Drain most of the colour so the wash reads as one
                            // monochrome tint rather than as a second picture.
                            // Colour filters need API 31; below that the bed
                            // keeps the cover's own colours.
                            .graphicsLayer {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    renderEffect = android.graphics.RenderEffect
                                        .createColorFilterEffect(
                                            android.graphics.ColorMatrixColorFilter(
                                                android.graphics.ColorMatrix().apply {
                                                    setSaturation(MONOCHROME_SATURATION)
                                                },
                                            ),
                                        )
                                        .asComposeRenderEffect()
                                }
                            },
                    ) {
                        Artwork(
                            artworkUri = artworkUri,
                            title = title,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(BED_BLUR_RADIUS, BlurredEdgeTreatment.Rectangle)
                                .scale(BED_SCALE),
                            shape = RectangleShape,
                            contentScale = ContentScale.Crop,
                            targetPx = 1024,
                            crossfadeMillis = EDGE_FADE_MS,
                        )
                    }
                }
                // The dominant colour over the bed, then a gradient down into
                // the deep shade so the bottom of the screen holds the hue.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(palette.dominant.copy(alpha = DOMINANT_OVERLAY)),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to palette.dominant.copy(alpha = 0.20f),
                                0.42f to palette.dominant.copy(alpha = 0.42f),
                                0.72f to palette.dominant.copy(alpha = 0.50f),
                                1f to palette.deep,
                            ),
                        ),
                )
            }

            // The sharp art.
            if (showArtwork && !artworkUri.isNullOrBlank()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(artHeight)
                        .align(Alignment.TopCenter)
                        // The phone frame: rounded top corners.
                        .clip(RoundedCornerShape(topStart = FRAME_CORNER, topEnd = FRAME_CORNER))
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            // The alpha mask: opaque over the cover, then a
                            // long smooth fade so the art dissolves into the
                            // bed with no visible edge.
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.White,
                                    fadeStart to Color.White,
                                    // Eased out, so the cover melts into the
                                    // bed instead of fading at a constant rate.
                                    (fadeStart + (1f - fadeStart) * 0.35f) to
                                        Color.White.copy(alpha = 0.62f),
                                    (fadeStart + (1f - fadeStart) * 0.7f) to
                                        Color.White.copy(alpha = 0.24f),
                                    1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                ) {
                    Artwork(
                        artworkUri = artworkUri,
                        title = title,
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape,
                        contentScale = ContentScale.Crop,
                        targetPx = 1280,
                        // The cover crossfades in step with the palette.
                        crossfadeMillis = EDGE_FADE_MS,
                    )
                }
            }
        }
        content()
    }
}

/** Where the art starts to dissolve into the bed, as a fraction of the screen. */
private const val FADE_START = 0.52f

/** Where the art has dissolved completely, as a fraction of the screen. */
private const val ART_FADE_END = 0.70f

/** The bed's blur radius. */
private val BED_BLUR_RADIUS = 70.dp

/** The bed's enlargement, so the blur has something to smear. */
private const val BED_SCALE = 1.15f

/** How much colour is left in the bed before the tint goes over it. */
private const val MONOCHROME_SATURATION = 0.35f

/** The dominant colour's share over the bed. */
private const val DOMINANT_OVERLAY = 0.40f

/** The screen's top corners, rounded like a phone frame. */
private val FRAME_CORNER = 30.dp

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
