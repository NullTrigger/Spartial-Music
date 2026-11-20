/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.LibraryRepository
import app.nodes.core.data.SearchHistoryRepository
import app.nodes.core.data.SettingsRepository
import app.nodes.core.model.HomeFeed
import app.nodes.core.model.SourceErrorType
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.SourceRegistry
import app.nodes.core.source.youtube.YouTubeSignIn
import app.nodes.core.source.youtubemusic.InnerTubeAccount
import app.nodes.core.source.youtubemusic.InnerTubeAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val feed: HomeFeed = HomeFeed(),
    val recents: List<Track> = emptyList(),
    /** Banner carousel: the recommendation rail, deduped, capped at eight. */
    val banners: List<Track> = emptyList(),
    /** The suggestions derive's "fresh" list, unchanged. */
    val newSongs: List<Track> = emptyList(),
    /** Tracks in the local catalogue that match the user's recent searches. */
    val searchSuggestions: List<Track> = emptyList(),
    val error: SourceErrorType? = null,
    val sourceName: String = "",
    val sourceId: SourceId = SourceId.LOCAL,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val registry: SourceRegistry,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    private val searchHistory: SearchHistoryRepository,
    private val signIn: YouTubeSignIn,
    private val musicAuth: InnerTubeAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** The signed-in YouTube Music account, if any; drives the avatar initial. */
    val account: StateFlow<InnerTubeAccount?> = musicAuth.account

    init {
        viewModelScope.launch {
            settings.settings
                .map { it.primarySource }
                .distinctUntilChanged()
                .collectLatest { sourceId -> load(sourceId) }
        }
        // Signing in or out adds or removes the personal rails, so reload rather
        // than making the user restart the app to see their own music.
        viewModelScope.launch {
            signIn.state
                .drop(1)
                .collect { load(_state.value.sourceId) }
        }
        // The same for the YouTube Music cookie: signing in makes Home personal,
        // signing out returns it to the anonymous shelves.
        viewModelScope.launch {
            musicAuth.account
                .drop(1)
                .collect { load(_state.value.sourceId) }
        }
        // "Recents" is the local listening history, independent of the active
        // provider: it stays useful while a network source is offline.
        viewModelScope.launch {
            library.recentlyPlayed.collect { recents ->
                _state.update { it.copy(recents = recents) }
                refreshSearchSuggestions()
            }
        }
        // The search history and locally cached search results are the only
        // inputs. Updating either recomputes a local rail without making calls.
        viewModelScope.launch {
            searchHistory.recent.collect {
                refreshSearchSuggestions()
            }
        }
        viewModelScope.launch {
            library.allTracks.collect {
                refreshSearchSuggestions()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { load(_state.value.sourceId) }
    }

    private suspend fun refreshSearchSuggestions() {
        val queries = searchHistory.recent.first()
        val cached = library.allTracks.first()
        val current = _state.value
        val excluded = (current.banners + current.feed.sections.flatMap { it.tracks } + current.recents)
            .mapTo(HashSet()) { it.id }
        val suggestions = searchHistorySuggestions(
            recentQueries = queries,
            cachedTracks = cached,
            excludedIds = excluded,
        )
        _state.update { it.copy(searchSuggestions = suggestions) }
    }

    private suspend fun load(sourceId: SourceId) {
        _state.update { it.copy(loading = true, error = null, sourceId = sourceId) }
        val source = registry.byId(sourceId) ?: registry.primary()
        val recent = _state.value.recents.ifEmpty { library.recentlyPlayed.first() }

        when (val result = source.getHome()) {
            is SourceResult.Success -> {
                val feed = result.data
                val bannerTracks = banners(feed, recent)
                _state.update {
                    it.copy(
                        loading = false,
                        sourceName = source.displayName,
                        error = null,
                        feed = feed.copy(
                            continuePlaying = recent.ifEmpty { feed.continuePlaying },
                        ),
                        banners = bannerTracks,
                        newSongs = newSongs(feed, recent),
                    )
                }
                refreshSearchSuggestions()
                // Record what was fetched: album and artist pages are a Room
                // query over music the app has already seen.
                runCatching {
                    library.cacheTracks(feed.hero.map { it.track } + feed.sections.flatMap { it.tracks })
                }
                // And warm the streams the next tap is most likely to ask for.
                prefetchLikelyTaps(bannerTracks, feed)
            }

            is SourceResult.Failure -> {
                _state.update {
                    it.copy(
                        loading = false,
                        error = result.type,
                        sourceName = source.displayName,
                    )
                }
            }
        }
    }

    /**
     * Warms the stream URL of the few tracks the next tap is most likely to
     * play: the head of the banner carousel and the first row of the first
     * shelves. A tap pays one network round trip before it can make a sound -
     * this is that same round trip, done ahead of the tap, into the source's
     * own URL cache. The budget is capped and the calls are sequential, so a
     * Home load stays a handful of requests.
     */
    private fun prefetchLikelyTaps(banners: List<Track>, feed: HomeFeed) {
        val likely = (banners.take(3) + feed.sections.mapNotNull { it.tracks.firstOrNull() }.take(2))
            .filter { it.videoId != null }
            .distinctBy { it.id }
        if (likely.isEmpty()) return
        viewModelScope.launch {
            likely.forEach { track ->
                runCatching { registry.forTrack(track.sourceId)?.prefetch(track) }
            }
        }
    }

    /**
     * Banner carousel source, padded to exactly eight: the provider's hero
     * rail first (the existing recommendation output), then the user's recent
     * plays, then the provider's own fallback shelves. Every stage is deduped,
     * so no song repeats inside the carousel. Fewer than eight only when the
     * app genuinely knows fewer than eight songs.
     */
    private fun banners(feed: HomeFeed, recents: List<Track>): List<Track> {
        val hero = feed.hero.map { it.track }
        val recent = recents.filterNot { track -> track.id in hero.map { it.id }.toSet() }
        val shelved = feed.sections
            .flatMap { it.tracks }
            .filterNot { track -> track.id in (hero + recent).map { it.id }.toSet() }
        return (hero + recent + shelved)
            .distinctBy { it.id }
            .take(BANNER_COUNT)
    }

    /**
     * "New songs you may like" runs the same derive the Suggestions screen
     * runs over the same feed - hero, shelves and recent plays - so the home
     * screen shows that algorithm's output without a second implementation:
     *
     * top = the algorithm's own first picks (excluded), because = tracks that
     * share the most recent song's artist, then everything else that remains.
     */
    private fun newSongs(feed: HomeFeed, recents: List<Track>): List<Track> {
        val hero = feed.hero.map { it.track }
        val shelved = feed.sections.flatMap { it.tracks }
        val all = (hero + shelved + feed.continuePlaying).distinctBy { it.id }
        val seed = recents.firstOrNull()
        val top = (hero + shelved).distinctBy { it.id }.take(8)
        val topIds = top.mapTo(HashSet()) { it.id }
        val related = seed?.let { first ->
            all.filter { track ->
                track.id != first.id && track.artist.equals(first.artist, ignoreCase = true)
            }
        }.orEmpty()
        val because = (related + all)
            .distinctBy { it.id }
            .filterNot { it.id in topIds }
            .take(6)
        val becauseIds = because.mapTo(HashSet()) { it.id }
        return all
            .filterNot { it.id in topIds || it.id in becauseIds }
            .take(NEW_SONG_COUNT)
    }

    private companion object {
        const val BANNER_COUNT = 8
        const val NEW_SONG_COUNT = 10
    }
}
