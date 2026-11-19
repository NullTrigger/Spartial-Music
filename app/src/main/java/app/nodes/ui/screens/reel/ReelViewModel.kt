/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.reel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import app.nodes.core.cover.AnimatedCover
import app.nodes.core.cover.CoverResolver
import app.nodes.core.cover.UserLoopStore
import app.nodes.core.data.FollowRepository
import app.nodes.core.data.LibraryRepository
import app.nodes.core.data.SettingsRepository
import app.nodes.core.data.db.PendingLikeDao
import app.nodes.core.data.db.PendingLikeEntity
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.SourceRegistry
import app.nodes.playback.PlaybackController
import app.nodes.playback.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReelTab { FOR_YOU, FOLLOWING }

data class ReelUiState(
    val tab: ReelTab = ReelTab.FOR_YOU,
    val cover: AnimatedCover? = null,
    /** Animated covers allowed by settings and network right now. */
    val allowAnimated: Boolean = true,
    val savedInPlaylist: Boolean = false,
    val followingEmpty: Boolean = false,
    /** One-shot line for the like-sync warning; the screen clears it. */
    val notice: String? = null,
)

/**
 * The Reel's brain. The feed IS the playback queue: the pager indexes
 * [PlaybackController.state]'s queue, a settled page plays that queue index,
 * and both tabs share one rule - when two or fewer upcoming items remain, a
 * background batch is appended without ever pausing playback.
 */
@HiltViewModel
class ReelViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val player: PlaybackController,
    private val library: LibraryRepository,
    private val follows: FollowRepository,
    private val settings: SettingsRepository,
    private val registry: SourceRegistry,
    private val covers: CoverResolver,
    private val userLoops: UserLoopStore,
    private val pendingLikes: PendingLikeDao,
) : ViewModel() {

    val playerState: StateFlow<PlayerUiState> = player.state

    private val _state = MutableStateFlow(ReelUiState())
    val state: StateFlow<ReelUiState> = _state.asStateFlow()

    val likedIds: StateFlow<Set<String>> = library.likedTracks
        .map { tracks -> tracks.mapTo(HashSet()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Tracks whose cover video failed this session: no retry loop. */
    private val coverFailures = HashSet<String>()

    private var extending = false

    init {
        viewModelScope.launch {
            val current = settings.current()
            _state.update { it.copy(allowAnimated = current.animatedCovers) }
        }
        retryPendingLikes()
    }

    // ------------------------------------------------------------------ pages

    /** The pager settled on [index]: make the player agree. */
    fun onPageSettled(index: Int) {
        val queue = playerState.value.queue
        if (index in queue.indices && index != playerState.value.queueIndex) {
            player.playQueueIndex(index)
        }
        refreshForCurrent()
    }

    fun togglePlayPause() = player.togglePlayPause()

    fun skipNext() = player.next()

    fun skipPrevious() = player.previous()

    private fun refreshForCurrent() {
        val current = playerState.value.current ?: return
        viewModelScope.launch {
            val settingsNow = settings.current()
            var cover = if (current.id in coverFailures) {
                null
            } else {
                runCatching { covers.find(current) }.getOrNull()
            }
            // Remote loops wait for Wi-Fi unless the user allowed mobile data.
            if (cover != null && !cover.isLocalFile &&
                settingsNow.animatedCoversOnMobile == false && isNetworkMetered()
            ) {
                cover = null
            }
            val savedId = settingsNow.savedPlaylistId
            val inSaved = savedId != null && runCatching {
                library.playlistDetail(savedId)?.tracks?.any { it.id == current.id } == true
            }.getOrDefault(false)
            _state.update {
                it.copy(cover = cover, savedInPlaylist = inSaved, allowAnimated = settingsNow.animatedCovers)
            }
        }
        // Two or fewer upcoming: fetch the next batch now, in the background.
        maybeExtend()
    }

    private fun isNetworkMetered(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    // ------------------------------------------------------------------- feed

    /**
     * Append a batch when the queue runs low. Selection order: related
     * tracks from the active source, then the local candidate pool
     * (recents and likes), then a shuffle of the whole library. Never blocks
     * playback; never duplicates an id that is queued or just played.
     */
    private fun maybeExtend() {
        if (extending) return
        val state = playerState.value
        val upcoming = state.queue.size - state.queueIndex - 1
        if (state.queue.isEmpty() || upcoming > 2) return
        extending = true
        viewModelScope.launch {
            try {
                val batch = nextBatch(state)
                batch.forEach(player::addToQueue)
            } finally {
                extending = false
            }
        }
    }

    private suspend fun nextBatch(state: PlayerUiState): List<Track> {
        val current = state.current ?: return emptyList()
        val exclude = HashSet(state.queue.map { it.id })
        exclude += library.recentlyPlayedIds(RECENT_EXCLUDE)
        val candidates = ArrayList<Track>()

        if (_state.value.tab == ReelTab.FOLLOWING) {
            candidates += followingCandidates(exclude, BATCH)
        }
        if (candidates.isEmpty()) {
            // 1. related tracks from the track's own source.
            val source = registry.byId(current.sourceId)
            if (source != null) {
                val related = runCatching {
                    (source.getRelated(current, BATCH * 2) as? SourceResult.Success)?.data
                }.getOrNull().orEmpty()
                candidates += related
            }
        }
        if (candidates.isEmpty()) {
            // 2. the local candidate pool: recents and likes.
            candidates += library.recentlyPlayed.first()
            candidates += library.likedTracks.first()
            // 3. last resort: a shuffle of the whole library.
            candidates += library.allTracks.first().shuffled()
        }
        return ReelFeed.batch(candidates, exclude, BATCH)
    }

    /** One cached track list per followed artist, newest follow first. */
    private suspend fun followingPools(): List<List<Track>> {
        val artists = follows.mostRecent(FOLLOWING_ARTISTS)
        if (artists.isEmpty()) return emptyList()
        return artists.map { followed ->
            runCatching { library.artistTracks(followed.name).first() }.getOrDefault(emptyList())
        }
    }

    /** More songs from the followed artists, for the Following refill. */
    private suspend fun followingCandidates(exclude: Set<String>, limit: Int): List<Track> =
        ReelFeed.interleaveByArtist(followingPools(), exclude, limit)

    /** Switch the pager between For you (the live queue) and Following. */
    fun selectTab(tab: ReelTab) {
        if (tab == _state.value.tab) return
        _state.update { it.copy(tab = tab) }
        if (tab == ReelTab.FOLLOWING) {
            viewModelScope.launch { startFollowingFeed() }
        }
    }

    /**
     * Build the Following feed: the 10 most recently followed artists, each
     * loaded with the same Room loader the artist page uses, interleaved so
     * no artist floods the pager, minus the queue and the last played.
     */
    private suspend fun startFollowingFeed() {
        val artists = follows.mostRecent(FOLLOWING_ARTISTS)
        if (artists.isEmpty()) {
            _state.update { it.copy(followingEmpty = true) }
            return
        }
        _state.update { it.copy(followingEmpty = false) }
        // Every followed artist's song may already be queued or just played.
        var feed = followingCandidates(
            playerState.value.queue.mapTo(HashSet()) { it.id } +
                library.recentlyPlayedIds(RECENT_EXCLUDE),
            FOLLOWING_FEED_SIZE,
        )
        if (feed.isEmpty()) {
            // Rebuild without the exclusion so the tab still has content; the
            // refill step dedupes against the live queue anyway.
            feed = ReelFeed.interleaveByArtist(followingPools(), emptySet(), FOLLOWING_FEED_SIZE)
        }
        if (feed.isEmpty()) {
            // Followed artists whose music the device has never seen.
            _state.update { it.copy(followingEmpty = true) }
            return
        }
        player.playNow(feed.first(), feed)
    }

    /**
     * Nothing is playing when the Reel opens: seed the queue from the local
     * pool so the pager always has a first page. Related tracks take over
     * from the first extension.
     */
    fun seedFeed() {
        viewModelScope.launch {
            if (playerState.value.queue.isNotEmpty()) return@launch
            val pool = library.recentlyPlayed.first() +
                library.likedTracks.first() +
                library.allTracks.first().shuffled()
            val feed = ReelFeed.batch(pool, emptySet(), BATCH)
            if (feed.isNotEmpty()) player.playNow(feed.first(), feed)
        }
    }

    /** Opening the screen: make sure there is a first page, then sync. */
    fun onScreenShown() {
        if (playerState.value.queue.isEmpty()) {
            seedFeed()
        } else {
            refreshForCurrent()
        }
        // The other retry moment for pending likes is the next app start.
        retryPendingLikes()
    }

    // ------------------------------------------------------------------ likes

    /**
     * Optimistic like: the local write happens first and always sticks. For
     * a signed-in user's YouTube Music track the like is then pushed with
     * the existing replay mechanism; if the push fails the like stays local,
     * the track is remembered as pending, and the screen shows one quiet
     * line. Unlikes stay local: no unlike request is verified (REVIEW_NOTES).
     */
    fun toggleLike(track: Track) {
        viewModelScope.launch {
            val nowLiked = !likedIds.value.contains(track.id)
            library.setLiked(track, nowLiked)
            val current = settings.current()
            when (likeAction(current.youtubeSignedIn, track.sourceId, nowLiked)) {
                LikeAction.LOCAL_ONLY -> Unit

                LikeAction.LOCAL_ONLY_UNLIKE -> Unit

                LikeAction.LOCAL_AND_PUSH -> {
                    val videoId = track.videoId ?: return@launch
                    val source = registry.byId(SourceId.YOUTUBE_MUSIC)
                        as? app.nodes.core.source.youtubemusic.YouTubeMusicSource
                        ?: return@launch
                    val ok = runCatching {
                        source.pushLikes(listOf(videoId)).failed == 0
                    }.getOrDefault(false)
                    if (!ok) {
                        pendingLikes.add(
                            PendingLikeEntity(
                                trackId = track.id,
                                videoId = videoId,
                                attempts = 1,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                        _state.update { it.copy(notice = NOTICE_LIKE_UNSYNCED) }
                    }
                }
            }
        }
    }

    /**
     * Replay likes that failed before: on open (here) and on the next open
     * after a reconnect. Capped attempts, oldest first, and a track that
     * succeeded is dropped from the table.
     */
    fun retryPendingLikes() {
        viewModelScope.launch {
            val rows = pendingLikes.oldest(PENDING_RETRY_BATCH)
            if (rows.isEmpty()) return@launch
            val current = settings.current()
            if (!current.youtubeSignedIn) return@launch
            val source = registry.byId(SourceId.YOUTUBE_MUSIC)
                as? app.nodes.core.source.youtubemusic.YouTubeMusicSource
                ?: return@launch
            for (row in rows) {
                if (row.attempts >= PENDING_ATTEMPT_CAP) {
                    pendingLikes.remove(row.trackId)
                    continue
                }
                val ok = runCatching {
                    source.pushLikes(listOf(row.videoId)).failed == 0
                }.getOrDefault(false)
                if (ok) pendingLikes.remove(row.trackId) else pendingLikes.bumpAttempts(row.trackId, row.attempts + 1)
            }
            pendingLikes.dropExhausted(PENDING_ATTEMPT_CAP)
        }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    // ------------------------------------------------------------------ saved

    /**
     * Toggle membership in the built-in "Saved" playlist, created (and
     * pinned once) on first use. The id lives in the settings DataStore, so
     * this costs no schema change.
     */
    fun toggleSaved(track: Track) {
        viewModelScope.launch {
            val current = settings.current()
            val id = current.savedPlaylistId
                ?: library.createPlaylist(SAVED_PLAYLIST_SEED).id.also { newId ->
                    settings.setSavedPlaylistId(newId)
                    // Show it in the Library Pins row from the start.
                    runCatching { library.setPinned(newId, true) }
                }
            val detail = runCatching { library.playlistDetail(id) }.getOrNull()
            val inSaved = detail?.tracks?.any { it.id == track.id } == true
            if (inSaved) {
                library.removeFromPlaylist(id, track.id)
            } else {
                library.addToPlaylist(id, track)
            }
            _state.update { it.copy(savedInPlaylist = !inSaved) }
        }
    }

    // ------------------------------------------------------------------ cover

    /** The user picked a loop file for [track]: adopt it immediately. */
    fun setCoverFile(track: Track, uri: Uri) {
        viewModelScope.launch {
            if (userLoops.setCover(track.id, uri)) {
                covers.invalidate(track.id)
                coverFailures.remove(track.id)
                _state.update {
                    it.copy(cover = runCatching { covers.find(track) }.getOrNull())
                }
            }
        }
    }

    fun removeCover(track: Track) {
        viewModelScope.launch {
            userLoops.removeCover(track.id)
            covers.invalidate(track.id)
            _state.update { it.copy(cover = null) }
        }
    }

    /** The cover video failed: remember it for this session, no retry. */
    fun onCoverFailure(track: Track) {
        coverFailures += track.id
        _state.update { it.copy(cover = null) }
    }

    /** Re-evaluate the animated-cover settings after the user visited Settings. */
    fun refreshCoverSettings() {
        viewModelScope.launch {
            val current = settings.current()
            _state.update { it.copy(allowAnimated = current.animatedCovers) }
        }
    }

    companion object {
        const val BATCH = 10
        const val RECENT_EXCLUDE = 50
        const val FOLLOWING_ARTISTS = 10
        const val FOLLOWING_FEED_SIZE = 60
        const val PENDING_RETRY_BATCH = 12
        const val PENDING_ATTEMPT_CAP = 3
        const val SAVED_PLAYLIST_SEED = "Saved"

        /** Key the screen resolves to the sync-failure sentence. */
        const val NOTICE_LIKE_UNSYNCED = "like-unsynced"
    }
}
