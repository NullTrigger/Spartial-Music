/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.R
import app.nodes.core.data.AppSettings
import app.nodes.core.data.BackupSettings
import app.nodes.core.data.LibraryBackup
import app.nodes.core.data.LibraryRepository
import app.nodes.core.data.LyricsRepository
import app.nodes.core.data.RemoteCache
import app.nodes.core.data.SearchHistoryRepository
import app.nodes.core.data.SettingsRepository
import app.nodes.core.lyrics.LyricsProviders
import app.nodes.core.source.youtubemusic.InnerTubeAccount
import app.nodes.core.source.youtubemusic.InnerTubeAuth
import app.nodes.core.source.youtubemusic.YouTubeMusicSource
import app.nodes.playback.PlaybackController
import coil.imageLoader
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
    private val lyrics: LyricsRepository,
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

    fun setAnimatedCovers(enabled: Boolean) {
        viewModelScope.launch { settings.setAnimatedCovers(enabled) }
    }

    fun setAnimatedCoversOnMobile(enabled: Boolean) {
        viewModelScope.launch { settings.setAnimatedCoversOnMobile(enabled) }
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

    fun setAutomix(enabled: Boolean) {
        viewModelScope.launch { settings.setAutomix(enabled) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch { settings.setCrossfadeSeconds(seconds) }
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

    fun setRememberQueue(enabled: Boolean) {
        viewModelScope.launch {
            settings.setRememberQueue(enabled)
            if (!enabled) library.clearSavedQueue()
        }
    }

    fun setRememberShuffle(enabled: Boolean) {
        viewModelScope.launch { settings.setRememberShuffle(enabled) }
    }

    fun setAutoplaySimilar(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoplaySimilar(enabled) }
    }

    fun setLyricsProviderEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { settings.setLyricsProviderEnabled(id, enabled) }
    }

    /**
     * Swaps [id] with its neighbour of the same group; the ends of the list do
     * nothing. The community sources and the third-party group are shown in
     * separate sections, so a row must never jump across the group boundary.
     */
    fun moveLyricsProvider(id: String, delta: Int) {
        viewModelScope.launch {
            val order = appSettings.value.lyricsProviderOrder
            val thirdParty = LyricsProviders.ADDITIONAL_SOURCES
            val sameGroup = order.filter { (it in thirdParty) == (id in thirdParty) }
            val from = sameGroup.indexOf(id)
            val to = from + delta
            if (from < 0 || to !in sameGroup.indices) return@launch
            val reordered = order.toMutableList()
            val a = reordered.indexOf(id)
            val b = reordered.indexOf(sameGroup[to])
            reordered[a] = sameGroup[to]
            reordered[b] = id
            settings.setLyricsProviderOrder(reordered)
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

    /** Clear only Coil's in-memory and on-disk artwork/thumbnail entries. */
    fun clearThumbnailCache() {
        viewModelScope.launch {
            context.imageLoader.memoryCache?.clear()
            context.imageLoader.diskCache?.clear()
            _message.value = context.getString(R.string.settings_thumbnail_cache_cleared)
        }
    }

    /** Clear cached lyric payloads and the in-memory resolved results. */
    fun clearLyricsCache() {
        viewModelScope.launch {
            lyrics.clearCache()
            _message.value = context.getString(R.string.settings_lyrics_cache_cleared)
        }
    }

    /** Clear cached provider/catalogue responses; saved library rows are kept. */
    fun clearSongCache() {
        viewModelScope.launch {
            cache.clear()
            _message.value = context.getString(R.string.settings_song_cache_cleared)
        }
    }

    /** Clear the three disposable cache stores, but never library data or audio. */
    fun clearCache() {
        viewModelScope.launch {
            context.imageLoader.memoryCache?.clear()
            context.imageLoader.diskCache?.clear()
            lyrics.clearCache()
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
     * songs ("Sync to YT"). Unofficial and one-directional: Spartial Music never pulls
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
                // have nothing to like on YouTube.
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

    // ------------------------------------------------------------------
    // Backup and restore. One versioned JSON file; see LibraryBackup. No
    // credential is part of the model, so none can be written or read.
    // ------------------------------------------------------------------

    private val backupJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Writes the library backup to [uri] (a `CreateDocument` result). */
    fun exportLibrary(uri: android.net.Uri, includeHistory: Boolean) {
        viewModelScope.launch {
            runCatching {
                // Only the profile travels in the settings block today: it is
                // local, non-secret, and the one thing a restored device
                // cannot guess. Credentials are not fields of this model.
                val profile = settings.current()
                val backup = library.exportBackup(includeHistory).copy(
                    settings = BackupSettings(
                        profileAvatarId = profile.profileAvatarId,
                        profileDisplayName = profile.profileDisplayName,
                    ),
                )
                val json = backupJson.encodeToString(LibraryBackup.serializer(), backup)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("Could not open the file for writing.")
            }.onSuccess {
                _message.value = context.getString(R.string.settings_export_done)
            }.onFailure { error ->
                _message.value = context.getString(
                    R.string.settings_export_failed,
                    error.message ?: "unknown error",
                )
            }
        }
    }

    /**
     * Reads a library backup from [uri] (an `OpenDocument` result) and merges
     * it. Existing data is never dropped, unknown fields are ignored, and a
     * file over 20 MB or from a newer schema is refused with a clear message.
     */
    fun importLibrary(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                } ?: error("Could not open the file.")
                if (bytes.size > MAX_BACKUP_BYTES) {
                    error(context.getString(R.string.settings_import_too_large))
                }
                val backup = backupJson.decodeFromString(LibraryBackup.serializer(), bytes.decodeToString())
                if (backup.schemaVersion > LibraryBackup.SCHEMA_VERSION) {
                    error(context.getString(R.string.settings_import_newer))
                }
                val result = library.importBackup(backup)
                backup.settings?.let { settings.applyBackupSettings(it) }
                result
            }.onSuccess { result ->
                _message.value = context.getString(
                    R.string.settings_import_done,
                    result.playlists,
                    result.liked,
                )
            }.onFailure { error ->
                _message.value = error.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.settings_import_failed)
            }
        }
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 20L * 1024L * 1024L
    }
}
