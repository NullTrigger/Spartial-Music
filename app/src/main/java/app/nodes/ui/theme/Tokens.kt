/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared spacing scale. Every screen composes from these values. */
object NodesSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp

    /** Horizontal page gutter. */
    val gutter: Dp = 20.dp

    /** Space reserved under content for the mini player + tab bar. */
    val dockSpace: Dp = 172.dp
}

/** Corner radii for cards, sheets, artwork and pills. */
object NodesRadius {
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 22.dp
    val xl: Dp = 28.dp
    val xxl: Dp = 34.dp

    val card: Shape = RoundedCornerShape(18.dp)
    val largeCard: Shape = RoundedCornerShape(26.dp)
    val artwork: Shape = RoundedCornerShape(22.dp)
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill: Shape = CircleShape
}

/** Elevation is expressed as soft shadows rather than Material surface tint. */
object NodesElevation {
    val none: Dp = 0.dp
    val low: Dp = 2.dp
    val medium: Dp = 8.dp
    val high: Dp = 18.dp
    val floating: Dp = 26.dp
}

/** Motion durations shared by the bars, dots and expanding headers. */
object NodesMotion {
    const val quick = 160
    const val standard = 280
    const val slow = 520
}
