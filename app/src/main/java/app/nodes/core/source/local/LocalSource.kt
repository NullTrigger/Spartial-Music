/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads audio already on the device through MediaStore. No files are copied or
 * uploaded. When audio permission is missing this source degrades to the sample
 * catalogue so the UI still has something to draw.
 */
@Singleton
class LocalSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : MusicSource {

    override val id: SourceId = SourceId.LOCAL
    override val displayName: String = "Local device"

    override suspend fun availability(): SourceAvailability {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
        return if (granted) {
            SourceAvailability(id, SourceStatus.READY)
        } else {
            SourceAvailability(
                id,
                SourceStatus.NEEDS_PERMISSION,
                "Allow audio access to browse files on this device.",
            )
        }
    }

    override suspend fun search(query: String, limit: Int): SourceResult<List<Track>> {
        if (query.isBlank()) return SourceResult.Success(emptyList())
        val selection = "${MediaStore.Audio.Media.TITLE} LIKE ? OR " +
            "${MediaStore.Audio.Media.ARTIST} LIKE ? OR " +
            "${MediaStore.Audio.Media.ALBUM} LIKE ?"
        val args = arrayOf("%$query%", "%$query%", "%$query%")
        val results = queryTracks(selection, args, limit)
        return SourceResult.Success(if (results.isEmpty()) DemoCatalog.search(query) else results)
    }

    override suspend fun getHome(): SourceResult<HomeFeed> {
        val recent = queryTracks(null, null, 20)
        if (recent.isEmpty()) {
            val availability = availability()
            return if (availability.status == SourceStatus.NEEDS_PERMISSION) {
                SourceResult.Success(DemoCatalog.feed)
            } else {
                SourceResult.Success(DemoCatalog.feed)
            }
        }
        return SourceResult.Success(
            HomeFeed(
                hero = recent.take(6).map { HeroSlide(track = it) },
                continuePlaying = recent.take(8),
                sections = listOf(
                    HomeSection(
                        id = "local-recent",
                        title = "Recently added",
                        tracks = recent.take(10),
                    ),
                    HomeSection(
                        id = "local-all",
                        title = "On this device",
                        subtitle = "${recent.size} tracks",
                        tracks = recent,
                    ),
                ),
            ),
        )
    }

    override suspend fun getTrending(limit: Int): SourceResult<List<Track>> =
        SourceResult.Success(queryTracks(null, null, limit))

    override suspend fun getPlaylist(playlistId: String): SourceResult<PlaylistDetail> {
        // Local "playlists" are the app's own Room playlists; album browsing is
        // handled by filtering on album id here.
        val tracks = queryTracks("${MediaStore.Audio.Media.ALBUM_ID} = ?", arrayOf(playlistId), 200)
        if (tracks.isEmpty()) {
            return SourceResult.Failure(SourceErrorType.NOT_FOUND)
        }
        val first = tracks.first()
        return SourceResult.Success(
            PlaylistDetail(
                playlist = Playlist(
                    id = playlistId,
                    title = first.album ?: "Local album",
                    artworkUri = first.artworkUri,
                    trackCount = tracks.size,
                    sourceId = SourceId.LOCAL,
                ),
                tracks = tracks,
            ),
        )
    }

    override suspend fun play(track: Track): SourceResult<PlaybackHandle> {
        val uri = track.mediaUri
        return if (uri.isNullOrBlank()) {
            SourceResult.Success(
                PlaybackHandle.Unavailable(
                    "This is placeholder metadata. Add local audio or configure a source in Settings.",
                ),
            )
        } else {
            SourceResult.Success(PlaybackHandle.Stream(uri))
        }
    }

    private suspend fun queryTracks(
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int,
    ): List<Track> = withContext(Dispatchers.IO) {
        if (!hasAudioPermission()) return@withContext emptyList()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )

        val tracks = mutableListOf<Track>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext() && tracks.size < limit) {
                    val mediaId = cursor.getLong(idColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    tracks += Track(
                        id = "local-$mediaId",
                        title = cursor.getString(titleColumn) ?: "Unknown title",
                        artist = cursor.getString(artistColumn) ?: "Unknown artist",
                        album = cursor.getString(albumColumn),
                        artworkUri = albumArtUri(albumId)?.toString(),
                        mediaUri = ContentUris.withAppendedId(collection, mediaId).toString(),
                        durationMs = cursor.getLong(durationColumn),
                        sourceId = SourceId.LOCAL,
                    )
                }
            }
        }
        tracks
    }

    private fun albumArtUri(albumId: Long): Uri? {
        if (albumId <= 0) return null
        return ContentUris.withAppendedId(ALBUM_ART_URI, albumId)
    }

    private fun hasAudioPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    private companion object {
        val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
