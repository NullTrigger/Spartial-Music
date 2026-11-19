/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.reel

import app.nodes.core.model.SourceId
import app.nodes.core.model.Track

/**
 * The Reel's feed math, kept free of Android and coroutine types so it can
 * be tested plainly. The feed IS the playback queue, so everything here is
 * just list hygiene: dedupe by id, exclude what the user has seen, and
 * interleave so one artist never floods the pager.
 */
object ReelFeed {

    /**
     * Round-robin over per-artist lists: take one song from each followed
     * artist in turn until [limit] songs are gathered. Ids already in
     * [exclude] (the queue plus the last played tracks) are skipped, and no
     * id appears twice in the result.
     */
    fun interleaveByArtist(
        byArtist: List<List<Track>>,
        exclude: Set<String>,
        limit: Int,
    ): List<Track> {
        val out = ArrayList<Track>(limit)
        val seen = HashSet<String>()
        val cursors = IntArray(byArtist.size)
        var exhausted = false
        while (out.size < limit && !exhausted) {
            exhausted = true
            for (index in byArtist.indices) {
                val source = byArtist[index]
                while (cursors[index] < source.size) {
                    val track = source[cursors[index]]
                    cursors[index]++
                    if (track.id in exclude || !seen.add(track.id)) continue
                    out += track
                    exhausted = false
                    break
                }
                if (out.size >= limit) break
            }
        }
        return out
    }

    /**
     * A refill batch: append [candidates] after the ones already queued or
     * recently played, deduped against [exclude] and each other, capped at
     * [limit].
     */
    fun batch(candidates: List<Track>, exclude: Set<String>, limit: Int): List<Track> {
        val seen = HashSet(exclude)
        val out = ArrayList<Track>(limit)
        for (track in candidates) {
            if (out.size >= limit) break
            if (seen.add(track.id)) out += track
        }
        return out
    }
}

/** What a like tap should do beyond the local database. */
enum class LikeAction {
    /** Guest, or a local/Jamendo track: the Room write is the whole story. */
    LOCAL_ONLY,

    /** Signed in and the track is a YouTube Music track: push the like. */
    LOCAL_AND_PUSH,

    /**
     * Unliking. The app has no verified InnerTube unlike request, so this
     * stays local; see REVIEW_NOTES.
     */
    LOCAL_ONLY_UNLIKE,
}

/** The like decision, as a pure function so the state machine is testable. */
fun likeAction(signedIn: Boolean, sourceId: SourceId, liked: Boolean): LikeAction = when {
    !liked -> LikeAction.LOCAL_ONLY_UNLIKE
    signedIn && sourceId == SourceId.YOUTUBE_MUSIC -> LikeAction.LOCAL_AND_PUSH
    else -> LikeAction.LOCAL_ONLY
}
