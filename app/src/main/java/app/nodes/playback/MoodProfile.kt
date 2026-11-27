/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import app.nodes.core.model.Track
import java.util.Locale

/**
 * What the queue sounds like right now: the artists and albums behind it and
 * the language its text is written in.
 *
 * The inference is entirely local and explainable — no provider call, no
 * profile, no telemetry. A language is recognised two ways:
 *
 *  - a word that names it in the track's own text ("Bhojpuri Song 2025",
 *    "Malayalam Hits", "Kollywood"), which wins over everything else;
 *  - failing that, the script the text is written in: Devanagari counts as
 *    Hindi, Gurmukhi as Punjabi, Malayalam as Malayalam, and so on. Latin
 *    script names no language — English and romanised Tamil are
 *    indistinguishable one song at a time — so Latin is [UNSTATED].
 *
 * A candidate that shares the mood's language, or its artist, is [Match.SAME].
 * A candidate with no language signal at all is [Match.NEUTRAL]: it may well
 * be the same language written in Latin script, so it must not be punished.
 * Only a track that positively names a *different* language is [Match.OTHER] —
 * that is the case that must stay a small minority.
 */
internal data class MoodProfile(
    val artists: Set<String> = emptySet(),
    val albums: Set<String> = emptySet(),
    val clusters: Set<String> = emptySet(),
) {
    /** How a candidate relates to the queue's mood. */
    enum class Match { SAME, NEUTRAL, OTHER }

    fun matchOf(track: Track): Match {
        val artist = track.artist.keyOrNull()
        if (artist != null && artist in artists) return Match.SAME
        val album = track.album?.keyOrNull()
        if (album != null && album in albums) return Match.SAME
        val cluster = clusterOf(track)
        return when {
            cluster == UNSTATED -> Match.NEUTRAL
            cluster in clusters -> Match.SAME
            else -> Match.OTHER
        }
    }

    companion object {
        /** Latin-script text names no language: English and romanised Tamil alike. */
        const val UNSTATED = "unstated"

        /**
         * The mood around [seed] — what is playing, the strongest signal the
         * user gives — plus whatever dominates the queue's [recent] tail: what
         * they queued last is the best statement of what they want next.
         *
         * A language must hold at least half the room to count from the tail,
         * so one stray track that slipped into the queue cannot join the mood
         * and then grow its own share batch after batch. The playing track is
         * exempt: tapping one song is how a mood starts.
         */
        fun of(seed: Track?, recent: List<Track>): MoodProfile {
            val tracks = listOfNotNull(seed) + recent
            val counts = LinkedHashMap<String, Int>()
            for (track in tracks) {
                val cluster = clusterOf(track)
                if (cluster != UNSTATED) counts[cluster] = (counts[cluster] ?: 0) + 1
            }
            val clusters = counts.filterValues { it * 2 >= tracks.size }.keys.toMutableSet()
            seed?.let { clusterOf(it) }
                ?.takeIf { it != UNSTATED }
                ?.let(clusters::add)
            return MoodProfile(
                artists = tracks.mapNotNullTo(HashSet()) { it.artist.keyOrNull() },
                albums = tracks.mapNotNullTo(HashSet()) { it.album?.keyOrNull() },
                clusters = clusters,
            )
        }

        /**
         * The language or script a track's own text speaks. Named words beat
         * scripts, so "Bhojpuri Song" stays Bhojpuri even where the script
         * detector would say Hindi.
         */
        fun clusterOf(track: Track): String {
            val text = "${track.title} ${track.artist} ${track.album.orEmpty()}"
            val words = text.lowercase(Locale.ROOT)
            for ((marker, cluster) in LANGUAGE_MARKERS) {
                if (marker.containsMatchIn(words)) return cluster
            }
            return scriptClusterOf(text)
        }
    }
}

/**
 * A language named in the track's own text. Word-boundary matched so "odia"
 * cannot claim "melodia".
 */
private val LANGUAGE_MARKERS: List<Pair<Regex, String>> = listOf(
    "bhojpuri" to "bhojpuri",
    "malayalam" to "malayalam",
    "mollywood" to "malayalam",
    "kannada" to "kannada",
    "tamil" to "tamil",
    "kollywood" to "tamil",
    "telugu" to "telugu",
    "tollywood" to "telugu",
    "bengali" to "bengali",
    "bangla" to "bengali",
    "marathi" to "marathi",
    "gujarati" to "gujarati",
    "punjabi" to "punjabi",
    "hindi" to "hindi",
    "bollywood" to "hindi",
    "hindustani" to "hindi",
    "urdu" to "urdu",
    "odia" to "odia",
    "oriya" to "odia",
    "assamese" to "assamese",
    "nepali" to "nepali",
    "english" to "english",
).map { (word, cluster) ->
    Regex("(?<![\\p{L}])$word(?![\\p{L}])") to cluster
}

/**
 * The dominant script of [text], or [MoodProfile.UNSTATED] when it is Latin
 * (no language named) or carries no recognisable script at all.
 */
private fun scriptClusterOf(text: String): String {
    val counts = HashMap<String, Int>()
    for (ch in text) {
        val cluster = when (ch.code) {
            in 0x0900..0x097F -> "hindi" // Devanagari: Hindi, Bhojpuri, Marathi share it
            in 0x0980..0x09FF -> "bengali"
            in 0x0A00..0x0A7F -> "punjabi" // Gurmukhi
            in 0x0A80..0x0AFF -> "gujarati"
            in 0x0B00..0x0B7F -> "odia"
            in 0x0B80..0x0BFF -> "tamil"
            in 0x0C00..0x0C7F -> "telugu"
            in 0x0C80..0x0CFF -> "kannada"
            in 0x0D00..0x0D7F -> "malayalam"
            else -> null
        } ?: continue
        counts[cluster] = (counts[cluster] ?: 0) + 1
    }
    return counts.maxByOrNull { it.value }?.key ?: MoodProfile.UNSTATED
}

private fun String.keyOrNull(): String? =
    trim().lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }
