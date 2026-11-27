/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.sync

import app.nodes.core.data.LibraryRepository
import app.nodes.core.data.SettingsRepository
import app.nodes.core.model.Track
import app.nodes.core.source.youtubemusic.InnerTubeAuth
import app.nodes.core.source.youtubemusic.YouTubeMusicSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Glossy's "Auto-sync with account", on YouTube Music.
 *
 * While the setting is on and an account is signed in, this keeps three things
 * in step without the user asking, at most once every [COOLDOWN_MS]:
 *
 *  - **Likes** - likes made since the last pass are pushed onto the account
 *    (the first pass sends every like, exactly what the old "Sync to YT"
 *    button did), and the account's own liked songs are added to the local
 *    library, so a like made on the phone shows up here and a like made here
 *    shows up there.
 *  - **Copied playlists** - every local playlist that was ever copied to
 *    YouTube is refreshed in place: tracks added since the copy are appended
 *    to the same remote playlist instead of creating a duplicate.
 *  - **History** - a play reported by [YouTubeMusicSource.play] is what writes
 *    the entry; anything played while the setting was on is in the account's
 *    history when the next sync runs.
 *
 * Unlike Glossy this is off by default: every part of it writes to a real
 * account, and an install that never asked for that must not start doing it.
 * It is a best-effort loop, not a guarantee: a sync that fails changes nothing
 * locally and is simply tried again next time. Nothing is read back from
 * history, and nothing is ever unliked locally - a like removed on YouTube
 * stays liked here, because the local library is the user's own record.
 */
@Singleton
class YouTubeSync @Inject constructor(
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    private val auth: InnerTubeAuth,
    private val source: YouTubeMusicSource,
    private val playlistLinks: YouTubePlaylistLinks,
) {

    /** What one sync managed, for the settings screen to report. */
    data class Summary(
        val likesPushed: Int = 0,
        val likesFailed: Int = 0,
        val likesPulled: Int = 0,
        val playlistsUpdated: Int = 0,
        val playlistsFailed: Int = 0,
    ) {
        val didAnything: Boolean
            get() = likesPushed > 0 || likesPulled > 0 || playlistsUpdated > 0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mutex = Mutex()

    private val started = AtomicBoolean(false)

    private val _running = MutableStateFlow(false)

    /** True while a sync is in flight, automatic or manual. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /**
     * The sync that just finished, as an event rather than state: re-opening
     * Settings must not replay an old "0 likes sent" banner.
     */
    private val _summary = MutableSharedFlow<Summary>(extraBufferCapacity = 1)

    val summary: SharedFlow<Summary> = _summary.asSharedFlow()

    /**
     * Starts the background loop once. Called from [app.nodes.SpartialApplication],
     * so it runs for the life of the process rather than a screen's.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            while (isActive) {
                runCatching { syncIfDue() }
                delay(TICK_MS)
            }
        }
    }

    /** Runs a sync now, cooldown or not (the settings button). */
    fun syncNow() {
        scope.launch { runCatching { perform() } }
    }

    private suspend fun syncIfDue() {
        val current = settings.current()
        if (!current.syncYouTube || !auth.isSignedIn) return
        val now = System.currentTimeMillis()
        // A clock that jumped backwards count as due, not as never.
        val elapsed = now - current.lastYouTubeSyncAt
        if (current.lastYouTubeSyncAt > 0L && elapsed in 0 until COOLDOWN_MS) return
        perform()
    }

    private suspend fun perform() {
        if (!auth.isSignedIn) return
        if (!mutex.tryLock()) return
        try {
            _running.value = true
            var summary = Summary()

            // 1. This device's likes onto the account. Replaying a like that
            //    already exists is a no-op server-side, which is what makes
            //    the whole loop safe to repeat.
            //
            //    Only what changed since the last run is sent: pushing every
            //    like every half hour would be hundreds of calls for a no-op.
            //    The first run after the switch goes on sends everything, which
            //    is what the old "Sync to YT" button did.
            val lastRun = settings.current().lastYouTubeSyncAt
            val liked = library.likedTracks.first()
            val changed = if (lastRun == 0L) {
                liked
            } else {
                val cutoff = lastRun - RETRY_WINDOW_MS
                val recent = library.recentLikes(PUSH_LIMIT)
                    .filter { it.likedAt >= cutoff }
                    .mapTo(HashSet()) { it.trackId }
                liked.filter { it.id in recent }
            }
            val likedIds = changed.mapNotNull { it.videoId }.distinct()
            if (likedIds.isNotEmpty()) {
                val outcome = source.pushLikes(likedIds)
                summary = summary.copy(
                    likesPushed = outcome.pushed,
                    likesFailed = outcome.failed,
                )
            }

            // 2. The account's likes into this library. Add-only: an unlike
            //    made on YouTube does not remove a local like.
            val remote = runCatching { source.likedSongs() }.getOrDefault(emptyList<Track>())
            if (remote.isNotEmpty()) {
                runCatching { library.cacheTracks(remote) }
                val likedNow = library.likedTracks.first().map { it.id }.toSet()
                var pulled = 0
                for (track in remote) {
                    if (track.videoId == null || track.id in likedNow) continue
                    runCatching { library.setLiked(track, true) }.onSuccess { pulled++ }
                }
                summary = summary.copy(likesPulled = pulled)
            }

            // 3. Copied playlists, refreshed in place.
            var updated = 0
            var failed = 0
            val links = runCatching { playlistLinks.load() }
                .getOrDefault(emptyList<YouTubePlaylistLink>())
            for (link in links) {
                val detail = runCatching { library.playlistDetail(link.localPlaylistId) }.getOrNull()
                if (detail == null) {
                    // The local playlist was deleted; the remote copy is the
                    // user's to keep or remove, so only the link goes.
                    runCatching { playlistLinks.forget(link.localPlaylistId) }
                    continue
                }
                val outcome = runCatching {
                    source.pushPlaylist(detail.playlist.title, detail.tracks, link.localPlaylistId)
                }.getOrDefault(YouTubeMusicSource.PlaylistPush.Failed)
                if (outcome is YouTubeMusicSource.PlaylistPush.Done) updated++ else failed++
            }

            _summary.tryEmit(
                summary.copy(
                    playlistsUpdated = updated,
                    playlistsFailed = failed,
                ),
            )
            settings.setLastYouTubeSyncAt(System.currentTimeMillis())
        } finally {
            _running.value = false
            mutex.unlock()
        }
    }

    private companion object {
        /** Glossy's cooldown: often enough to feel live, rare enough to be free. */
        const val COOLDOWN_MS = 30L * 60_000L

        /** How often the loop looks at the clock, not how often it syncs. */
        const val TICK_MS = 10L * 60_000L

        /**
         * How far back a like is re-sent. Overlapping the cooldown means a like
         * whose push failed last time is retried on the next pass, without
         * turning the pass into a full replay.
         */
        const val RETRY_WINDOW_MS = 60L * 60_000L

        /** A ceiling on the retry pass, so a burst of likes stays one batch. */
        const val PUSH_LIMIT = 100
    }
}
