/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nodes.ui.theme.GlassBorder
import app.nodes.ui.theme.GlassBorderStrong
import app.nodes.ui.theme.GlassFillBottom
import app.nodes.ui.theme.GlassFillTop
import app.nodes.ui.theme.NodesBlack

/**
 * Frosted glass.
 *
 * Real backdrop blur needs RenderEffect (API 31+). Rather than shipping a
 * library that pretends otherwise, Nodes layers three things that read as glass
 * on every API level:
 *
 *  1. a blurred, darkened copy of the artwork behind the surface (see
 *     [app.nodes.ui.components.ArtworkBackdrop]),
 *  2. a translucent white vertical gradient film,
 *  3. a hairline highlight border and a soft corner radius.
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(28.dp),
    borderWidth: Dp = 1.dp,
    borderColor: Color = GlassBorder,
    strong: Boolean = false,
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            colors = listOf(
                GlassFillTop.copy(alpha = if (strong) 0.30f else GlassFillTop.alpha),
                GlassFillBottom.copy(alpha = if (strong) 0.16f else GlassFillBottom.alpha),
            ),
        ),
    )
    .border(borderWidth, if (strong) GlassBorderStrong else borderColor, shape)

/** Glass for a pill button: slightly brighter, hairline top highlight. */
fun Modifier.glassPill(
    borderColor: Color = GlassBorder,
): Modifier = glass(shape = CircleShape, borderColor = borderColor, strong = true)

/**
 * The black scrim that lets the hero image fade into the page. Applied on top
 * of the image, inside its clip.
 */
fun Modifier.bottomFade(scrim: Color = NodesBlack): Modifier = this.background(
    Brush.verticalGradient(
        0f to Color.Transparent,
        0.42f to Color.Transparent,
        0.62f to scrim.copy(alpha = 0.35f),
        0.82f to scrim.copy(alpha = 0.88f),
        1f to scrim,
    ),
)

/** A soft radial vignette to keep text legible over busy artwork. */
fun Modifier.centreVignette(): Modifier = this.background(
    Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.25f),
        0.5f to Color.Black.copy(alpha = 0.18f),
        1f to Color.Transparent,
    ),
)

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val base = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Box(
        modifier = base.glass(shape = shape, strong = strong),
        content = content,
    )
}
