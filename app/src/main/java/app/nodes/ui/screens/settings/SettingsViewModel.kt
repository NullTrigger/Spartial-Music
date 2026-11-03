/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.R
import app.nodes.core.data.AppSettings
import app.nodes.core.data.LibraryRepository
import app.nodes.core.data.RemoteCache
import app.nodes.core.data.SearchHistoryRepository
import app.nodes.core.data.SettingsRepository
import app.nodes.core.source.youtubemusic.InnerTubeAccount
import app.nodes.core.source.youtubemusic.InnerTubeAuth
import app.nodes.core.source.youtubemusic.YouTubeMusicSource
import app.nodes.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val cache: RemoteCache,
    private val searchHistory: SearchHistoryRepository,
    private val innerTubeAuth: InnerTubeAuth,
    private val innerTubeSource: YouTubeMusicSource,
    private val library: LibraryRepository,
    private val player: PlaybackController,
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _likesSyncRunning = MutableStateFlow(false)

    /** The Google account whose YouTube Music cookie is stored, if any. */
    val musicAccount: StateFlow<InnerTubeAccount?> = innerTubeAuth.account

    /** True while a likes sync to YouTube Music is running. */
    val likesSyncRunning: StateFlow<Boolean> = _likesSyncRunning.asStateFlow()

    val recentSearchCount: StateFlow<Int> = searchHistory.recent
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The sleep timer's deadline, so the row can count down. */
    val sleepTimerEndsAt: StateFlow<Long> = player.state
        .map { it.sleepTimerEndsAt }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { settings.setWifiOnly(enabled) }
    }

    fun setHighQuality(enabled: Boolean) {
        viewModelScope.launch { settings.setHighQuality(enabled) }
    }

    fun setCrashReportsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setCrashReportsEnabled(enabled) }
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch { settings.setSkipSilence(enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { settings.setKeepScreenOn(enabled) }
    }

    fun setStopMusicOnTaskClear(enabled: Boolean) {
        viewModelScope.launch { settings.setStopMusicOnTaskClear(enabled) }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { settings.setPlaybackSpeed(speed) }
    }

    fun setSleepTimer(minutes: Int) {
        player.setSleepTimer(minutes)
    }

    fun setYoutubeRegion(region: String) {
        viewModelScope.launch { settings.setYoutubeRegion(region) }
    }

    fun setLyricsPreferSynced(enabled: Boolean) {
        viewModelScope.launch { settings.setLyricsPreferSynced(enabled) }
    }

    fun setLyricsShowSource(enabled: Boolean) {
        viewModelScope.launch { settings.setLyricsShowSource(enabled) }
    }

    fun setLyricsAutoScroll(enabled: Boolean) {
        viewModelScope.launch { settings.setLyricsAutoScroll(enabled) }
    }

    fun setQueuePreventDuplicates(enabled: Boolean) {
        viewModelScope.launch { settings.setQueuePreventDuplicates(enabled) }
    }

    fun setLyricsProviderEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { settings.setLyricsProviderEnabled(id, enabled) }
    }

    /** Swaps [id] with its neighbour; the ends of the list do nothing. */
    fun moveLyricsProvider(id: String, delta: Int) {
        viewModelScope.launch {
            val order = appSettings.value.lyricsProviderOrder.toMutableList()
            val from = order.indexOf(id)
            val to = from + delta
            if (from < 0 || to !in order.indices) return@launch
            val moved = order.removeAt(from)
            order.add(to, moved)
            settings.setLyricsProviderOrder(order)
        }
    }

    fun signOutMusic() {
        innerTubeAuth.signOut()
        // Drop the WebView cookies too, otherwise the next visit to the login
        // page would silently sign the same account back in.
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        viewModelScope.launch {
            settings.setYoutubeAccount(null, false)
            _message.value = context.getString(R.string.settings_signed_out)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            cache.clear()
            _message.value = context.getString(R.string.settings_cache_cleared)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            searchHistory.clear()
            _message.value = context.getString(R.string.settings_search_history_cleared)
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    /**
     * Pushes every locally liked YouTube Music track to the account's liked
     * songs ("Sync to YT"). Unofficial and one-directional: Nodes never pulls
     * anything back, and re-running it is safe - liking what is already liked
     * changes nothing. It only affects likes the app stores locally from this
     * source's own results.
     */
    fun syncLikesToYouTube() {
        if (_likesSyncRunning.value) return
        viewModelScope.launch {
            if (!innerTubeAuth.isSignedIn) {
                _message.value = context.getString(R.string.settings_sync_signed_out)
                return@launch
            }
            _likesSyncRunning.value = true
            _message.value = context.getString(R.string.settings_sync_running)
            try {
                // Only tracks from YouTube Music carry a video id; local files
                // and Jamendo have nothing to like on YouTube.
                val videoIds = library.likedTracks.first().mapNotNull { it.videoId }
                if (videoIds.isEmpty()) {
                    _message.value = context.getString(R.string.settings_sync_nothing)
                    return@launch
                }
                val outcome = innerTubeSource.pushLikes(videoIds)
                _message.value = context.getString(
                    R.string.settings_sync_done,
                    outcome.pushed,
                    outcome.failed,
                )
            } finally {
                _likesSyncRunning.value = false
            }
        }
    }
}
