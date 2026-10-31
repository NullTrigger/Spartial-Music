/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.model

import kotlinx.serialization.Serializable

/**
 * Identifies the provider a piece of content came from. Every provider
 * implements [app.nodes.core.source.MusicSource] and is registered in
 * `SourceRegistry`; nothing else in the app is allowed to talk to a provider
 * directly.
 */
enum class SourceId(val key: String) {
    LOCAL("local"),
    JAMENDO("jamendo"),
    YOUTUBE("youtube"),

    /** Unofficial InnerTube source; off unless the user enables it in Settings. */
    YOUTUBE_MUSIC("youtube_music"),
    ;

    companion object {
        fun fromKey(key: String): SourceId =
            entries.firstOrNull { it.key == key } ?: LOCAL
    }
}

/**
 * A playable (or at least browsable) track. `mediaUri` is a direct audio URL
 * only for sources that legally expose one (local files, Creative Commons
 * audio). YouTube tracks never carry a stream URL: they are played through the
 * official embedded player, see [PlaybackHandle].
 */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUri: String? = null,
    val mediaUri: String? = null,
    val durationMs: Long = 0L,
    val sourceId: SourceId = SourceId.LOCAL,
    /** YouTube video id, used only by the embedded IFrame player. */
    val videoId: String? = null,
    /** Human readable page for attribution (YouTube watch page, Jamendo page). */
    val externalUrl: String? = null,
    /** Licence line for CC content, e.g. "CC BY 4.0 · Jamendo". */
    val licenseNote: String? = null,
    val isExplicit: Boolean = false,
) {
    val isYoutube: Boolean get() = sourceId == SourceId.YOUTUBE
}

/** A slide in the hero carousel. */
data class HeroSlide(
    val track: Track,
    val kicker: String = "NOW PLAYING",
)

/**
 * One time-synced lyric line. Lyrics come only from a local `.lrc` sidecar
 * file the user supplies - Nodes never downloads lyrics from a third party.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

/** A horizontal rail on Home. */
data class HomeSection(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val tracks: List<Track> = emptyList(),
    val playlistId: String? = null,
)

data class HomeFeed(
    val hero: List<HeroSlide> = emptyList(),
    val continuePlaying: List<Track> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
)

@Serializable
data class Playlist(
    val id: String,
    val title: String,
    val description: String? = null,
    val artworkUri: String? = null,
    val trackCount: Int = 0,
    val sourceId: SourceId = SourceId.LOCAL,
    val ownerName: String? = null,
    val externalUrl: String? = null,
) {
    val isLocal: Boolean get() = sourceId == SourceId.LOCAL
}

data class PlaylistDetail(
    val playlist: Playlist,
    val tracks: List<Track>,
)

/** What the playback layer should do with a track. */
sealed interface PlaybackHandle {
    /** Stream or play a URL through Media3/ExoPlayer. */
    data class Stream(val url: String) : PlaybackHandle

    /**
     * Play through the official YouTube IFrame player. No stream URL is
     * extracted, nothing is downloaded, and audio stops when the player leaves
     * the screen - that is a requirement of the YouTube API Services Terms.
     */
    data class EmbeddedVideo(val videoId: String) : PlaybackHandle

    data class Unavailable(val reason: String) : PlaybackHandle
}

enum class SourceStatus {
    READY,
    NEEDS_API_KEY,
    NEEDS_CLIENT_ID,
    NEEDS_PERMISSION,
    DISABLED,
    ERROR,
}

data class SourceAvailability(
    val sourceId: SourceId,
    val status: SourceStatus,
    val message: String? = null,
) {
    val isReady: Boolean get() = status == SourceStatus.READY
}

enum class SourceErrorType {
    NO_CONFIGURATION,
    QUOTA_EXCEEDED,
    NETWORK,
    UNAUTHORIZED,
    NOT_FOUND,
    PARSE,
    UNKNOWN,
}

sealed interface SourceResult<out T> {
    data class Success<T>(val data: T, val fromCache: Boolean = false) : SourceResult<T>
    data class Failure(val type: SourceErrorType, val message: String? = null) : SourceResult<Nothing>

    // Not `inline`: interface members are open, so the compiler cannot inline
    // them ("'inline' modifier on virtual members is prohibited").
    fun <R> map(transform: (T) -> R): SourceResult<R> = when (this) {
        is Success -> Success(transform(data), fromCache)
        is Failure -> this
    }

    fun getOrNull(): T? = (this as? Success)?.data
}
