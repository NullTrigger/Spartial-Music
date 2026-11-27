/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.nodes.core.data.db.SpartialDatabase
import app.nodes.core.model.AddToPlaylistResult
import app.nodes.core.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Add-to-playlist behaviour against a real in-memory Room database: the
 * result reporting, the duplicate rule, and the order new tracks land in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddToPlaylistTest {

    private lateinit var db: SpartialDatabase
    private lateinit var repository: LibraryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SpartialDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LibraryRepository(db.trackDao(), db.libraryDao(), db.queueDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun track(id: String) = Track(
        id = id,
        title = "Track $id",
        artist = "Placeholder Trio",
        videoId = "video-$id",
    )

    @Test
    fun `add reports added and stores the row`() = runBlocking {
        val playlist = repository.createPlaylist("Test")

        val result = repository.addToPlaylist(playlist.id, track("other:1"))

        assertEquals(AddToPlaylistResult.Added, result)
        val rows = db.libraryDao().playlistTrackRows(playlist.id)
        assertEquals(listOf("other:1"), rows.map { it.trackId })
    }

    @Test
    fun `duplicate reports already exists and keeps a single row`() = runBlocking {
        val playlist = repository.createPlaylist("Test")
        repository.addToPlaylist(playlist.id, track("other:1"))

        val second = repository.addToPlaylist(playlist.id, track("other:1"))

        assertEquals(AddToPlaylistResult.AlreadyExists, second)
        val rows = db.libraryDao().playlistTrackRows(playlist.id)
        assertEquals(1, rows.size)
    }

    @Test
    fun `new tracks land after the existing ones`() = runBlocking {
        val playlist = repository.createPlaylist("Test")

        repository.addToPlaylist(playlist.id, track("other:1"))
        repository.addToPlaylist(playlist.id, track("other:2"))
        repository.addToPlaylist(playlist.id, track("other:3"))

        val rows = db.libraryDao().playlistTrackRows(playlist.id)
        assertEquals(listOf("other:1", "other:2", "other:3"), rows.map { it.trackId })
        assertEquals(listOf(0, 1, 2), rows.map { it.sortOrder })
    }
}
