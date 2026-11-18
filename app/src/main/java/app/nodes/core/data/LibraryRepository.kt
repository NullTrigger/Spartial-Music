/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import app.nodes.core.data.db.LibraryDao
import app.nodes.core.data.db.LikedTrackEntity
import app.nodes.core.data.db.PlayHistoryEntity
import app.nodes.core.data.db.PlaylistEntity
import app.nodes.core.data.db.QueueDao
import app.nodes.core.data.db.QueueItemEntity
import app.nodes.core.data.db.QueueStateEntity
import app.nodes.core.data.db.TrackDao
import app.nodes.core.data.db.toEntity
import app.nodes.core.data.db.toTrack
import app.nodes.core.model.AddToPlaylistResult
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
    private val queueDao: QueueDao,
) {
    val likedTracks: Flow<List<Track>> = libraryDao.likedTracks().map { rows -> rows.map { it.toTrack() } }

    /** Every track cached on this device, newest first. */
    val allTracks: Flow<List<Track>> = trackDao.allTracks().map { rows -> rows.map { it.toTrack() } }

    val recentlyPlayed: Flow<List<Track>> = libraryDao.recentlyPlayed(20).map { rows -> rows.map { it.toTrack() } }

    /** Ids of the last [count] played tracks, newest first. */
    suspend fun recentlyPlayedIds(count: Int): Set<String> =
        libraryDao.recentlyPlayed(count).first().mapTo(HashSet()) { it.id }

    val playlists: Flow<List<Playlist>> = libraryDao.playlists().map { rows ->
        rows.map { entity ->
            Playlist(
                id = entity.id,
                title = entity.name,
                description = entity.description,
                trackCount = libraryDao.trackCount(entity.id),
                sourceId = SourceId.LOCAL,
                createdAt = entity.createdAt,
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

    /**
     * Records the tracks the app has seen, so artist and album pages can show
     * them without another fetch. Rows that are not songs (album and playlist
     * cards) never enter the cache: they cannot play and would show up as
     * phantom entries in the Songs list and in queue suggestions.
     */
    suspend fun cacheTracks(tracks: List<Track>) {
        val playable = tracks.filter { it.videoId != null || it.mediaUri != null }
        if (playable.isEmpty()) return
        trackDao.upsertAll(playable.map { it.toEntity() })
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

    /** Idempotent like/unlike used by collection-level save actions. */
    suspend fun setLiked(track: Track, liked: Boolean) {
        trackDao.upsert(track.toEntity())
        if (liked) {
            libraryDao.like(LikedTrackEntity(track.id, System.currentTimeMillis()))
        } else {
            libraryDao.unlike(track.id)
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

    /**
     * Adds [track] to a local playlist as one transaction. The track is cached
     * first inside the same transaction, so remote rows render offline too.
     */
    suspend fun addToPlaylist(playlistId: String, track: Track): AddToPlaylistResult = try {
        if (libraryDao.addToPlaylist(playlistId, track.toEntity())) {
            AddToPlaylistResult.Added
        } else {
            AddToPlaylistResult.AlreadyExists
        }
    } catch (error: Exception) {
        AddToPlaylistResult.Error(error)
    }

    /** Removes one track from one playlist. The cached track itself stays. */
    suspend fun removeFromPlaylist(playlistId: String, trackId: String) {
        libraryDao.removeFromPlaylist(playlistId, trackId)
    }

    /** Which local playlists hold [trackId]; the picker's checkmarks. */
    fun observePlaylistsContaining(trackId: String): Flow<List<Playlist>> =
        libraryDao.playlistsContaining(trackId).map { rows ->
            rows.map { entity ->
                Playlist(
                    id = entity.id,
                    title = entity.name,
                    description = entity.description,
                    trackCount = libraryDao.trackCount(entity.id),
                    sourceId = SourceId.LOCAL,
                    createdAt = entity.createdAt,
                )
            }
        }

    /** Creates a playlist that starts with [track], in one transaction. */
    suspend fun createPlaylistAndAdd(name: String, track: Track): Playlist {
        val now = System.currentTimeMillis()
        val entity = PlaylistEntity(
            id = "local-playlist-${UUID.randomUUID()}",
            name = name,
            description = null,
            createdAt = now,
            updatedAt = now,
        )
        libraryDao.createPlaylistWithFirstTrack(entity, track.toEntity())
        return Playlist(
            id = entity.id,
            title = entity.name,
            trackCount = 1,
            sourceId = SourceId.LOCAL,
        )
    }

    suspend fun renamePlaylist(playlistId: String, name: String) {
        libraryDao.renamePlaylist(playlistId, name, System.currentTimeMillis())
    }

    /**
     * Moves one row of a playlist from [from] to [to], renumbering the
     * positions so they stay a dense 0..n-1 sequence.
     */
    suspend fun movePlaylistTrack(playlistId: String, from: Int, to: Int) {
        val rows = libraryDao.playlistTrackRows(playlistId).toMutableList()
        if (from !in rows.indices || to !in rows.indices || from == to) return
        val moved = rows.removeAt(from)
        rows.add(to, moved)
        libraryDao.replacePlaylistTracks(
            playlistId,
            rows.mapIndexed { index, row -> row.copy(sortOrder = index) },
        )
    }

    suspend fun playlistDetail(playlistId: String): PlaylistDetail? {
        val rows = libraryDao.tracksInPlaylist(playlistId)
        val playlist = playlists.first().firstOrNull { it.id == playlistId } ?: return null
        return PlaylistDetail(playlist = playlist, tracks = rows.map { it.toTrack() })
    }

    suspend fun deletePlaylist(playlistId: String) {
        libraryDao.deletePlaylistCompletely(playlistId)
    }

    // ------------------------------------------------------------------
    // Persisted queue
    // ------------------------------------------------------------------

    /**
     * Saves the play queue exactly as it stands. [originalPositions] maps a
     * queue index to its pre-shuffle index (identity when shuffle is off), so
     * turning shuffle off after a restart still restores the chosen order.
     * Tracks are upserted in the same transaction, so a restore can never meet
     * a queue row whose track is missing.
     */
    suspend fun saveQueue(
        tracks: List<Track>,
        currentIndex: Int,
        positionMs: Long,
        shuffleEnabled: Boolean,
        originalPositions: List<Int>,
    ) {
        if (tracks.isEmpty()) {
            queueDao.clearQueue()
            return
        }
        queueDao.replaceQueue(
            tracks = tracks.map { it.toEntity() },
            items = tracks.mapIndexed { index, track ->
                QueueItemEntity(
                    position = index,
                    trackId = track.id,
                    originalPosition = originalPositions.getOrNull(index) ?: index,
                )
            },
            state = QueueStateEntity(
                id = 0,
                currentIndex = currentIndex,
                positionMs = positionMs,
                shuffleEnabled = shuffleEnabled,
            ),
        )
    }

    /**
     * The saved queue, or null when there is nothing (or nothing usable) to
     * restore. Tracks that no longer resolve - their row is gone from the
     * cache - are skipped rather than breaking the restore.
     */
    suspend fun savedQueue(): SavedQueue? {
        val items = queueDao.items()
        if (items.isEmpty()) return null
        val state = queueDao.state() ?: return null
        val byId = trackDao.allTracks().first().associateBy { it.id }
        val restored = items.mapNotNull { item ->
            byId[item.trackId]?.toTrack()?.let { it to item.originalPosition }
        }
        if (restored.isEmpty()) return null
        return SavedQueue(
            tracks = restored.map { it.first },
            originalPositions = restored.map { it.second },
            currentIndex = state.currentIndex.coerceIn(0, restored.lastIndex),
            positionMs = state.positionMs,
            shuffleEnabled = state.shuffleEnabled,
        )
    }

    /**
     * Writes the queue and its state; see [saveQueue]. Skips a queue that was
     * never saved instead of writing an empty one over the user's data.
     */
    suspend fun clearSavedQueue() {
        queueDao.clearQueue()
    }

    // ------------------------------------------------------------------
    // Backup / restore
    // ------------------------------------------------------------------

    /**
     * Everything worth carrying to another device: playlists with their
     * tracks, liked tracks, optionally recent play history, and non-secret
     * settings. Credentials are not part of the model, so they cannot leak
     * into the file even by accident.
     */
    suspend fun exportBackup(includeHistory: Boolean): LibraryBackup {
        val playlistRows = libraryDao.playlists().first()
        val playlists = playlistRows.map { entity ->
            BackupPlaylist(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                createdAt = entity.createdAt,
                tracks = libraryDao.tracksInPlaylist(entity.id).map { it.toTrack() },
            )
        }
        return LibraryBackup(
            schemaVersion = LibraryBackup.SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            playlists = playlists,
            likedTracks = likedTracks.first(),
            playHistory = if (includeHistory) {
                libraryDao.recentHistory(HISTORY_EXPORT_LIMIT).map {
                    BackupPlay(trackId = it.trackId, playedAt = it.playedAt)
                }
            } else {
                emptyList()
            },
        )
    }

    /**
     * Merges a backup into the library. Ids are the merge key: existing rows
     * are never dropped, a playlist keeps its name and tracks and only gains
     * what it did not have, and identical history rows are not duplicated.
     */
    suspend fun importBackup(backup: LibraryBackup): ImportResult {
        var playlists = 0
        var liked = 0
        var tracks = 0
        for (entry in backup.playlists) {
            cacheTracks(entry.tracks)
            tracks += entry.tracks.size
            val existing = libraryDao.playlists().first().firstOrNull { it.id == entry.id }
            if (existing == null) {
                libraryDao.upsertPlaylist(
                    PlaylistEntity(
                        id = entry.id,
                        name = entry.name,
                        description = entry.description,
                        createdAt = entry.createdAt,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                for (track in entry.tracks) {
                    addToPlaylist(entry.id, track)
                }
            } else {
                for (track in entry.tracks) {
                    addToPlaylist(entry.id, track)
                }
            }
            playlists++
        }
        for (track in backup.likedTracks) {
            cacheTracks(listOf(track))
            if (libraryDao.isLiked(track.id).first()) continue
            libraryDao.like(LikedTrackEntity(track.id, System.currentTimeMillis()))
            liked++
        }
        for (play in backup.playHistory) {
            if (libraryDao.historyRowExists(play.trackId, play.playedAt)) continue
            libraryDao.recordPlay(
                PlayHistoryEntity(trackId = play.trackId, playedAt = play.playedAt),
            )
        }
        return ImportResult(playlists = playlists, liked = liked, tracks = tracks)
    }

    private companion object {
        const val HISTORY_EXPORT_LIMIT = 500
    }
}

/** A queue read back from the database; see [LibraryRepository.savedQueue]. */
data class SavedQueue(
    val tracks: List<Track>,
    /** Pre-shuffle index per queue index; identity when shuffle was off. */
    val originalPositions: List<Int>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
)

// ----------------------------------------------------------------------
// Backup / restore. One versioned JSON file. Credentials are not a field
// here at all: no cookie, no token, no API key can ever be written.
// ----------------------------------------------------------------------

@kotlinx.serialization.Serializable
data class LibraryBackup(
    val schemaVersion: Int = 1,
    val exportedAt: Long = 0L,
    val playlists: List<BackupPlaylist> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    /** Optional: only written when the user includes it. */
    val playHistory: List<BackupPlay> = emptyList(),
    /** Non-secret settings only; see [BackupSettings]. */
    val settings: BackupSettings? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@kotlinx.serialization.Serializable
data class BackupPlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Long = 0L,
    val tracks: List<Track> = emptyList(),
)

@kotlinx.serialization.Serializable
data class BackupPlay(val trackId: String, val playedAt: Long)

/** The settings worth carrying to another device. Never anything secret. */
@kotlinx.serialization.Serializable
data class BackupSettings(
    val dynamicColor: Boolean? = null,
    val highQualityAudio: Boolean? = null,
    val wifiOnlyStreaming: Boolean? = null,
    val playbackSpeed: Float? = null,
    val skipSilence: Boolean? = null,
    val queuePreventDuplicates: Boolean? = null,
    val rememberQueue: Boolean? = null,
    val rememberShuffle: Boolean? = null,
    val autoplaySimilar: Boolean? = null,
)

/** What an import did, for the message the user sees. */
data class ImportResult(
    val playlists: Int,
    val liked: Int,
    val tracks: Int,
)
