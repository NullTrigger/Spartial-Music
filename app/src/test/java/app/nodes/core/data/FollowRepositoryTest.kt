/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import app.nodes.core.data.db.FollowedArtistDao
import app.nodes.core.data.db.FollowedArtistEntity
import app.nodes.core.model.SourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Follows against a fake DAO: the local-first rules, no Room involved. */
class FollowRepositoryTest {

    private class FakeDao : FollowedArtistDao {
        private val rows = LinkedHashMap<Pair<String, String>, FollowedArtistEntity>()
        private val flow = MutableStateFlow<List<FollowedArtistEntity>>(emptyList())

        private fun publish() {
            flow.value = rows.values.sortedByDescending { it.followedAt }
        }

        override suspend fun insert(row: FollowedArtistEntity): Long {
            if (rows.containsKey(row.artistId to row.sourceId)) return -1L
            rows[row.artistId to row.sourceId] = row
            publish()
            return 1L
        }

        override fun isFollowing(artistId: String, sourceId: String): Flow<Boolean> =
            flow.mapRows(artistId, sourceId)

        override suspend fun exists(artistId: String, sourceId: String): Boolean =
            rows.containsKey(artistId to sourceId)

        override fun observeAll(): Flow<List<FollowedArtistEntity>> = flow

        override suspend fun mostRecent(limit: Int): List<FollowedArtistEntity> =
            rows.values.sortedByDescending { it.followedAt }.take(limit).toList()

        override suspend fun delete(artistId: String, sourceId: String) {
            rows.remove(artistId to sourceId)
            publish()
        }

        private fun Flow<List<FollowedArtistEntity>>.mapRows(
            artistId: String,
            sourceId: String,
        ): Flow<Boolean> = map { rows ->
            rows.any { it.artistId == artistId && it.sourceId == sourceId }
        }
    }

    @Test
    fun `follow then unfollow then follow again`() = runTest {
        val dao = FakeDao()
        val repo = FollowRepository(dao)
        assertTrue(repo.follow("artist-neon", SourceId.YOUTUBE_MUSIC, "Neon Skyline", null))
        assertTrue(repo.follow("artist-signal", SourceId.LOCAL, "Signal Bloom", null))
        assertEquals(2, repo.mostRecent(10).size)
        repo.unfollow("artist-neon", SourceId.YOUTUBE_MUSIC)
        assertEquals(1, repo.mostRecent(10).size)
        assertTrue(repo.follow("artist-neon", SourceId.YOUTUBE_MUSIC, "Neon Skyline", null))
    }

    @Test
    fun `duplicate follow is rejected without touching the row`() = runTest {
        val dao = FakeDao()
        val repo = FollowRepository(dao)
        assertTrue(repo.follow("artist-neon", SourceId.YOUTUBE_MUSIC, "Neon Skyline", null))
        assertFalse(repo.follow("artist-neon", SourceId.YOUTUBE_MUSIC, "Neon Skyline", null))
        assertEquals(1, repo.mostRecent(10).size)
    }

    @Test
    fun `artist id is the normalized name`() {
        assertEquals("ac-dc", FollowRepository.artistIdFor("AC/DC "))
        assertEquals("neon-skyline", FollowRepository.artistIdFor("Neon Skyline"))
    }
}
