/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.nodes.R
import app.nodes.core.lyrics.LyricsProviders

/**
 * Trending regions offered in Settings. This is the ISO 3166-1 alpha-2 code the
 * YouTube Data API expects, paired with the name shown to the user. Only region
 * mode 1 uses it; the InnerTube source ignores it.
 */
internal val YoutubeRegions: List<Pair<String, Int>> = listOf(
    "US" to R.string.region_us,
    "GB" to R.string.region_gb,
    "CA" to R.string.region_ca,
    "AU" to R.string.region_au,
    "IN" to R.string.region_in,
    "DE" to R.string.region_de,
    "FR" to R.string.region_fr,
    "JP" to R.string.region_jp,
    "BR" to R.string.region_br,
    "KR" to R.string.region_kr,
)

/** The lyrics sources the user can order and switch off. Local files always run. */
internal val LyricsSourceRows: List<Pair<String, Int>> = listOf(
    LyricsProviders.YOULYPLUS to R.string.lyrics_provider_youly_plus,
    LyricsProviders.LRCLIB to R.string.lyrics_provider_lrclib,
    LyricsProviders.SIMPMUSIC to R.string.lyrics_provider_simp_music,
)

@Composable
internal fun regionLabel(code: String): String {
    val id = YoutubeRegions.firstOrNull { it.first == code }?.second ?: R.string.region_us
    return stringResource(id)
}

@Composable
internal fun providerLabel(id: String?): String {
    val label = LyricsSourceRows.firstOrNull { it.first == id }?.second
        ?: if (id == LyricsProviders.LOCAL) R.string.lyrics_provider_local else R.string.lyrics_provider_none
    return stringResource(label)
}
