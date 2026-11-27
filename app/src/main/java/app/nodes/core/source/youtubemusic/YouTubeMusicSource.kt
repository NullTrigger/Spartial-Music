/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.youtubemusic

import app.nodes.core.data.RemoteCache
import app.nodes.core.data.SettingsRepository
import app.nodes.core.model.HeroSlide
import app.nodes.core.model.HomeFeed
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
import app.nodes.core.sync.YouTubePlaylistLink
import app.nodes.core.sync.YouTubePlaylistLinks
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * YouTube Music through the unofficial InnerTube API.
 *
 * On by default in this fork by maintainer decision (recorded in YOUTUBE.md),
 * because the official Data API cannot show a real music catalogue. It is
 * outside the YouTube API Services Terms and can stop working at any time; the
 * official Data API source stays registered, but the UI has no source picker
 * any more, so this is the only provider Home, Search and playback can reach.
 *
 * Playback is [PlaybackHandle.Stream] straight into Media3. A separate,
 * user-initiated Mode 2 feature may use the same resolved URL for an app-private
 * offline download. There is no ad skipping and no transcoding.
 */
@Singleton
class YouTubeMusicSource @Inject constructor(
    private val innerTube: InnerTubeClient,
    private val settings: SettingsRepository,
    private val remoteCache: RemoteCache,
    private val json: Json,
    private val playlistLinks: YouTubePlaylistLinks,
) : MusicSource {

    override val id: SourceId = SourceId.YOUTUBE_MUSIC
    override val displayName: String = "YouTube Music (unofficial)"

    override suspend fun availability(): SourceAvailability =
        SourceAvailability(id, SourceStatus.READY)

    override suspend fun search(query: String, limit: Int): SourceResult<List<Track>> {
        if (query.isBlank()) return SourceResult.Success(emptyList())
        return runCatching {
            typeTracks(query, limit) ?: innerTube.search(query, limit)
        }
            .fold(
                onSuccess = { SourceResult.Success(it) },
                onFailure = { it.asFailure() },
            )
    }

    /**
     * Songs of a kind of music, when that is what was asked for.
     *
     * "Disco" is not a title search: the catalogue keeps editorial playlists
     * for it, so the playlists YouTube returns for the word are asked for the
     * same way, and the first one whose name matches is opened for its tracks.
     * Anything that is not a type word - a track name, an artist, "one love" -
     * returns null here and keeps the exact title search.
     *
     * Null, not empty, so an empty playlist cannot silence a real search: if
     * the lists or the page come back empty, the caller falls back.
     */
    private suspend fun typeTracks(query: String, limit: Int): List<Track>? {
        if (!MusicTypes.isType(query)) return null
        val word = query.trim()
        val playlists = runCatching { innerTube.searchPlaylists(word, 6) }
            .getOrDefault(emptyList())
        if (playlists.isEmpty()) return null
        // The word is in the name of the list YouTube keeps for it ("Disco",
        // "Sad Songs"); the rest of the answer is related shelf noise.
        val pick = playlists.firstOrNull { it.title.contains(word, ignoreCase = true) }
            ?: playlists.first()
        val page = runCatching { innerTube.collection(pick.id, limit) }.getOrNull() ?: return null
        return page.tracks.take(limit.coerceAtLeast(1)).takeIf { it.isNotEmpty() }
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
        val cacheKey = "ytm:playlist:$playlistId"
        val result = runCatching { innerTube.collection(playlistId, 200) }
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
        if (result is SourceResult.Success) {
            // A page that loaded once stays readable without a network.
            runCatching {
                remoteCache.put(cacheKey, json.encodeToString(result.data))
            }
            return result
        }
        // Offline or a hiccup: the last copy that loaded beats an error
        // screen, and RemoteCache drops anything older than a week.
        val cached = runCatching {
            remoteCache.get(cacheKey, RemoteCache.MAX_AGE_MS)
                ?.let { json.decodeFromString<PlaylistDetail>(it) }
        }.getOrNull()
        return if (cached != null && cached.tracks.isNotEmpty()) {
            SourceResult.Success(cached, fromCache = true)
        } else {
            result
        }
    }

    /** The signed-in user's own playlists; empty when signed out. */
    suspend fun userPlaylists(): List<Playlist> {
        val fresh = runCatching { innerTube.libraryPlaylists() }.getOrNull()
        if (fresh != null) {
            if (fresh.isNotEmpty()) {
                runCatching { remoteCache.put(MY_PLAYLISTS_KEY, json.encodeToString(fresh)) }
            }
            return fresh
        }
        return runCatching {
            remoteCache.get(MY_PLAYLISTS_KEY, RemoteCache.MAX_AGE_MS)
                ?.let { json.decodeFromString<List<Playlist>>(it) }
        }.getOrNull().orEmpty()
    }

    /** The signed-in user's liked songs; empty when signed out. */
    suspend fun likedSongs(): List<Track> {
        val fresh = runCatching { innerTube.collection(LIKED_SONGS_ID, 200) }
            .getOrNull()?.tracks
        if (fresh != null) {
            if (fresh.isNotEmpty()) {
                runCatching { remoteCache.put(LIKED_SONGS_KEY, json.encodeToString(fresh)) }
            }
            return fresh
        }
        return runCatching {
            remoteCache.get(LIKED_SONGS_KEY, RemoteCache.MAX_AGE_MS)
                ?.let { json.decodeFromString<List<Track>>(it) }
        }.getOrNull().orEmpty()
    }

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
        data class Done(
            val playlistId: String,
            val added: Int,
            val missed: Int,
            /** True the first time, false when an existing copy was refreshed. */
            val created: Boolean = false,
        ) : PlaylistPush
        data object SignedOut : PlaylistPush
        data object Failed : PlaylistPush
    }

    /**
     * Copies a local playlist to the account, or brings the copy it made before
     * up to date.
     *
     * The first call matches every local track to a YouTube video by title and
     * artist and creates a private playlist holding those videos. Later calls
     * append only what that playlist does not hold yet, which is what lets a
     * link survive the app being restarted and the playlist being edited -
     * "copied" becomes "kept in step" instead of a second, duplicate playlist.
     *
     * Matches are remembered by [YouTubePlaylistLinks] so a refresh costs one
     * append rather than one search per song. Tracks that cannot be matched are
     * counted, never guessed: a wrong song in someone's playlist is worse than a
     * missing one.
     *
     * @param localPlaylistId the local playlist's id, so the copy can be found
     *        again. Null keeps the old behaviour of always creating a new list.
     */
    suspend fun pushPlaylist(
        name: String,
        tracks: List<Track>,
        localPlaylistId: String? = null,
    ): PlaylistPush {
        if (!innerTube.isSignedIn) return PlaylistPush.SignedOut
        val link = localPlaylistId?.let { id -> playlistLinks.find(id) }
        val matches = LinkedHashMap<String, String>(link?.matches.orEmpty())
        var missed = 0
        val fresh = mutableListOf<String>()
        for (track in tracks) {
            if (matches.containsKey(track.id)) continue
            val query = listOf(track.title, track.artist).filter { it.isNotBlank() }
                .joinToString(" ")
            val match = runCatching { innerTube.search(query, 3) }.getOrDefault(emptyList())
                .firstOrNull { it.videoId != null }
                ?.videoId
            if (match != null) {
                matches[track.id] = match
                fresh += match
            } else {
                missed++
            }
        }
        if (matches.isEmpty()) return PlaylistPush.Failed

        val existing = link?.remotePlaylistId
        if (existing == null) {
            val id = runCatching {
                innerTube.createPlaylist(name, null, matches.values.toList())
            }.getOrNull() ?: return PlaylistPush.Failed
            remember(localPlaylistId, id, matches)
            return PlaylistPush.Done(
                playlistId = id,
                added = matches.size,
                missed = missed,
                created = true,
            )
        }

        if (fresh.isNotEmpty()) {
            val ok = runCatching { innerTube.addToPlaylist(existing, fresh) }.getOrDefault(false)
            if (!ok) {
                // The copy is gone (deleted on the account) or refusing writes.
                // Dropping the link lets the next attempt create a fresh copy
                // instead of retrying the same dead playlist forever.
                localPlaylistId?.let { id -> runCatching { playlistLinks.forget(id) } }
                return PlaylistPush.Failed
            }
        }
        remember(localPlaylistId, existing, matches)
        return PlaylistPush.Done(playlistId = existing, added = fresh.size, missed = missed)
    }

    /** Stores what the remote playlist now holds, when the local id is known. */
    private suspend fun remember(
        localPlaylistId: String?,
        remotePlaylistId: String,
        matches: Map<String, String>,
    ) {
        if (localPlaylistId == null) return
        runCatching {
            playlistLinks.save(YouTubePlaylistLink(localPlaylistId, remotePlaylistId, matches))
        }
    }

    override suspend fun play(track: Track): SourceResult<PlaybackHandle> {
        val videoId = track.videoId ?: return SourceResult.Success(
            PlaybackHandle.Unavailable("This track has no YouTube video id."),
        )
        val current = settings.current()
        val outcome = runCatching {
            innerTube.audioUrl(
                videoId = videoId,
                preferHighQuality = current.highQualityAudio,
                // A play is what the account's history is made of; a prefetch
                // below deliberately leaves this off. Reporting without a
                // session would only be an anonymous ping.
                reportToHistory = current.syncYouTube && innerTube.isSignedIn,
            )
        }
            .getOrElse {
                return SourceResult.Success(
                    PlaybackHandle.Unavailable(
                        "Could not reach YouTube Music. Check your connection and try again.",
                    ),
                )
            }

        return when (outcome) {
            is InnerTubeStream.Playable -> SourceResult.Success(
                PlaybackHandle.Stream(outcome.url, outcome.userAgent),
            )
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
     * Albums and EPs the service itself lists as new. A browse page of cards
     * rather than songs, so the answer is a list of collections the New
     * releases screen opens like any other album page.
     */
    suspend fun newReleases(limit: Int = 30): SourceResult<List<Playlist>> =
        runCatching { innerTube.newReleases(limit) }
            .fold(
                onSuccess = { SourceResult.Success(it) },
                onFailure = { it.asFailure() },
            )

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

        /** RemoteCache keys for the signed-in user's own lists. */
        const val MY_PLAYLISTS_KEY = "ytm:my-playlists"
        const val LIKED_SONGS_KEY = "ytm:liked-songs"
    }
}
