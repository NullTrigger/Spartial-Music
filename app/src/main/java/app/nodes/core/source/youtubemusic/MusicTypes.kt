/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.youtubemusic

/**
 * The words that ask for a *kind* of music rather than a track.
 *
 * A search box serves two different questions. "One Love" is a name: the answer
 * is the songs called that, and a title search finds them. "Disco" is a type:
 * a title search answers it with the handful of songs that happen to be called
 * "Disco", which is not what the person typing meant.
 *
 * The list is deliberately closed rather than clever. A guess that a word is a
 * genre when it is really a title ("Love", "Home", "Alone") would replace exact
 * results with editorial playlists, so only words that are almost always a kind
 * of music are here, and multi-word genre names are spelled out. Anything not
 * in the list keeps the exact title search it always had.
 */
internal object MusicTypes {

    private val words: Set<String> = setOf(
        // Moods
        "sad", "sad songs", "happy", "happy songs", "chill", "chillout", "chill out",
        "relax", "relaxing", "romantic", "romance", "party", "workout", "gym",
        "sleep", "sleep music", "focus", "study", "motivation", "feel good",
        "heartbreak", "breakup", "emotional", "devotional",
        // Genres
        "disco", "lofi", "lo-fi", "hip hop", "hip-hop", "rap", "rock", "metal",
        "punk", "pop", "indie", "jazz", "blues", "classical", "country", "folk",
        "reggae", "reggaeton", "afrobeat", "k-pop", "kpop", "j-pop", "jpop",
        "soul", "funk", "r&b", "rnb", "gospel", "edm", "house", "techno",
        "trance", "dubstep", "drum and bass", "dnb", "ambient", "instrumental",
        "piano", "guitar", "acoustic", "orchestral", "soundtrack", "phonk",
        "drill", "trap", "amapiano", "bhangra", "qawwali", "sufi", "ghazal",
        "bhajan", "garba", "filmi", "item songs",
        // Languages and scenes, which is how the catalogue is browsed here
        "hindi", "punjabi", "tamil", "telugu", "malayalam", "kannada", "marathi",
        "bengali", "gujarati", "urdu", "bhojpuri", "spanish", "korean", "english",
        // Decades
        "60s", "70s", "80s", "90s", "2000s", "2010s", "retro", "old songs",
        "old school", "classic hits",
    )

    /**
     * True when [query] is one of the type words above.
     *
     * Matching is on the whole trimmed query in lower case: "disco" is a type,
     * "disco inferno" is a title, and the extra word is the tell.
     */
    fun isType(query: String): Boolean = query.trim().lowercase() in words
}
