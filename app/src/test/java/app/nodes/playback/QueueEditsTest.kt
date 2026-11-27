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
import org.junit.Assert.assertNotEquals
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

    @Test
    fun `queue suggestions echo the queue and drop unlinked tracks`() {
        val playing = Track(id = "playing", title = "Playing", artist = "Chikku", album = "One")
        val sameAlbum = Track(id = "same-album", title = "Same album", artist = "Chikku", album = "One")
        val sameArtist = Track(id = "same-artist", title = "Same artist", artist = "Chikku", album = "Two")
        val stranger = Track(id = "cached-search-result", title = "Unrelated", artist = "Someone", album = "Else")

        val picked = QueueEdits.echo(
            queue = listOf(playing),
            candidates = listOf(stranger, sameArtist, sameAlbum, playing, sameAlbum),
            limit = 8,
        )

        // Same album first, then the same artist. The unrelated cached track,
        // the track already queued and the duplicate never come back.
        assertEquals(listOf("same-album", "same-artist"), picked.map { it.id })

        // A queue with nothing related appends nothing rather than filling up.
        assertEquals(
            emptyList<String>(),
            QueueEdits.echo(queue = listOf(playing), candidates = listOf(stranger), limit = 8).map { it.id },
        )
    }

    @Test
    fun `song names ignore pressings and suffixes`() {
        assertEquals(QueueEdits.titleKey(track("Fa Fa Fa")), QueueEdits.titleKey(track("Fa Fa Fa (Live)")))
        assertEquals(QueueEdits.titleKey(track("Fa Fa Fa")), QueueEdits.titleKey(track("FA FA FA [Remix]")))
        assertEquals(QueueEdits.titleKey(track("Fa Fa Fa")), QueueEdits.titleKey(track("Fa-fa-fa - Remastered 2011")))
        assertNotEquals(QueueEdits.titleKey(track("Fa Fa Funk Style")), QueueEdits.titleKey(track("Fa Fa Fa")))
    }

    @Test
    fun `auto-filled additions never repeat a song name`() {
        val taken = setOf(QueueEdits.titleKey(track("Fa Fa Funk Style")))
        val pressings = listOf(
            track("Fa Fa Fa (Live)"),
            track("FA FA FA"),
            track("Fa-fa-fa [Remix]"),
            track("Night Drive"),
            track("Fa Fa Fa (Instrumental)"),
        )

        val out = QueueEdits.varyByName(pressings, taken, 4)

        // One pressing of "fa fa fa" only; the freed slots go to variety.
        assertEquals(listOf("Fa Fa Fa (Live)", "Night Drive"), out.map { it.title })
    }

    // ------------------------------------------------------------------
    // Mood shaping
    // ------------------------------------------------------------------

    /** A track that names its language outright, under its own artist. */
    private fun named(id: String, language: String) = Track(
        id = id,
        title = "$id ($language Song)",
        artist = "Singer $id",
    )

    @Test
    fun `the infinite queue grows in its own language and keeps a small variety share`() {
        val mood = MoodProfile.of(seed = named("seed", "Malayalam"), recent = emptyList())
        val candidates = listOf(
            named("m1", "Malayalam"),
            named("b1", "Bhojpuri"),
            named("m2", "Malayalam"),
            named("m3", "Malayalam"),
            named("b2", "Bhojpuri"),
            named("m4", "Malayalam"),
            named("m5", "Malayalam"),
            named("b3", "Bhojpuri"),
            named("m6", "Malayalam"),
            named("m7", "Malayalam"),
            named("b4", "Bhojpuri"),
            named("m8", "Malayalam"),
            named("m9", "Malayalam"),
            named("b5", "Bhojpuri"),
            named("m10", "Malayalam"),
        )

        val out = QueueEdits.shapeByMood(candidates, mood, limit = 10)

        // Malayalam leads and Bhojpuri is one batch in ten, not half the queue.
        assertEquals(10, out.size)
        assertEquals(1, out.count { "Bhojpuri" in it.title })
        assertEquals(9, out.count { "Malayalam" in it.title })
        // The same-mood tracks keep the order the sources preferred them in.
        assertEquals(
            listOf("m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9"),
            out.filter { "Malayalam" in it.title }.map { it.id },
        )
    }

    @Test
    fun `unstated-language tracks fill in before named strangers`() {
        val mood = MoodProfile.of(seed = named("seed", "Malayalam"), recent = emptyList())
        val candidates = listOf(
            named("b1", "Bhojpuri"),
            // Latin-script, no language named: could well be romanised Malayalam.
            Track(id = "romanised-hit", title = "Kammaniye", artist = "Singer romanised"),
            named("b2", "Bhojpuri"),
        )

        val out = QueueEdits.shapeByMood(candidates, mood, limit = 3)

        // The Latin-script track gets the benefit of the doubt before anything
        // that positively names another language.
        assertEquals(listOf("romanised-hit", "b1", "b2"), out.map { it.id })
    }

    @Test
    fun `a pure mood pool still fills the queue when it runs dry`() {
        val mood = MoodProfile.of(seed = named("seed", "Malayalam"), recent = emptyList())
        val onlyStrangers = listOf(named("b1", "Bhojpuri"), named("b2", "Bhojpuri"))

        // A full queue beats a pure one: nothing is invented and nothing is starved.
        assertEquals(
            listOf("b1", "b2"),
            QueueEdits.shapeByMood(onlyStrangers, mood, limit = 5).map { it.id },
        )
        assertEquals(
            emptyList<String>(),
            QueueEdits.shapeByMood(emptyList(), mood, limit = 5).map { it.id },
        )
    }
}
