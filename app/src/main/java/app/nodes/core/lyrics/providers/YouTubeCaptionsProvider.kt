/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.lyrics.providers

import app.nodes.core.lyrics.LyricsPayload
import app.nodes.core.lyrics.LyricsProvider
import app.nodes.core.lyrics.LyricsQuery
import app.nodes.core.source.youtubemusic.InnerTubeClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The video's own captions as a last-resort lyric source.
 *
 * Every other provider can simply not know a song; YouTube always has text on
 * the video itself. It is the last entry in the order because captions are
 * whatever the uploader typed - formatting, punctuation and even the language
 * are theirs - so a real lyric sheet, when one exists anywhere, beats this.
 *
 * Only YouTube Music tracks can answer (the lookup is keyed by video id);
 * anything else reports a miss immediately rather than paying a network call.
 */
@Singleton
class YouTubeCaptionsProvider @Inject constructor(
    private val innerTube: InnerTubeClient,
) : LyricsProvider {

    override val id: String = "youtube_captions"
    override val displayName: String = "YouTube captions"

    override suspend fun fetch(query: LyricsQuery): LyricsPayload {
        val videoId = query.videoId?.takeIf { it.isNotBlank() }
            ?: return LyricsPayload.NotFound
        val raw = innerTube.transcript(videoId)
            ?: return LyricsPayload.NotFound

        // The captions come from the exact video the user is playing, so the
        // identity is the strongest there is: same video id, same duration.
        return LyricsPayload.Found(
            raw = raw,
            syncedHint = true,
            title = query.title,
            artist = query.artist,
            durationSec = query.durationSec,
            videoId = videoId,
        )
    }
}
