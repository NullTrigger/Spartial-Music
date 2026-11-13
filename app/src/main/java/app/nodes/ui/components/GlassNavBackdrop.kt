/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Shared glass styling for the floating bar and the mini player.
 *
 * API 31+: a real backdrop blur through Haze - ~28dp radius (inside the
 * 24–32dp window), a dark tint at 28% so labels stay legible over bright
 * artwork, and 6% noise for grain. The white 9% sheen and the border are
 * drawn on top and are identical on both branches.
 *
 * Below API 31 Haze cannot blur; the spec's fallback is the dark tinted
 * surface at ~75%, same border and highlight, used there only.
 */
object GlassNav {

    val blurStyle = HazeStyle(
        backgroundColor = Color(0xFF07080A),
        tints = listOf(HazeTint(Color.Black.copy(alpha = 0.28f))),
        blurRadius = 28.dp,
        noiseFactor = 0.06f,
    )

    val fallbackTint = HazeTint(Color.Black.copy(alpha = 0.75f))

    /** Sheen with a faint top-edge inner highlight, as one subtle gradient. */
    val sheen: Brush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.14f),
        0.18f to Color.White.copy(alpha = 0.09f),
        1f to Color.White.copy(alpha = 0.07f),
    )

    /** 1dp border, brighter at the top-left, dimmer at the bottom-right. */
    val border: Brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.40f),
            Color.White.copy(alpha = 0.10f),
        ),
    )
}

/**
 * The glass surface for the floating bar and mini player: soft shadow, a real
 * backdrop blur through [hazeEffect] when [hazeState] is present, then the
 * sheen and the gradient border on top.
 *
 * Performance: on API 31+ the blur is a RenderEffect the GPU composites; Haze
 * samples the backdrop down before blurring, so the cost is one extra layer
 * compositing per frame while the content behind the surface moves, and
 * nothing when it does not. There is no per-pixel work on the UI thread to
 * jank the scroll; the fallback branch below API 31 is a plain tint.
 */
fun glassNavBackdrop(base: Modifier, hazeState: HazeState?, shape: Shape): Modifier =
    base
        .shadow(elevation = 12.dp, shape = shape, clip = false)
        .clip(shape)
        .then(
            if (hazeState != null) {
                Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = GlassNav.blurStyle.backgroundColor,
                        tints = GlassNav.blurStyle.tints,
                        blurRadius = GlassNav.blurStyle.blurRadius,
                        noiseFactor = GlassNav.blurStyle.noiseFactor,
                        fallbackTint = GlassNav.fallbackTint,
                    ),
                )
            } else {
                Modifier.background(Color.Black.copy(alpha = 0.75f))
            },
        )
        .background(GlassNav.sheen)
        .border(width = 1.dp, brush = GlassNav.border, shape = shape)
