/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.reel

import app.nodes.core.model.SourceId
import app.nodes.core.model.Track
import java.text.Normalizer
import java.util.Locale

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
     * [exclude] (the queue plus the last played tracks) are skipped, and the
     * same recording (normalized title + artist) appears at most once.
     */
    fun interleaveByArtist(
        byArtist: List<List<Track>>,
        exclude: Set<String>,
        limit: Int,
        excludeMusic: Set<String> = emptySet(),
    ): List<Track> {
        val out = ArrayList<Track>(limit)
        val seenIds = HashSet(exclude)
        val seenMusic = HashSet(excludeMusic)
        // The current by-ID exclusions include the recent playback history.
        // Also exclude the corresponding recordings across source-prefixed ids.
        byArtist.flatten()
            .filter { it.id in exclude }
            .mapTo(seenMusic) { identity(it) }
        val cursors = IntArray(byArtist.size)
        var exhausted = false
        while (out.size < limit && !exhausted) {
            exhausted = true
            for (index in byArtist.indices) {
                val source = byArtist[index]
                while (cursors[index] < source.size) {
                    val track = source[cursors[index]]
                    cursors[index]++
                    if (track.id in seenIds || !seenMusic.add(identity(track))) continue
                    seenIds += track.id
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
    fun batch(
        candidates: List<Track>,
        exclude: Set<String>,
        limit: Int,
        excludeMusic: Set<String> = emptySet(),
    ): List<Track> {
        val seenIds = HashSet(exclude)
        val seenMusic = HashSet(excludeMusic)
        candidates.filter { it.id in exclude }.mapTo(seenMusic) { identity(it) }
        val out = ArrayList<Track>(limit)
        for (track in candidates) {
            if (out.size >= limit) break
            val identity = identity(track)
            if (track.id in seenIds || !seenMusic.add(identity)) continue
            seenIds += track.id
            out += track
        }
        return out
    }

    /**
     * Behavior ranking over already-known tracks. No provider is queried here:
     * the result is derived from local search history, playback history, likes,
     * and the caller's candidate pool. Newer searches and plays weigh more.
     */
    fun personalize(
        candidates: List<Track>,
        recentQueries: List<String>,
        recentlyPlayed: List<Track>,
        liked: List<Track>,
    ): List<Track> {
        val unique = batch(candidates, emptySet(), candidates.size)
        if (unique.isEmpty()) return unique
        val queries = recentQueries.map(::tokens).filter { it.isNotEmpty() }
        val playRanks = recentlyPlayed.withIndex().associate { identity(it.value) to it.index }
        val likedMusic = liked.mapTo(HashSet(), ::identity)
        return unique.withIndex()
            .map { (index, track) ->
                val metadata = tokens("${track.title} ${track.artist} ${track.album.orEmpty()}")
                var score = 0
                queries.forEachIndexed { queryIndex, query ->
                    if (query.all { it in metadata }) {
                        score += (queries.size - queryIndex) * SEARCH_WEIGHT + query.size
                    }
                }
                val key = identity(track)
                playRanks[key]?.let { rank ->
                    score += (recentlyPlayed.size - rank) * PLAY_WEIGHT
                }
                // New tracks from listened-to artists are useful; do not give
                // the bonus to the exact recordings in playRanks, which are
                // filtered by the caller when constructing the candidate batch.
                if (recentlyPlayed.any {
                        normalize(it.artist) == normalize(track.artist) && identity(it) != key
                    }
                ) {
                    score += ARTIST_AFFINITY_WEIGHT
                }
                if (key in likedMusic) score += LIKE_WEIGHT
                track to (score to index)
            }
            .sortedWith(
                compareByDescending<Pair<Track, Pair<Int, Int>>> { it.second.first }
                    .thenBy { it.second.second },
            )
            .map { it.first }
    }

    /** Same recording key across source-prefixed ids: normalized title + artist. */
    fun identity(track: Track): String {
        val title = normalize(track.title)
        val artist = normalize(track.artist)
        return if (title.isBlank() && artist.isBlank()) track.id else "$title|$artist"
    }

    private fun tokens(value: String): Set<String> = normalize(value)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length > 1 }
        .toSet()

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")

    private const val SEARCH_WEIGHT = 1_000
    private const val PLAY_WEIGHT = 100
    private const val ARTIST_AFFINITY_WEIGHT = 25
    private const val LIKE_WEIGHT = 50
}

/** What a like tap should do beyond the local database. */
enum class LikeAction {
    /** Guest, or a locally sourced track: the Room write is the whole story. */
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
