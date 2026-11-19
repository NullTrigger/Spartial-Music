/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.youtubemusic

import app.nodes.core.data.SettingsRepository
import app.nodes.core.model.HeroSlide
import app.nodes.core.model.HomeFeed
import app.nodes.core.model.HomeSection
import app.nodes.core.model.PlaybackHandle
import app.nodes.core.model.Playlist
import app.nodes.core.model.PlaylistDetail
import app.nodes.core.model.SourceAvailability
import app.nodes.core.model.SourceErrorType
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceResult
import app.nodes.core.model.SourceStatus
import app.nodes.core.model.Track
import app.nodes.core.source.MusicSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Music through the unofficial InnerTube API.
 *
 * On by default in this fork by maintainer decision (recorded in YOUTUBE.md),
 * because the official Data API cannot show a real music catalogue. It is
 * outside the YouTube API Services Terms and can stop working at any time; the
 * official Data API source stays registered, but the UI has no source picker
 * any more, so this is the only provider Home, Search and playback can reach.
 *
 * Playback is [PlaybackHandle.Stream] straight into Media3 — no downloading, no
 * ad skipping, and no transcoding.
 */
@Singleton
class YouTubeMusicSource @Inject constructor(
    private val innerTube: InnerTubeClient,
    private val settings: SettingsRepository,
) : MusicSource {

    override val id: SourceId = SourceId.YOUTUBE_MUSIC
    override val displayName: String = "YouTube Music (unofficial)"

    override suspend fun availability(): SourceAvailability =
        SourceAvailability(id, SourceStatus.READY)

    override suspend fun search(query: String, limit: Int): SourceResult<List<Track>> {
        if (query.isBlank()) return SourceResult.Success(emptyList())
        return runCatching { innerTube.search(query, limit) }
            .fold(
                onSuccess = { SourceResult.Success(it) },
                onFailure = { it.asFailure() },
            )
    }

    /** Suggestions for the search field, best effort. */
    suspend fun suggestions(input: String): List<String> {
        return innerTube.suggestions(input)
    }

    /**
     * Home is `browse(FEmusic_home)`: whatever shelves YouTube Music returns for
     * this session, in its order. Signed out that is album and playlist cards;
     * signed in, the personalised song shelves come too.
     *
     * Anonymous home can be thin, so when it holds no playable song at all the
     * Explore page's song shelves are added. That keeps Home useful without
     * inventing data, and never replaces a real response.
     */
    override suspend fun getHome(): SourceResult<HomeFeed> {
        val home = runCatching { innerTube.home() }
            .getOrElse { return it.asFailure() }

        var sections = home.sections
        if (sections.none { section -> section.tracks.any { it.videoId != null } }) {
            val explore = runCatching { innerTube.explore() }.getOrNull()
            if (explore != null) {
                val songShelves = explore.sections.filter { section ->
                    section.tracks.any { it.videoId != null }
                }
                val known = sections.map { it.title }.toSet()
                sections = sections + songShelves.filter { it.title !in known }
            }
        }

        if (sections.isEmpty()) {
            return SourceResult.Failure(
                SourceErrorType.NETWORK,
                "YouTube Music returned no shelves.",
            )
        }

        val playable = sections
            .flatMap { it.tracks }
            .filter { it.videoId != null }
            .distinctBy { it.id }

        return SourceResult.Success(
            HomeFeed(
                hero = playable.take(5).map { HeroSlide(track = it) },
                continuePlaying = emptyList(),
                sections = sections,
            ),
        )
    }

    override suspend fun getTrending(limit: Int): SourceResult<List<Track>> {
        return runCatching {
            val songs = innerTube.explore().sections
                .flatMap { it.tracks }
                .filter { it.videoId != null }
                .distinctBy { it.id }
            if (songs.isNotEmpty()) {
                songs.take(limit.coerceIn(1, 50))
            } else {
                innerTube.search("trending music", limit)
            }
        }.fold(
            onSuccess = { SourceResult.Success(it) },
            onFailure = { it.asFailure() },
        )
    }

    /**
     * A playlist, an album, or the liked-songs list. The id may be a bare
     * playlist id ("PL…"), a browse id as returned by Home ("VLPL…",
     * "MPREb…") or "LM" for the signed-in user's liked songs.
     */
    override suspend fun getPlaylist(playlistId: String): SourceResult<PlaylistDetail> {
        return runCatching { innerTube.collection(playlistId, 200) }
            .fold(
                onSuccess = { page ->
                    if (page.tracks.isEmpty()) {
                        SourceResult.Failure(
                            SourceErrorType.NOT_FOUND,
                            "That album or playlist is empty or private.",
                        )
                    } else {
                        // The page header names the real album or playlist;
                        // the synthetic name only survives when the layout
                        // does not carry one.
                        SourceResult.Success(
                            PlaylistDetail(
                                playlist = Playlist(
                                    id = playlistId,
                                    title = page.title ?: playlistTitle(playlistId),
                                    trackCount = page.tracks.size,
                                    sourceId = SourceId.YOUTUBE_MUSIC,
                                    artworkUri = page.artworkUri,
                                    externalUrl = "https://music.youtube.com/browse/$playlistId",
                                ),
                                tracks = page.tracks,
                            ),
                        )
                    }
                },
                onFailure = { it.asFailure() },
            )
    }

    /** The signed-in user's own playlists; empty when signed out. */
    suspend fun userPlaylists(): List<Playlist> = runCatching { innerTube.libraryPlaylists() }
        .getOrDefault(emptyList())

    /** The signed-in user's liked songs; empty when signed out. */
    suspend fun likedSongs(): List<Track> =
        runCatching { innerTube.collection(LIKED_SONGS_ID, 200) }
            .getOrNull()?.tracks.orEmpty()

    /** How a likes push went: what was accepted, what YouTube refused. */
    data class LikeSync(val pushed: Int, val failed: Int)

    /**
     * Replays [videoIds] on the account's like record, one call each so one
     * refused track does not lose the rest. Pushing a like that already exists
     * is a no-op server-side, which is what makes this replayable.
     */
    suspend fun pushLikes(videoIds: List<String>): LikeSync {
        var pushed = 0
        var failed = 0
        for (videoId in videoIds.distinct()) {
            if (runCatching { innerTube.likeVideo(videoId) }.getOrDefault(false)) {
                pushed++
            } else {
                failed++
            }
        }
        return LikeSync(pushed = pushed, failed = failed)
    }

    /** What a playlist push managed: the new playlist, or what went wrong. */
    sealed interface PlaylistPush {
        data class Done(val playlistId: String, val added: Int, val missed: Int) : PlaylistPush
        data object SignedOut : PlaylistPush
        data object Failed : PlaylistPush
    }

    /**
     * Copies a local playlist to the account: every local track is matched to
     * a YouTube video by its title and artist, and a private playlist holding
     * those videos is created. Tracks that cannot be matched are counted, never
     * guessed - a wrong song in someone's playlist is worse than a missing one.
     */
    suspend fun pushPlaylist(name: String, tracks: List<Track>): PlaylistPush {
        if (!innerTube.isSignedIn) return PlaylistPush.SignedOut
        val videoIds = mutableListOf<String>()
        var missed = 0
        for (track in tracks) {
            val query = listOf(track.title, track.artist).filter { it.isNotBlank() }
                .joinToString(" ")
            val match = runCatching { innerTube.search(query, 3) }.getOrDefault(emptyList())
                .firstOrNull { it.videoId != null }
                ?.videoId
            if (match != null) videoIds += match else missed++
        }
        if (videoIds.isEmpty()) return PlaylistPush.Failed
        val id = runCatching { innerTube.createPlaylist(name, null, videoIds) }.getOrNull()
            ?: return PlaylistPush.Failed
        return PlaylistPush.Done(playlistId = id, added = videoIds.size, missed = missed)
    }

    override suspend fun play(track: Track): SourceResult<PlaybackHandle> {
        val videoId = track.videoId ?: return SourceResult.Success(
            PlaybackHandle.Unavailable("This track has no YouTube video id."),
        )
        val preferHighQuality = settings.current().highQualityAudio
        val outcome = runCatching { innerTube.audioUrl(videoId, preferHighQuality) }
            .getOrElse {
                return SourceResult.Success(
                    PlaybackHandle.Unavailable(
                        "Could not reach YouTube Music. Check your connection and try again.",
                    ),
                )
            }

        return when (outcome) {
            is InnerTubeStream.Playable -> SourceResult.Success(PlaybackHandle.Stream(outcome.url))
            is InnerTubeStream.Refused -> SourceResult.Success(
                PlaybackHandle.Unavailable(
                    "YouTube would not serve this track (${outcome.detail}). " +
                        "It may be region-locked, age-gated, a live stream, or only offered " +
                        "to clients this app does not imitate. See YOUTUBE.md.",
                ),
            )
        }
    }

    override fun invalidateStream(track: Track) {
        track.videoId?.let(innerTube::invalidateStream)
    }

    /**
     * Radio / up-next tracks for the infinite queue: the same `next` request
     * the music site makes when a song runs out. Metadata only; playback still
     * goes through [play] like every other track.
     */
    override suspend fun getRelated(track: Track, limit: Int): SourceResult<List<Track>> {
        val videoId = track.videoId ?: return SourceResult.Success(emptyList())
        return runCatching { innerTube.next(videoId, limit) }
            .fold(
                onSuccess = { SourceResult.Success(it.filter { candidate -> candidate.videoId != videoId }) },
                onFailure = { it.asFailure() },
            )
    }

    override suspend fun prefetch(track: Track) {
        val videoId = track.videoId ?: return
        val preferHighQuality = settings.current().highQualityAudio
        // Warm the URL cache; the answer itself is not needed until play().
        runCatching { innerTube.audioUrl(videoId, preferHighQuality) }
    }

    /**
     * The name shown when a page's own header did not carry one. Never the
     * provider's own name: the page is an album or playlist the app could not
     * name, not a thing called "YouTube Music".
     */
    private fun playlistTitle(playlistId: String): String = when {
        playlistId == LIKED_SONGS_ID -> "Liked songs"
        playlistId.startsWith("MPRE") -> "Untitled album"
        else -> "Untitled playlist"
    }

    private fun Throwable.asFailure(): SourceResult.Failure =
        if (this is IOException) {
            SourceResult.Failure(SourceErrorType.NETWORK, message)
        } else {
            SourceResult.Failure(SourceErrorType.PARSE, message)
        }

    private companion object {
        /** YouTube Music's own id for the signed-in user's liked songs. */
        const val LIKED_SONGS_ID = "LM"
    }
}
