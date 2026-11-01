/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.lyrics

/** Everything a provider is allowed to know about the song it is asked for. */
data class LyricsQuery(
    /** Stable id of the track, used to find a local `.lrc` sidecar. */
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSec: Int = 0,
    /** YouTube video id, when the track came from YouTube Music. */
    val videoId: String? = null,
)

/** What one provider answered. Raw text stays raw; [LyricsParser] owns parsing. */
sealed interface LyricsPayload {

    data class Found(
        val raw: String,
        /** The provider claims timestamps; the parser still decides. */
        val syncedHint: Boolean = true,
        /** What the provider says the song is, so a bad match can be refused. */
        val title: String? = null,
        val artist: String? = null,
        val durationSec: Int = 0,
    ) : LyricsPayload

    data object NotFound : LyricsPayload

    data class Failed(val reason: String) : LyricsPayload
}

/**
 * One source of lyrics.
 *
 * Implementations must never throw: a network failure is [LyricsPayload.Failed],
 * a miss is [LyricsPayload.NotFound]. The repository races providers with a
 * timeout, so a provider that blocks is dropped rather than allowed to hang the
 * player.
 */
interface LyricsProvider {

    /** Stable id used in settings; never shown to the user. */
    val id: String

    /** Name shown in the lyrics header when "show source" is on. */
    val displayName: String

    suspend fun fetch(query: LyricsQuery): LyricsPayload
}

/** The ids the app knows about, and the order a fresh install tries them in. */
object LyricsProviders {

    const val LOCAL = "local"
    const val LRCLIB = "lrclib"
    const val SIMPMUSIC = "simp_music"
    const val YOULYPLUS = "youly_plus"

    /**
     * Local files win: the user put them there on purpose. Then the provider
     * that returns word-level timing most often (YouLyPlus), then the most
     * carefully matched index (LRCLIB), then SimpMusic.
     */
    val DEFAULT_ORDER: List<String> = listOf(LOCAL, YOULYPLUS, LRCLIB, SIMPMUSIC)

    /** Providers that need no account or key, so the UI can list them all. */
    val KNOWN: List<String> = DEFAULT_ORDER
}
