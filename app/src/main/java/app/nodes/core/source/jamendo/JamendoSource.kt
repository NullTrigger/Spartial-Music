/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.jamendo

import app.nodes.BuildConfig
import app.nodes.core.data.RemoteCache
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
import app.nodes.core.source.DemoCatalog
import app.nodes.core.source.MusicSource
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Creative Commons streaming through Jamendo's public API (free key, no
 * scraping). Artwork and audio URLs come from Jamendo; every track carries its
 * licence link so the UI can credit the artist.
 */
@Singleton
class JamendoSource @Inject constructor(
    @Named("jamendo") private val api: JamendoApi,
    private val cache: RemoteCache,
    private val json: Json,
) : MusicSource {

    override val id: SourceId = SourceId.JAMENDO
    override val displayName: String = "Jamendo (Creative Commons)"

    private val clientId: String get() = BuildConfig.JAMENDO_CLIENT_ID

    override suspend fun availability(): SourceAvailability = if (clientId.isBlank()) {
        SourceAvailability(
            id,
            SourceStatus.NEEDS_CLIENT_ID,
            "Add JAMENDO_CLIENT_ID to local.properties to stream Creative Commons music.",
        )
    } else {
        SourceAvailability(id, SourceStatus.READY)
    }

    override suspend fun search(query: String, limit: Int): SourceResult<List<Track>> {
        if (clientId.isBlank()) return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        if (query.isBlank()) return SourceResult.Success(emptyList())
        val key = "jamendo:search:${query.lowercase()}:$limit"
        val cached = cache.get(key)
        if (cached != null) {
            val payload = runCatching {
                json.decodeFromString(SearchPayload.serializer(), cached)
            }.getOrNull()
            if (payload != null) {
                return SourceResult.Success(payload.tracks, fromCache = true)
            }
        }
        return runCatching {
            val response = api.tracks(
                clientId = clientId,
                format = "json",
                limit = limit,
                order = "relevance",
                search = query,
                audioFormat = "mp31",
            )
            val tracks = response.results.mapNotNull { it.toTrack() }
            if (tracks.isEmpty()) {
                SourceResult.Success(DemoCatalog.search(query))
            } else {
                cache.put(key, json.encodeToString(SearchPayload.serializer(), SearchPayload(tracks)))
                SourceResult.Success(tracks)
            }
        }.getOrElse { it.asJamendoFailure() }
    }

    override suspend fun getHome(): SourceResult<HomeFeed> {
        if (clientId.isBlank()) return SourceResult.Success(DemoCatalog.feed)

        val popular = cachedTracks("jamendo:popular", 20) {
            api.tracks(
                clientId = clientId,
                format = "json",
                limit = 20,
                order = "popularity_total",
                search = null,
                audioFormat = "mp31",
            )
        }

        val fresh = cachedTracks("jamendo:fresh", 12) {
            api.tracks(
                clientId = clientId,
                format = "json",
                limit = 12,
                order = "releasedate_desc",
                search = null,
                audioFormat = "mp31",
            )
        }

        val tracks = popular.getOrNull().orEmpty()
        if (tracks.isEmpty()) {
            return popular as? SourceResult.Failure ?: SourceResult.Success(DemoCatalog.feed)
        }

        return SourceResult.Success(
            HomeFeed(
                hero = tracks.take(5).map { HeroSlide(track = it) },
                continuePlaying = tracks.drop(5).take(6),
                sections = listOf(
                    HomeSection(
                        id = "jamendo-new",
                        title = "New songs you may like",
                        subtitle = "Fresh under Creative Commons",
                        tracks = fresh.getOrNull().orEmpty().ifEmpty { tracks.takeLast(8) },
                    ),
                    HomeSection(
                        id = "jamendo-popular",
                        title = "Popular on Jamendo",
                        tracks = tracks,
                    ),
                ),
            ),
        )
    }

    override suspend fun getTrending(limit: Int): SourceResult<List<Track>> {
        if (clientId.isBlank()) return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        return cachedTracks("jamendo:trending:$limit", limit) {
            api.tracks(
                clientId = clientId,
                format = "json",
                limit = limit,
                order = "popularity_month",
                search = null,
                audioFormat = "mp31",
            )
        }
    }

    override suspend fun getPlaylist(playlistId: String): SourceResult<PlaylistDetail> {
        if (clientId.isBlank()) return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        return runCatching {
            val response = api.playlistTracks(
                clientId = clientId,
                format = "json",
                playlistId = playlistId,
                limit = 100,
                audioFormat = "mp31",
            )
            val tracks = response.results.mapNotNull { it.toTrack() }
            SourceResult.Success(
                PlaylistDetail(
                    playlist = Playlist(
                        id = playlistId,
                        title = "Jamendo playlist",
                        trackCount = tracks.size,
                        sourceId = SourceId.JAMENDO,
                    ),
                    tracks = tracks,
                ),
            )
        }.getOrElse { it.asJamendoFailure() }
    }

    override suspend fun play(track: Track): SourceResult<PlaybackHandle> {
        val url = track.mediaUri
        return when {
            url.isNullOrBlank() -> SourceResult.Success(
                PlaybackHandle.Unavailable("Jamendo did not return an audio URL for this track."),
            )
            else -> SourceResult.Success(PlaybackHandle.Stream(url))
        }
    }

    private suspend fun cachedTracks(
        key: String,
        limit: Int,
        fetch: suspend () -> JamendoEnvelope<JamendoTrackDto>,
    ): SourceResult<List<Track>> {
        val cached = cache.get(key)
        if (cached != null) {
            val payload = runCatching {
                json.decodeFromString(SearchPayload.serializer(), cached)
            }.getOrNull()
            if (payload != null && payload.tracks.isNotEmpty()) {
                return SourceResult.Success(payload.tracks, fromCache = true)
            }
        }
        return runCatching {
            val tracks = fetch().results.mapNotNull { it.toTrack() }
            if (tracks.isNotEmpty()) {
                cache.put(key, json.encodeToString(SearchPayload.serializer(), SearchPayload(tracks)))
            }
            SourceResult.Success(tracks.take(limit))
        }.getOrElse { it.asJamendoFailure() }
    }

    private fun JamendoTrackDto.toTrack(): Track? {
        if (id.isBlank() || audio.isNullOrBlank()) return null
        return Track(
            id = "jamendo-$id",
            title = name ?: "Untitled",
            artist = artistName ?: "Unknown artist",
            album = albumName,
            artworkUri = album_image ?: image,
            mediaUri = audio,
            durationMs = duration.toLong() * 1000L,
            sourceId = SourceId.JAMENDO,
            externalUrl = shareurl,
            licenseNote = if (licenseUrl != null) "Creative Commons · $licenseUrl" else "Creative Commons · Jamendo",
        )
    }
}

@kotlinx.serialization.Serializable
internal data class SearchPayload(val tracks: List<Track>)

private fun Throwable.asJamendoFailure(): SourceResult.Failure = when (this) {
    is java.io.IOException -> SourceResult.Failure(SourceErrorType.NETWORK, message)
    is retrofit2.HttpException -> when (code()) {
        401, 403 -> SourceResult.Failure(SourceErrorType.UNAUTHORIZED, message)
        404 -> SourceResult.Failure(SourceErrorType.NOT_FOUND, message)
        else -> SourceResult.Failure(SourceErrorType.UNKNOWN, message)
    }
    else -> SourceResult.Failure(SourceErrorType.UNKNOWN, message)
}
