/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import app.nodes.core.data.db.FollowedArtistDao
import app.nodes.core.data.db.FollowedArtistEntity
import app.nodes.core.model.SourceId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * An artist the user follows. Local-first: a follow is a row in this app's
 * database and never a request to a provider. YouTube subscription import or
 * export is not implemented (Planned; see REVIEW_NOTES).
 */
data class FollowedArtist(
    val artistId: String,
    val sourceId: SourceId,
    val name: String,
    val imageUrl: String?,
    val followedAt: Long,
)

@Singleton
class FollowRepository @Inject constructor(
    private val followDao: FollowedArtistDao,
) {
    /** True when this call actually created the follow; false if it existed. */
    suspend fun follow(artistId: String, sourceId: SourceId, name: String, imageUrl: String?): Boolean {
        if (followDao.exists(artistId, sourceId.key)) return false
        followDao.insert(
            FollowedArtistEntity(
                artistId = artistId,
                sourceId = sourceId.key,
                name = name,
                imageUrl = imageUrl,
                followedAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    suspend fun unfollow(artistId: String, sourceId: SourceId) {
        followDao.delete(artistId, sourceId.key)
    }

    fun isFollowing(artistId: String, sourceId: SourceId): Flow<Boolean> =
        followDao.isFollowing(artistId, sourceId.key)

    fun observeFollowedArtists(): Flow<List<FollowedArtist>> =
        followDao.observeAll().mapRows()

    /** The [limit] most recently followed artists, newest first. */
    suspend fun mostRecent(limit: Int): List<FollowedArtist> =
        followDao.mostRecent(limit).mapRows()

    private fun List<FollowedArtistEntity>.mapRows() = map { row ->
        FollowedArtist(
            artistId = row.artistId,
            sourceId = SourceId.entries.firstOrNull { it.key == row.sourceId } ?: SourceId.LOCAL,
            name = row.name,
            imageUrl = row.imageUrl,
            followedAt = row.followedAt,
        )
    }

    private fun Flow<List<FollowedArtistEntity>>.mapRows(): Flow<List<FollowedArtist>> =
        map { rows -> rows.mapRows() }

    companion object {
        /**
         * The id an artist page keys a follow by. The app's artist pages are
         * name-keyed (Room and InnerTube both address artists by display
         * name), so the name is the identity; the source separates two
         * providers that happen to share a spelling.
         */
        fun artistIdFor(name: String): String =
            name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").ifBlank { "artist" }
    }
}
