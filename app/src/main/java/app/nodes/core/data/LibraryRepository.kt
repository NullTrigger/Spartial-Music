/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import app.nodes.core.data.db.LibraryDao
import app.nodes.core.data.db.LikedTrackEntity
import app.nodes.core.data.db.PlayHistoryEntity
import app.nodes.core.data.db.PlaylistEntity
import app.nodes.core.data.db.TrackDao
import app.nodes.core.data.db.toEntity
import app.nodes.core.data.db.toTrack
import app.nodes.core.model.ArtistStat
import app.nodes.core.model.ListeningStats
import app.nodes.core.model.Playlist
import app.nodes.core.model.PlaylistDetail
import app.nodes.core.model.SourceId
import app.nodes.core.model.Track
import app.nodes.core.model.TrackStat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** How many rows each ranking on the stats screen shows. */
private const val STATS_RANK_LIMIT = 10

/**
 * Everything the user owns: likes, playlists, history. All of it lives in the
 * local Room database - there is no account and no sync (PRIVACY.md).
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val libraryDao: LibraryDao,
) {
    val likedTracks: Flow<List<Track>> = libraryDao.likedTracks().map { rows -> rows.map { it.toTrack() } }

    val recentlyPlayed: Flow<List<Track>> = libraryDao.recentlyPlayed(20).map { rows -> rows.map { it.toTrack() } }

    val playlists: Flow<List<Playlist>> = libraryDao.playlists().map { rows ->
        rows.map { entity ->
            Playlist(
                id = entity.id,
                title = entity.name,
                description = entity.description,
                trackCount = libraryDao.trackCount(entity.id),
                sourceId = SourceId.LOCAL,
            )
        }
    }

    fun isLiked(trackId: String): Flow<Boolean> = libraryDao.isLiked(trackId)

    /** Everything the cache holds for one artist name, newest first. */
    fun artistTracks(artist: String): Flow<List<Track>> =
        trackDao.byArtist(artist).map { rows -> rows.map { it.toTrack() } }

    /** Everything the cache holds for one album name, newest first. */
    fun albumTracks(album: String): Flow<List<Track>> =
        trackDao.byAlbum(album).map { rows -> rows.map { it.toTrack() } }

    /**
     * Listening stats for the window starting at [since] (epoch millis). All
     * three queries re-emit whenever play_history or tracks change, so an open
     * stats screen keeps counting while music is playing. Minutes are an
     * estimate: see [ListeningStats].
     */
    fun listeningStats(since: Long): Flow<ListeningStats> = combine(
        libraryDao.playTotals(since),
        libraryDao.topTracks(since, STATS_RANK_LIMIT),
        libraryDao.topArtists(since, STATS_RANK_LIMIT),
    ) { totals, tracks, artists ->
        ListeningStats(
            plays = totals.plays,
            durationMs = totals.durationMs,
            topTracks = tracks.map { TrackStat(track = it.track.toTrack(), plays = it.plays) },
            topArtists = artists.map { ArtistStat(artist = it.artist, plays = it.plays, durationMs = it.durationMs) },
        )
    }

    suspend fun cacheTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        trackDao.upsertAll(tracks.map { it.toEntity() })
    }

    suspend fun toggleLike(track: Track): Boolean {
        trackDao.upsert(track.toEntity())
        // Reads the first emission; the table is tiny.
        val alreadyLiked = libraryDao.isLiked(track.id).first()
        return if (alreadyLiked) {
            libraryDao.unlike(track.id)
            false
        } else {
            libraryDao.like(LikedTrackEntity(track.id, System.currentTimeMillis()))
            true
        }
    }

    suspend fun recordPlay(track: Track) {
        trackDao.upsert(track.toEntity())
        libraryDao.recordPlay(PlayHistoryEntity(trackId = track.id, playedAt = System.currentTimeMillis()))
    }

    suspend fun createPlaylist(name: String): Playlist {
        val now = System.currentTimeMillis()
        val entity = PlaylistEntity(
            id = "local-playlist-${UUID.randomUUID()}",
            name = name,
            description = null,
            createdAt = now,
            updatedAt = now,
        )
        libraryDao.upsertPlaylist(entity)
        return Playlist(
            id = entity.id,
            title = entity.name,
            trackCount = 0,
            sourceId = SourceId.LOCAL,
        )
    }

    suspend fun addToPlaylist(playlistId: String, track: Track) {
        trackDao.upsert(track.toEntity())
        libraryDao.addToPlaylist(playlistId, track.id)
    }

    suspend fun playlistDetail(playlistId: String): PlaylistDetail? {
        val rows = libraryDao.tracksInPlaylist(playlistId)
        val playlist = playlists.first().firstOrNull { it.id == playlistId } ?: return null
        return PlaylistDetail(playlist = playlist, tracks = rows.map { it.toTrack() })
    }

    suspend fun deletePlaylist(playlistId: String) {
        libraryDao.deletePlaylistTracks(playlistId)
        libraryDao.deletePlaylist(playlistId)
    }
}
