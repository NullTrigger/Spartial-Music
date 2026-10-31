/*
 * Copyright (C) 2026 The Nodes Authors.
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
 * Off by default: the user has to enable it in Settings, because it is not
 * covered by the YouTube API Services Terms and can stop working at any time.
 * The official Data API source ([app.nodes.core.source.youtube.YouTubeOfficialSource])
 * stays available and is the one to keep as the default.
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

    private suspend fun enabled(): Boolean = settings.current().innerTubeEnabled

    override suspend fun availability(): SourceAvailability = when {
        enabled() -> SourceAvailability(id, SourceStatus.READY)
        else -> SourceAvailability(
            id,
            SourceStatus.NEEDS_PERMISSION,
            "Off by default. Turn it on in Settings → YouTube Music. It is unofficial and can break.",
        )
    }

    override suspend fun search(query: String, limit: Int): SourceResult<List<Track>> {
        if (!enabled()) return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        if (query.isBlank()) return SourceResult.Success(emptyList())
        return innerTube.search(query, limit).asResult()
    }

    override suspend fun getHome(): SourceResult<HomeFeed> {
        if (!enabled()) return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)

        val rails: List<Pair<String, List<Track>>> = HOME_RAILS.mapNotNull { (title, query) ->
            val tracks: List<Track> = runCatching { innerTube.search(query, 20) }.getOrNull().orEmpty()
            if (tracks.isEmpty()) null else title to tracks
        }
        if (rails.isEmpty()) {
            return SourceResult.Failure(SourceErrorType.NETWORK, "YouTube Music did not answer.")
        }

        val sections = rails.map { (title, tracks) ->
            HomeSection(id = "ytmusic-${title.lowercase().replace(' ', '-')}", title = title, tracks = tracks)
        }
        val hero = rails.first().second

        return SourceResult.Success(
            HomeFeed(
                hero = hero.take(5).map { HeroSlide(track = it) },
                continuePlaying = hero.drop(5).take(6),
                sections = sections,
            ),
        )
    }

    override suspend fun getTrending(limit: Int): SourceResult<List<Track>> {
        if (!enabled()) return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        return innerTube.search("trending music", limit).asResult()
    }

    override suspend fun getPlaylist(playlistId: String): SourceResult<PlaylistDetail> {
        if (!enabled()) return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        return runCatching { innerTube.playlist(playlistId, 100) }
            .fold(
                onSuccess = { tracks ->
                    if (tracks.isEmpty()) {
                        SourceResult.Failure(SourceErrorType.NOT_FOUND, "That playlist is empty or private.")
                    } else {
                        SourceResult.Success(
                            PlaylistDetail(
                                playlist = Playlist(
                                    id = playlistId,
                                    title = "YouTube Music playlist",
                                    trackCount = tracks.size,
                                    sourceId = SourceId.YOUTUBE_MUSIC,
                                    externalUrl = "https://music.youtube.com/playlist?list=$playlistId",
                                ),
                                tracks = tracks,
                            ),
                        )
                    }
                },
                onFailure = { it.asFailure() },
            )
    }

    override suspend fun play(track: Track): SourceResult<PlaybackHandle> {
        if (!enabled()) return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        val videoId = track.videoId ?: return SourceResult.Success(
            PlaybackHandle.Unavailable("This track has no YouTube video id."),
        )
        val preferHighQuality = settings.current().highQualityAudio
        val url = runCatching { innerTube.audioUrl(videoId, preferHighQuality) }.getOrNull()
        return if (url.isNullOrBlank()) {
            SourceResult.Success(
                PlaybackHandle.Unavailable(
                    "YouTube did not return a playable stream for this track. " +
                        "It may be region-locked, age-gated or a live stream.",
                ),
            )
        } else {
            SourceResult.Success(PlaybackHandle.Stream(url))
        }
    }

    private fun List<Track>.asResult(): SourceResult<List<Track>> =
        SourceResult.Success(this)

    private fun Throwable.asFailure(): SourceResult.Failure =
        if (this is IOException) {
            SourceResult.Failure(SourceErrorType.NETWORK, message)
        } else {
            SourceResult.Failure(SourceErrorType.UNKNOWN, message)
        }

    private companion object {
        /** Queries that make a plausible music home without a recommendations feed. */
        val HOME_RAILS = listOf(
            "Trending music" to "trending music videos this week",
            "New releases" to "new music releases",
            "Chill mix" to "chill lo-fi mix",
        )
    }
}
