/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.AppSettings
import app.nodes.core.data.RemoteCache
import app.nodes.core.data.SearchHistoryRepository
import app.nodes.core.data.SettingsRepository
import app.nodes.core.source.youtubemusic.InnerTubeAccount
import app.nodes.core.source.youtubemusic.InnerTubeAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val cache: RemoteCache,
    private val searchHistory: SearchHistoryRepository,
    private val innerTubeAuth: InnerTubeAuth,
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** The Google account whose YouTube Music cookie is stored, if any. */
    val musicAccount: StateFlow<InnerTubeAccount?> = innerTubeAuth.account

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { settings.setWifiOnly(enabled) }
    }

    fun setHighQuality(enabled: Boolean) {
        viewModelScope.launch { settings.setHighQuality(enabled) }
    }

    fun signOutMusic() {
        innerTubeAuth.signOut()
        // Drop the WebView cookies too, otherwise the next visit to the login
        // page would silently sign the same account back in.
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        viewModelScope.launch {
            settings.setYoutubeAccount(null, false)
            _message.value = "Signed out of YouTube Music. Home is anonymous again."
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            cache.clear()
            searchHistory.clear()
            _message.value = "Cached responses cleared."
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
