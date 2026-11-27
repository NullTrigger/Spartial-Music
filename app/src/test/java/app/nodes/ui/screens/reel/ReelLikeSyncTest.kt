/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.reel

import app.nodes.core.model.SourceId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The like state machine: guest and non-YouTube tracks are local-only;
 * signed-in YouTube Music likes push; unlikes stay local because no unlike
 * request is verified in this project (see REVIEW_NOTES).
 */
class ReelLikeSyncTest {

    @Test
    fun `guest like is local only`() {
        assertEquals(
            LikeAction.LOCAL_ONLY,
            likeAction(
                signedIn = false,
                syncEnabled = true,
                sourceId = SourceId.YOUTUBE_MUSIC,
                liked = true,
            ),
        )
    }

    @Test
    fun `signed-in youtube music like pushes when auto-sync is on`() {
        assertEquals(
            LikeAction.LOCAL_AND_PUSH,
            likeAction(
                signedIn = true,
                syncEnabled = true,
                sourceId = SourceId.YOUTUBE_MUSIC,
                liked = true,
            ),
        )
    }

    @Test
    fun `auto-sync off keeps the like local`() {
        assertEquals(
            LikeAction.LOCAL_ONLY,
            likeAction(
                signedIn = true,
                syncEnabled = false,
                sourceId = SourceId.YOUTUBE_MUSIC,
                liked = true,
            ),
        )
    }

    @Test
    fun `local and youtube tracks never push even when signed in`() {
        for (source in listOf(SourceId.LOCAL, SourceId.YOUTUBE)) {
            assertEquals(
                LikeAction.LOCAL_ONLY,
                likeAction(
                    signedIn = true,
                    syncEnabled = true,
                    sourceId = source,
                    liked = true,
                ),
            )
        }
    }

    @Test
    fun `unlike is local only`() {
        assertEquals(
            LikeAction.LOCAL_ONLY_UNLIKE,
            likeAction(
                signedIn = true,
                syncEnabled = true,
                sourceId = SourceId.YOUTUBE_MUSIC,
                liked = false,
            ),
        )
    }
}
