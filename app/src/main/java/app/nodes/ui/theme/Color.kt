/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.theme

import androidx.compose.ui.graphics.Color

// The redesigned surface stack: near-black, with just enough separation
// between layers to keep cards and controls readable without borders.
val SpartialBlack = Color(0xFF07080A)
val SpartialBlackElevated = Color(0xFF0C0D10)
val SpartialSurface = Color(0xFF131418)
val SpartialSurfaceHigh = Color(0xFF1B1D22)
val SpartialSurfaceHighest = Color(0xFF282A30)

// Type colours.
val SpartialWhite = Color(0xFFF6F6F7)
val SpartialWhiteMuted = Color(0xE6FFFFFF)
val SpartialGrey = Color(0xFF9FA1A8)
val SpartialGreyDim = Color(0xFF6A6C74)

// The neutral accent used by controls, chips and focus rings. Artwork tints
// supply the colour on player and collection screens; chrome stays monochrome.
val SpartialAccent = Color(0xFFF1F2F4)
val SpartialAccentDeep = Color(0xFFB6B8BE)
val SpartialDanger = Color(0xFFFF6B6B)

/**
 * The navigation accent: active-tab icon and label inside the bubble, and the
 * collapsed bar's left circle. Value pending the maintainer's confirmation.
 */
val navAccent = Color(0xFF3DA9FF)

/**
 * Warm and cool accents used by generated placeholder artwork, genre tiles and
 * the deterministic fallback tint when a provider has no cover image.
 */
val ArtAccents = listOf(
    Color(0xFF345A78),
    Color(0xFF7A3941),
    Color(0xFF7A6248),
    Color(0xFF44566B),
    Color(0xFF4E3F72),
    Color(0xFF2F5A50),
    Color(0xFF6C4A32),
    Color(0xFF3D445E),
    Color(0xFF7A3F5D),
)

/** Four-stop tile palettes for the Search genre grids. */
val GenreGradients = listOf(
    listOf(Color(0xFFE2B83E), Color(0xFF8D5C1F)),
    listOf(Color(0xFF173C52), Color(0xFF0B1922)),
    listOf(Color(0xFFD9C6A0), Color(0xFF61513B)),
    listOf(Color(0xFFB73A2D), Color(0xFF4A1412)),
    listOf(Color(0xFF3B2D6E), Color(0xFF130F29)),
    listOf(Color(0xFF2E6F5B), Color(0xFF11271F)),
    listOf(Color(0xFFA35439), Color(0xFF3C1C14)),
    listOf(Color(0xFF36506B), Color(0xFF151F2A)),
)

// Frosted-glass tokens. Glass is a white film over a darkened backdrop.
val GlassFillTop = Color(0x2BFFFFFF)
val GlassFillBottom = Color(0x0FFFFFFF)
val GlassBorder = Color(0x26FFFFFF)
val GlassBorderStrong = Color(0x42FFFFFF)


