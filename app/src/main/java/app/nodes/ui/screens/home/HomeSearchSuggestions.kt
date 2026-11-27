/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.home

import app.nodes.core.model.Track
import java.text.Normalizer
import java.util.Locale

/** How many tracks the "From your searches" rail shows. */
internal const val SEARCH_SUGGESTION_LIMIT = 10

/**
 * Picks tracks the user has already discovered through Search, ranked against
 * their recent local search history. This is intentionally a pure local derive:
 * it does not query a provider, refresh a catalogue, or make a network request.
 *
 * The newest search gets the largest weight, while matches from older searches
 * still contribute. Any track already shown in Home or Continue Playing is
 * excluded, so the rail adds discovery rather than duplicating another row.
 */
internal fun searchHistorySuggestions(
    recentQueries: List<String>,
    cachedTracks: List<Track>,
    excludedIds: Set<String> = emptySet(),
    limit: Int = 10,
): List<Track> {
    if (recentQueries.isEmpty() || cachedTracks.isEmpty() || limit <= 0) return emptyList()

    val queries = recentQueries.map(::tokens).filter { it.isNotEmpty() }
    if (queries.isEmpty()) return emptyList()

    return cachedTracks
        .asSequence()
        .filterNot { it.id in excludedIds }
        .distinctBy { it.id }
        .mapNotNull { track ->
            val metadata = tokens("${track.title} ${track.artist} ${track.album.orEmpty()}")
            var score = 0
            queries.forEachIndexed { index, queryTokens ->
                if (queryTokens.all { it in metadata }) {
                    // SearchHistoryRepository stores most recent first. The
                    // first query contributes 8x and the eighth contributes 1x.
                    score += (queries.size - index) * 100 + queryTokens.size
                }
            }
            if (score == 0) null else track to score
        }
        // Kotlin's sortedBy is stable, so cachedTracks order is the tie-break.
        .sortedByDescending { it.second }
        .take(limit)
        .map { it.first }
        .toList()
}

/**
 * Blends the local matches with what the provider suggests around them.
 *
 * The local pass only knows music the app has already seen, so on its own the
 * rail repeats itself. [discovery] is the provider's answer for the best match
 * (or, when nothing local matches, for the newest query itself): the songs the
 * user has not heard yet but searched their way towards. Matches lead, because
 * they answer the search, then discovery fills the rest one artist at a time so
 * a single radio answer cannot turn the rail into eight songs by one artist.
 */
internal fun mergeSearchSuggestions(
    matches: List<Track>,
    discovery: List<Track>,
    excludedIds: Set<String> = emptySet(),
    limit: Int = SEARCH_SUGGESTION_LIMIT,
): List<Track> {
    if (limit <= 0) return emptyList()
    val picked = LinkedHashMap<String, Track>()
    for (track in matches) {
        if (picked.size >= limit) break
        if (track.id in excludedIds) continue
        picked.putIfAbsent(track.id, track)
    }
    if (picked.size >= limit) return picked.values.toList()

    val byArtist = LinkedHashMap<String, ArrayDeque<Track>>()
    for (track in discovery) {
        if (track.id in excludedIds || picked.containsKey(track.id)) continue
        byArtist.getOrPut(artistKey(track.artist)) { ArrayDeque() }.addLast(track)
    }
    var roundAdded = true
    while (roundAdded && picked.size < limit) {
        roundAdded = false
        for (queue in byArtist.values) {
            if (picked.size >= limit) break
            val track = queue.removeFirstOrNull() ?: continue
            if (picked.putIfAbsent(track.id, track) == null) roundAdded = true
        }
    }
    return picked.values.toList()
}

private fun artistKey(artist: String): String =
    Normalizer.normalize(artist, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .trim()

private fun tokens(value: String): Set<String> =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length > 1 }
        .toSet()
