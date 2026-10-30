/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.theme

import androidx.compose.ui.graphics.Color

// Near-black base. Nodes is a dark-first app; these are the only surfaces that
// exist in the reference design.
val NodesBlack = Color(0xFF050506)
val NodesBlackElevated = Color(0xFF0A0B0D)
val NodesSurface = Color(0xFF101115)
val NodesSurfaceHigh = Color(0xFF17191E)
val NodesSurfaceHighest = Color(0xFF202329)

// Type colours.
val NodesWhite = Color(0xFFF6F7F7)
val NodesWhiteMuted = Color(0xE6FFFFFF)
val NodesGrey = Color(0xFF9A9DA4)
val NodesGreyDim = Color(0xFF6B6E75)

// Brand accent (the avatar teal from the reference).
val NodesTeal = Color(0xFF14B3A1)
val NodesTealDeep = Color(0xFF0C7D71)

// Warm and cool accents used only by generated placeholder artwork.
val ArtAccents = listOf(
    Color(0xFF2E4A62),
    Color(0xFF5A3B57),
    Color(0xFF23433C),
    Color(0xFF5B4636),
    Color(0xFF3B3F63),
    Color(0xFF553A3A),
    Color(0xFF2F4B4F),
    Color(0xFF46406B),
)

// Frosted-glass tokens. Glass is a white film over a blurred backdrop.
val GlassFillTop = Color(0x26FFFFFF)
val GlassFillBottom = Color(0x0DFFFFFF)
val GlassBorder = Color(0x24FFFFFF)
val GlassBorderStrong = Color(0x3DFFFFFF)
