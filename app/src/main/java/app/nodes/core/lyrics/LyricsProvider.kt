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
        /** Provider's recording/video id, when it can attest a direct lookup. */
        val videoId: String? = null,
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
    const val NETEASE = "netease"
    const val LRCLIB = "lrclib"
    const val SIMPMUSIC = "simp_music"
    const val LYRICS_OVH = "lyrics_ovh"
    const val KUGOU = "kugou"
    const val BETTER_LYRICS = "better_lyrics"
    const val LYRICS_PLUS = "lyrics_plus"
    const val PAXSENIX = "paxsenix"
    const val YOUTUBE_CAPTIONS = "youtube_captions"

    /**
     * Local files win: the user put them there on purpose. Then the word-level
     * sources that actually sing along - BetterLyrics and LyricsPlus - and
     * Paxsenix, whose Apple Music payloads are syllable-timed but need a token
     * scrape per session. LRCLIB follows: its exact-match endpoint is the least
     * likely of the line-synced sources to hand back another song. SimpMusic is
     * keyed by video id and is the best line-synced source for YouTube Music
     * tracks, NetEase and KuGou cover Asian and international catalogues,
     * Lyrics.ovh is plain text, and the video's own captions are the floor:
     * always there for a YouTube track, but only ever as good as the uploader
     * typed them.
     */
    val DEFAULT_ORDER: List<String> = listOf(
        LOCAL,
        BETTER_LYRICS,
        LYRICS_PLUS,
        PAXSENIX,
        LRCLIB,
        SIMPMUSIC,
        NETEASE,
        KUGOU,
        LYRICS_OVH,
        YOUTUBE_CAPTIONS,
    )

    /** Providers that need no account or key, so the UI can list them all. */
    val KNOWN: List<String> = DEFAULT_ORDER
}
