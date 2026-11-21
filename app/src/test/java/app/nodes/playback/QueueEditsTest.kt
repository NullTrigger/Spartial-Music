/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import app.nodes.core.data.BackupPlay
import app.nodes.core.data.BackupPlaylist
import app.nodes.core.data.BackupSettings
import app.nodes.core.data.LibraryBackup
import app.nodes.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Pure queue logic: persistence round-trip, shuffle without repeats, and the
 * infinite queue's selection order. No Media3 session, no Android.
 */
class QueueEditsTest {

    private fun track(id: String) = Track(id = id, title = id, artist = "Artist")

    @Test
    fun `queue serialization round-trips including the unshuffled order`() {
        val original = listOf("a", "b", "c", "d")
        val playedOrder = listOf("c", "a", "d", "b")
        val snapshot = QueueEdits.Snapshot(
            trackIds = playedOrder,
            originalPositions = playedOrder.map { original.indexOf(it) },
            currentIndex = 2,
            positionMs = 12_345L,
            shuffleEnabled = true,
        )

        val (items, state) = QueueEdits.toRows(snapshot)
        val back = QueueEdits.fromRows(items, state)

        assertEquals(snapshot.trackIds, back.trackIds)
        // The unshuffled order survives: sorting by originalPosition rebuilds
        // the exact list the user chose.
        assertEquals(
            original,
            back.trackIds
                .zip(back.originalPositions)
                .sortedBy { it.second }
                .map { it.first },
        )
        assertEquals(snapshot.currentIndex, back.currentIndex)
        assertEquals(snapshot.positionMs, back.positionMs)
        assertEquals(snapshot.shuffleEnabled, back.shuffleEnabled)
    }

    @Test
    fun `shuffle order never repeats before the list is exhausted`() {
        val ids = (1..10).map { "t$it" }
        repeat(25) { seed ->
            val order = QueueEdits.shuffledOrder(ids, Random(seed))
            assertEquals(ids.size, order.size)
            assertEquals(ids.toSet(), order.toSet())
            // A full pass through the order plays every track exactly once.
            assertEquals(order.size, order.distinct().size)
        }
    }

    @Test
    fun `backup round-trips and no credential field can ever be written`() {
        val backup = LibraryBackup(
            schemaVersion = LibraryBackup.SCHEMA_VERSION,
            exportedAt = 42L,
            playlists = listOf(
                BackupPlaylist(
                    id = "p1",
                    name = "Mix",
                    createdAt = 7L,
                    tracks = listOf(track("ytmusic-x")),
                ),
            ),
            likedTracks = listOf(track("local-y")),
            playHistory = listOf(BackupPlay(trackId = "ytmusic-x", playedAt = 9L)),
            settings = BackupSettings(playbackSpeed = 1.25f, rememberQueue = true),
        )

        val json = kotlinx.serialization.json.Json {
            encodeDefaults = true
        }.encodeToString(LibraryBackup.serializer(), backup)
        val back = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
        }.decodeFromString(LibraryBackup.serializer(), json)

        assertEquals(backup, back)
        // No field anywhere in the backup model is named like a credential, so
        // no cookie, token or key can end up in an exported file.
        val forbidden = listOf("cookie", "token", "secret", "key", "password", "auth")
        val fieldNames = buildList {
            fun scan(type: kotlinx.serialization.descriptors.SerialDescriptor) {
                for (i in 0 until type.elementsCount) {
                    add(type.getElementName(i).lowercase())
                    scan(type.getElementDescriptor(i))
                }
            }
            scan(LibraryBackup.serializer().descriptor)
        }
        for (name in fieldNames) {
            assertTrue(
                "Field '$name' looks like a credential and must not be exported",
                forbidden.none { name.contains(it) },
            )
        }
    }

    @Test
    fun `infinite queue picks related first, then local, minus queued and recent`() {
        val related = listOf(track("r1"), track("r2"), track("local-a"))
        val pool = listOf(track("local-a"), track("local-b"), track("local-c"), track("r1"))

        val picked = QueueEdits.selectExtension(
            related = related,
            localPool = pool,
            queuedIds = setOf("r2", "already-queued"),
            recentlyPlayedIds = setOf("local-a", "played-50-back"),
            limit = 10,
        )

        // Related first (minus queued/recent), then the local pool, deduped.
        assertEquals(listOf("r1", "local-b", "local-c"), picked.map { it.id })
        // Nothing queued or recently played comes back, ever.
        assertTrue(picked.none { it.id in setOf("r2", "already-queued", "local-a", "played-50-back") })

        // All steps dry: nothing to add.
        assertTrue(
            QueueEdits.selectExtension(
                related = emptyList(),
                localPool = emptyList(),
                queuedIds = setOf("x"),
                recentlyPlayedIds = emptySet(),
                limit = 5,
            ).isEmpty(),
        )
    }
}
