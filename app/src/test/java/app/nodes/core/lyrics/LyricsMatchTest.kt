/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.lyrics

import app.nodes.core.data.LyricsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMatchTest {

    private val query = LyricsQuery(
        trackId = "demo:1",
        title = "Toxic",
        artist = "Britney Spears",
        durationSec = 200,
        videoId = "official-video-1",
    )

    @Test
    fun `matching title artist duration and video id is accepted`() {
        assertTrue(
            LyricsRepository.isVerifiedLyricsMatch(
                LyricsPayload.Found(
                    raw = "[00:01.00]Line",
                    title = "TOXIC!",
                    artist = "Britney Spears",
                    durationSec = 203,
                    videoId = "official-video-1",
                ),
                query,
            ),
        )
    }

    @Test
    fun `different title is rejected even with matching artist and duration`() {
        assertFalse(
            LyricsRepository.isVerifiedLyricsMatch(
                LyricsPayload.Found(
                    raw = "[00:01.00]Wrong song",
                    title = "Toxic (Live)",
                    artist = "Britney Spears",
                    durationSec = 200,
                ),
                query,
            ),
        )
    }

    @Test
    fun `different artist is rejected even when title and duration match`() {
        assertFalse(
            LyricsRepository.isVerifiedLyricsMatch(
                LyricsPayload.Found(
                    raw = "[00:01.00]Cover lyrics",
                    title = "Toxic",
                    artist = "A Cover Band",
                    durationSec = 200,
                ),
                query,
            ),
        )
    }

    @Test
    fun `missing duration is allowed only with exact title and artist`() {
        assertTrue(
            LyricsRepository.isVerifiedLyricsMatch(
                LyricsPayload.Found(
                    raw = "Un-timed lyrics",
                    title = "Toxic",
                    artist = "Britney Spears",
                ),
                query,
            ),
        )
        assertFalse(
            LyricsRepository.isVerifiedLyricsMatch(
                LyricsPayload.Found(
                    raw = "[00:01.00]Unverified",
                    title = "Toxic",
                    durationSec = 200,
                ),
                query,
            ),
        )
    }

    @Test
    fun `different video id is rejected when provider supplies one`() {
        assertFalse(
            LyricsRepository.isVerifiedLyricsMatch(
                LyricsPayload.Found(
                    raw = "[00:01.00]Different recording",
                    title = "Toxic",
                    artist = "Britney Spears",
                    durationSec = 200,
                    videoId = "cover-video-2",
                ),
                query,
            ),
        )
    }
}
