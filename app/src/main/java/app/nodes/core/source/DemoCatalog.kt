/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source

import app.nodes.core.model.HeroSlide
import app.nodes.core.model.HomeFeed
import app.nodes.core.model.HomeSection
import app.nodes.core.model.SourceId
import app.nodes.core.model.Track

/**
 * Placeholder content shown when no provider is configured yet.
 *
 * Everything here is invented for this repository: no real artist, album or
 * cover art. The tracks have no audio URL on purpose - Spartial Music will not fake
 * playback - so the Play button explains how to enable a real source.
 */
object DemoCatalog {

    private const val NOTE = "Sample metadata · add a source to play audio"

    val tracks: List<Track> = listOf(
        "Midnight Static" to "Sample Ensemble",
        "Paper Lanterns" to "Placeholder Trio",
        "Slow Orbit" to "Spartial Studio",
        "Velvet Rooms" to "Sample Ensemble",
        "Low Tide" to "Placeholder Trio",
        "Neon Rain" to "Spartial Studio",
        "Afterglow" to "Sample Ensemble",
        "Glass Corridor" to "Placeholder Trio",
        "Northern Line" to "Spartial Studio",
        "Salt and Signal" to "Sample Ensemble",
        "Quiet Machines" to "Placeholder Trio",
        "Sunday Drive" to "Spartial Studio",
    ).mapIndexed { index, (title, artist) ->
        Track(
            id = "demo-$index",
            title = title,
            artist = artist,
            album = "Placeholder Sessions Vol. 1",
            artworkUri = null,
            mediaUri = null,
            durationMs = (168_000L + index * 9_000L),
            sourceId = SourceId.LOCAL,
            licenseNote = NOTE,
        )
    }

    val hero: List<HeroSlide> = tracks.take(5).map { HeroSlide(track = it) }

    val feed: HomeFeed = HomeFeed(
        hero = hero,
        continuePlaying = tracks.take(6),
        sections = listOf(
            HomeSection(
                id = "demo-new",
                title = "New songs you may like",
                subtitle = "Placeholder catalogue",
                tracks = tracks.shuffled(kotlin.random.Random(7)).take(8),
            ),
            HomeSection(
                id = "demo-quiet",
                title = "Quiet machines",
                subtitle = "Electronic sketches",
                tracks = tracks.takeLast(6),
            ),
        ),
    )

    fun trending(): List<Track> = tracks.shuffled(kotlin.random.Random(3))

    fun search(query: String): List<Track> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return tracks.filter {
            it.title.contains(needle, ignoreCase = true) ||
                it.artist.contains(needle, ignoreCase = true) ||
                (it.album?.contains(needle, ignoreCase = true) == true)
        }
    }
}
