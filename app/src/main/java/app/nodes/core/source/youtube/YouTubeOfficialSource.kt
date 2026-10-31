/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.youtube

import app.nodes.BuildConfig
import app.nodes.core.data.QuotaTracker
import app.nodes.core.data.RemoteCache
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
import app.nodes.core.source.DemoCatalog
import app.nodes.core.source.MusicSource
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * YouTube integration, Mode 1 only: the official Data API v3 plus the official
 * embedded player.
 *
 * Hard rules baked into this class (see YOUTUBE.md):
 *  - never extract a stream URL, never download media;
 *  - playback is delegated to the visible IFrame player via
 *    [PlaybackHandle.EmbeddedVideo];
 *  - every network call is quota-accounted and cached, and a spent quota turns
 *    into a friendly error instead of a raw HTTP failure.
 */
@Singleton
class YouTubeOfficialSource @Inject constructor(
    @Named("youtube") private val api: YouTubeApi,
    private val quota: QuotaTracker,
    private val cache: RemoteCache,
    private val signIn: YouTubeSignIn,
    private val settings: SettingsRepository,
    private val json: Json,
) : MusicSource {

    override val id: SourceId = SourceId.YOUTUBE
    override val displayName: String = "YouTube (official API)"

    private val apiKey: String get() = BuildConfig.YOUTUBE_API_KEY

    override suspend fun availability(): SourceAvailability = when {
        apiKey.isBlank() -> SourceAvailability(
            id,
            SourceStatus.NEEDS_API_KEY,
            "Add YOUTUBE_API_KEY to local.properties, then rebuild. See YOUTUBE.md.",
        )
        quota.remaining() <= 0 -> SourceAvailability(
            id,
            SourceStatus.ERROR,
            "Daily YouTube quota used up. It resets at midnight Pacific Time.",
        )
        else -> SourceAvailability(id, SourceStatus.READY)
    }

    override suspend fun search(query: String, limit: Int): SourceResult<List<Track>> {
        if (apiKey.isBlank()) return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        if (query.isBlank()) return SourceResult.Success(emptyList())

        val cacheKey = "youtube:search:${query.lowercase()}:$limit"
        readCache(cacheKey)?.let { return SourceResult.Success(it, fromCache = true) }

        if (!quota.canAfford(QuotaTracker.COST_SEARCH)) {
            return SourceResult.Failure(SourceErrorType.QUOTA_EXCEEDED)
        }

        return try {
            quota.spend(QuotaTracker.COST_SEARCH)
            val response = api.search(
                part = "snippet",
                type = "video",
                videoCategoryId = MUSIC_CATEGORY_ID,
                query = query,
                maxResults = limit.coerceIn(1, 50),
                key = apiKey,
            )
            val tracks = response.items.mapNotNull { item ->
                val videoId = item.id?.videoId ?: return@mapNotNull null
                item.snippet.toTrack(videoId)
            }
            writeCache(cacheKey, tracks)
            SourceResult.Success(tracks)
        } catch (t: Throwable) {
            t.asYouTubeFailure()
        }
    }

    override suspend fun getHome(): SourceResult<HomeFeed> {
        if (apiKey.isBlank()) return SourceResult.Success(DemoCatalog.feed)

        val region = settings.current().youtubeRegion
        val chart = trendingTracks(region, 20)
        val trending = chart.getOrNull().orEmpty()
        if (trending.isEmpty()) {
            return chart as? SourceResult.Failure ?: SourceResult.Success(DemoCatalog.feed)
        }

        val playlists = popularPlaylists()
        val playlistsResult = playlists.getOrNull().orEmpty()

        val sections = mutableListOf<HomeSection>()
        if (playlistsResult.isNotEmpty()) {
            sections += HomeSection(
                id = "youtube-playlists",
                title = "New songs you may like",
                subtitle = "Curated YouTube music playlists",
                tracks = playlistsResult.mapNotNull { it.asTrack() },
            )
        }

        val firstPlaylistTracks = playlistsResult.firstOrNull()?.id?.let { playlistId ->
            playlistTracks(playlistId, 12).getOrNull().orEmpty()
        }.orEmpty()

        if (firstPlaylistTracks.isNotEmpty()) {
            sections += HomeSection(
                id = "youtube-fresh",
                title = playlistsResult.firstOrNull()?.title ?: "Fresh from YouTube",
                tracks = firstPlaylistTracks,
                playlistId = playlistsResult.firstOrNull()?.id,
            )
        }

        sections += HomeSection(
            id = "youtube-chart",
            title = "Trending now",
            subtitle = "YouTube Music chart · $region",
            tracks = trending,
        )

        return SourceResult.Success(
            HomeFeed(
                hero = trending.take(5).map { HeroSlide(track = it) },
                continuePlaying = trending.drop(5).take(6),
                // Signed-in content sits above the public charts.
                sections = personalSections() + sections,
            ),
        )
    }

    override suspend fun getTrending(limit: Int): SourceResult<List<Track>> {
        if (apiKey.isBlank()) return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        return trendingTracks(settings.current().youtubeRegion, limit)
    }

    override suspend fun getPlaylist(playlistId: String): SourceResult<PlaylistDetail> {
        if (apiKey.isBlank()) return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        return playlistTracks(playlistId, 50).map { tracks ->
            PlaylistDetail(
                playlist = Playlist(
                    id = playlistId,
                    title = "YouTube playlist",
                    trackCount = tracks.size,
                    sourceId = SourceId.YOUTUBE,
                    externalUrl = "https://music.youtube.com/playlist?list=$playlistId",
                ),
                tracks = tracks,
            )
        }
    }

    override suspend fun play(track: Track): SourceResult<PlaybackHandle> {
        val videoId = track.videoId
            ?: track.id.removePrefix("youtube-").takeIf { it.isNotBlank() }
        return if (videoId.isNullOrBlank()) {
            SourceResult.Success(PlaybackHandle.Unavailable("This track has no YouTube video id."))
        } else {
            // Deliberately never resolves a stream URL: the official player plays it.
            SourceResult.Success(PlaybackHandle.EmbeddedVideo(videoId))
        }
    }

    /** The signed-in user's own playlists, when OAuth is configured. */
    suspend fun myPlaylists(): SourceResult<List<Playlist>> {
        val token = signIn.currentToken()
        if (apiKey.isBlank() || token.isNullOrBlank()) {
            return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        }
        val cacheKey = "youtube:my-playlists"
        cache.get(cacheKey, 5 * 60 * 1000L)?.let { body ->
            val cached = runCatching {
                json.decodeFromString(PlaylistPayload.serializer(), body).playlists
            }.getOrNull()
            if (!cached.isNullOrEmpty()) {
                return SourceResult.Success(cached, fromCache = true)
            }
        }
        if (!quota.canAfford(QuotaTracker.COST_LIST)) {
            return SourceResult.Failure(SourceErrorType.QUOTA_EXCEEDED)
        }
        return try {
            quota.spend(QuotaTracker.COST_LIST)
            val response = api.myPlaylists(
                part = "snippet,contentDetails",
                mine = "true",
                maxResults = 25,
                key = apiKey,
                authorization = "Bearer $token",
            )
            val playlists = response.items.mapNotNull { it.asPlaylist() }
            cache.put(cacheKey, json.encodeToString(PlaylistPayload.serializer(), PlaylistPayload(playlists)))
            SourceResult.Success(playlists)
        } catch (t: Throwable) {
            t.asYouTubeFailure()
        }
    }

    /**
     * The signed-in user's liked videos, which is the closest thing the Data API
     * offers to "my YouTube Music": their account's own music, played through
     * the official player like everything else.
     */
    suspend fun myLikedTracks(limit: Int = 20): SourceResult<List<Track>> {
        val token = signIn.currentToken()
        if (apiKey.isBlank() || token.isNullOrBlank()) {
            return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        }
        val cacheKey = "youtube:liked:$limit"
        readCache(cacheKey)?.let { return SourceResult.Success(it, fromCache = true) }
        if (!quota.canAfford(QuotaTracker.COST_LIST)) {
            return SourceResult.Failure(SourceErrorType.QUOTA_EXCEEDED)
        }
        return try {
            quota.spend(QuotaTracker.COST_LIST)
            val response = api.likedVideos(
                part = "snippet,contentDetails",
                myRating = "like",
                maxResults = limit.coerceIn(1, 50),
                key = apiKey,
                authorization = "Bearer $token",
            )
            val tracks = response.items.mapNotNull { item ->
                val videoId = item.id ?: return@mapNotNull null
                item.snippet.toTrack(videoId, item.contentDetails?.duration)
            }
            writeCache(cacheKey, tracks)
            SourceResult.Success(tracks)
        } catch (t: Throwable) {
            t.asYouTubeFailure()
        }
    }

    /**
     * Rails that only exist while signed in. Failure is soft: a spent quota or a
     * stale token must not take the public charts down with it.
     */
    private suspend fun personalSections(): List<HomeSection> {
        if (signIn.currentToken().isNullOrBlank()) return emptyList()
        val sections = mutableListOf<HomeSection>()

        val liked = myLikedTracks(20).getOrNull().orEmpty()
        if (liked.isNotEmpty()) {
            sections += HomeSection(
                id = "youtube-liked",
                title = "Liked on YouTube",
                subtitle = signIn.currentEmail() ?: "Your Google account",
                tracks = liked,
            )
        }

        // A rail of playlist cards would not be playable, so show the tracks of
        // the user's most recent own playlist instead.
        val first = myPlaylists().getOrNull().orEmpty().firstOrNull()
        val fromOwnPlaylist = first?.let { playlistTracks(it.id, 20).getOrNull().orEmpty() }.orEmpty()
        if (first != null && fromOwnPlaylist.isNotEmpty()) {
            sections += HomeSection(
                id = "youtube-my-playlist",
                title = first.title,
                subtitle = "From your YouTube playlists",
                tracks = fromOwnPlaylist,
                playlistId = first.id,
            )
        }

        return sections
    }

    // ---------------------------------------------------------------------

    private suspend fun trendingTracks(region: String, limit: Int): SourceResult<List<Track>> {
        if (apiKey.isBlank()) return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        val cacheKey = "youtube:trending:$region:$limit"
        readCache(cacheKey)?.let { return SourceResult.Success(it, fromCache = true) }

        if (!quota.canAfford(QuotaTracker.COST_LIST)) {
            return SourceResult.Failure(SourceErrorType.QUOTA_EXCEEDED)
        }
        return try {
            quota.spend(QuotaTracker.COST_LIST)
            val response = api.mostPopular(
                part = "snippet,contentDetails",
                chart = "mostPopular",
                videoCategoryId = MUSIC_CATEGORY_ID,
                regionCode = region,
                maxResults = limit.coerceIn(1, 50),
                key = apiKey,
            )
            val tracks = response.items.mapNotNull { item ->
                val videoId = item.id ?: return@mapNotNull null
                item.snippet.toTrack(videoId, item.contentDetails?.duration)
            }
            writeCache(cacheKey, tracks)
            SourceResult.Success(tracks)
        } catch (t: Throwable) {
            t.asYouTubeFailure()
        }
    }

    private suspend fun popularPlaylists(): SourceResult<List<Playlist>> {
        if (apiKey.isBlank()) return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        val cacheKey = "youtube:playlists:popular"
        cache.get(cacheKey)?.let { body ->
            val cached = runCatching {
                json.decodeFromString(PlaylistPayload.serializer(), body).playlists
            }.getOrNull()
            if (!cached.isNullOrEmpty()) {
                return SourceResult.Success(cached, fromCache = true)
            }
        }
        if (!quota.canAfford(QuotaTracker.COST_LIST)) {
            return SourceResult.Failure(SourceErrorType.QUOTA_EXCEEDED)
        }
        return try {
            quota.spend(QuotaTracker.COST_LIST)
            val response = api.popularPlaylists(
                part = "snippet,contentDetails",
                chart = "mostPopular",
                maxResults = 10,
                key = apiKey,
            )
            val playlists = response.items.mapNotNull { it.asPlaylist() }
            cache.put(cacheKey, json.encodeToString(PlaylistPayload.serializer(), PlaylistPayload(playlists)))
            SourceResult.Success(playlists)
        } catch (t: Throwable) {
            t.asYouTubeFailure()
        }
    }

    private suspend fun playlistTracks(playlistId: String, limit: Int): SourceResult<List<Track>> {
        if (apiKey.isBlank()) return SourceResult.Failure(SourceErrorType.NO_CONFIGURATION)
        val cacheKey = "youtube:playlist:$playlistId:$limit"
        readCache(cacheKey)?.let { return SourceResult.Success(it, fromCache = true) }
        if (!quota.canAfford(QuotaTracker.COST_LIST)) {
            return SourceResult.Failure(SourceErrorType.QUOTA_EXCEEDED)
        }
        return try {
            quota.spend(QuotaTracker.COST_LIST)
            val response = api.playlistItems(
                part = "snippet,contentDetails",
                playlistId = playlistId,
                maxResults = limit.coerceIn(1, 50),
                key = apiKey,
            )
            val tracks = response.items.mapNotNull { entry ->
                val videoId = entry.contentDetails?.videoId ?: return@mapNotNull null
                entry.snippet.toTrack(videoId)
            }
            writeCache(cacheKey, tracks)
            SourceResult.Success(tracks)
        } catch (t: Throwable) {
            t.asYouTubeFailure()
        }
    }

    private suspend fun readCache(key: String): List<Track>? {
        val body = cache.get(key) ?: return null
        return runCatching {
            json.decodeFromString(TrackPayload.serializer(), body).tracks
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun writeCache(key: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        cache.put(key, json.encodeToString(TrackPayload.serializer(), TrackPayload(tracks)))
    }

    private fun Snippet?.toTrack(videoId: String, isoDuration: String? = null): Track {
        val snippet = this
        return Track(
            id = "youtube-$videoId",
            title = snippet?.title?.decodeHtml() ?: "Untitled",
            artist = snippet?.channelTitle ?: "YouTube",
            album = null,
            artworkUri = snippet?.thumbnails?.high?.url ?: snippet?.thumbnails?.medium?.url,
            mediaUri = null,
            durationMs = isoDuration?.let(::parseIso8601Duration) ?: 0L,
            sourceId = SourceId.YOUTUBE,
            videoId = videoId,
            externalUrl = "https://www.youtube.com/watch?v=$videoId",
        )
    }

    private fun PlaylistItem.asPlaylist(): Playlist? {
        val playlistId = id ?: return null
        return Playlist(
            id = playlistId,
            title = snippet?.title?.decodeHtml() ?: "YouTube playlist",
            artworkUri = snippet?.thumbnails?.high?.url ?: snippet?.thumbnails?.medium?.url,
            trackCount = contentDetails?.itemCount ?: 0,
            sourceId = SourceId.YOUTUBE,
            ownerName = snippet?.channelTitle,
            externalUrl = "https://music.youtube.com/playlist?list=$playlistId",
        )
    }

    private fun Playlist.asTrack(): Track? {
        val art = artworkUri ?: return null
        return Track(
            id = "youtube-playlist-$id",
            title = title,
            artist = ownerName ?: "YouTube playlist",
            artworkUri = art,
            mediaUri = null,
            sourceId = SourceId.YOUTUBE,
            externalUrl = externalUrl,
        )
    }

    private fun Throwable.asYouTubeFailure(): SourceResult.Failure = when (this) {
        is IOException -> SourceResult.Failure(SourceErrorType.NETWORK, message)
        is HttpException -> {
            val body = runCatching { response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
            when {
                code() == 403 && (body.contains("quotaExceeded") || body.contains("dailyLimitExceeded")) ->
                    SourceResult.Failure(SourceErrorType.QUOTA_EXCEEDED, "Daily quota exceeded")
                code() == 401 -> SourceResult.Failure(SourceErrorType.UNAUTHORIZED, message)
                code() == 400 && body.contains("keyInvalid") ->
                    SourceResult.Failure(SourceErrorType.NO_CONFIGURATION, "Invalid API key")
                code() == 404 -> SourceResult.Failure(SourceErrorType.NOT_FOUND, message)
                code() == 403 -> SourceResult.Failure(SourceErrorType.UNAUTHORIZED, message)
                else -> SourceResult.Failure(SourceErrorType.UNKNOWN, message)
            }
        }
        else -> SourceResult.Failure(SourceErrorType.UNKNOWN, message)
    }

    private companion object {
        const val MUSIC_CATEGORY_ID = "10"
    }
}

@kotlinx.serialization.Serializable
internal data class TrackPayload(val tracks: List<Track>)

@kotlinx.serialization.Serializable
internal data class PlaylistPayload(val playlists: List<Playlist>)

/** YouTube titles are HTML-escaped. */
internal fun String.decodeHtml(): String = this
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")

/** Parses ISO-8601 durations like PT3M42S (videos.list contentDetails.duration). */
internal fun parseIso8601Duration(value: String): Long {
    if (value.isBlank()) return 0L
    var millis = 0L
    var number = StringBuilder()
    for (char in value) {
        when (char) {
            in '0'..'9' -> number.append(char)
            'H' -> {
                millis += (number.toString().toLongOrNull() ?: 0L) * 3_600_000L
                number = StringBuilder()
            }
            'M' -> {
                millis += (number.toString().toLongOrNull() ?: 0L) * 60_000L
                number = StringBuilder()
            }
            'S' -> {
                millis += (number.toString().toLongOrNull() ?: 0L) * 1_000L
                number = StringBuilder()
            }
            else -> number = StringBuilder()
        }
    }
    return millis
}
