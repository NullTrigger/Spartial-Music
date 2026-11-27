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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
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

    /** When the rails on screen were fetched; 0 until the first load. */
    private var lastLoadAtMs = 0L

    /**
     * Serialises the search-suggestion derive. Its three collectors (history,
     * library, recents) fire in bursts, and each derive may ask the provider
     * once, so overlapping runs would duplicate that call without adding
     * anything to the rail.
     */
    private val searchMutex = Mutex()

    /** The last provider answer for a search seed, and when it was fetched. */
    private var discoverySeedId: String? = null
    private var discoveryFetchedAtMs = 0L
    private var discoveryTracks: List<Track> = emptyList()

    /** The last provider answer for a bare query, cached the same way. */
    private var queryText: String? = null
    private var queryFetchedAtMs = 0L
    private var queryTracks: List<Track> = emptyList()

    /** The signed-in YouTube Music account, if any; drives the avatar initial. */
    val account: StateFlow<InnerTubeAccount?> = musicAuth.account

    /** The onboarding-picked avatar (1..9), shown in the home header circle. */
    val profileAvatarId: StateFlow<Int> = settings.profileAvatarId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

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

    /**
     * Called whenever the app comes back to the foreground.
     *
     * The Home rails are the provider's answer, so they have to be asked for
     * again when the user returns to the app: YouTube Music serves different
     * shelves across a day, and while the process was backgrounded this screen
     * kept showing the ones it fetched when it first opened. Re-deriving the
     * suggestions here is what makes "New Songs You May Like" move between
     * sessions.
     *
     * A short freshness window keeps a quick app switch (notification shade,
     * a call) from turning into a second Home request, and an in-flight load is
     * never doubled.
     */
    fun refreshOnOpen() {
        if (_state.value.loading) return
        val age = System.currentTimeMillis() - lastLoadAtMs
        if (lastLoadAtMs != 0L && age < HOME_REFRESH_AFTER_MS) return
        viewModelScope.launch { load(_state.value.sourceId) }
    }

    private suspend fun refreshSearchSuggestions() = searchMutex.withLock {
        val queries = searchHistory.recent.first()
        val cached = library.allTracks.first()
        val current = _state.value
        val excluded = (current.banners + current.feed.sections.flatMap { it.tracks } + current.recents)
            .mapTo(HashSet()) { it.id }
        val matches = searchHistorySuggestions(
            recentQueries = queries,
            cachedTracks = cached,
            excludedIds = excluded,
            limit = SEARCH_SUGGESTION_LIMIT,
        )
        // The rail is "more like what you searched", not the search again:
        // the best local match - or, with nothing local to seed from, the
        // provider's first answer for the newest query - names a radio, and
        // that radio is the rail.
        val seed = matches.firstOrNull()
            ?: queries.firstOrNull()?.let { query -> discoveryForQuery(query).firstOrNull() }
        val radio = seed?.let { discoveryAround(it) }.orEmpty()
        // The rows that answer the query by name are exactly what the user
        // just failed to want more of: searching "one love" kept filling this
        // row with songs called One Love. They only appear when the radio has
        // nothing to put in their place.
        val other = radio.filterNot { answersQuery(it, queries) }
        // Fewer slides beats a row of repeats: the literal answers are only
        // the fallback for a radio that came back empty (offline, for one).
        val suggestions = mergeSearchSuggestions(
            matches = emptyList(),
            discovery = other,
            excludedIds = excluded,
            fallback = if (other.isEmpty()) matches else emptyList(),
        )
        _state.update { it.copy(searchSuggestions = suggestions) }
    }

    /**
     * The provider's radio for [seed]: more of what the user searched for,
     * starting from the one track the app can name. Cached per seed for
     * [SEARCH_DISCOVERY_TTL_MS], because the library collector fires on every
     * play and the rail must not turn that into a request per tap.
     */
    private suspend fun discoveryAround(seed: Track): List<Track> {
        val now = System.currentTimeMillis()
        if (discoverySeedId == seed.id && now - discoveryFetchedAtMs < SEARCH_DISCOVERY_TTL_MS) {
            return discoveryTracks
        }
        val source = registry.byId(seed.sourceId) ?: registry.primary()
        val tracks = runCatching {
            (source.getRelated(seed, SEARCH_DISCOVERY_COUNT) as? SourceResult.Success)?.data
        }.getOrNull().orEmpty()
        if (tracks.isNotEmpty()) {
            discoverySeedId = seed.id
            discoveryFetchedAtMs = now
            discoveryTracks = tracks
        }
        return tracks
    }

    /** The provider's own answer for a query the local cache cannot match. */
    private suspend fun discoveryForQuery(query: String): List<Track> {
        val now = System.currentTimeMillis()
        if (queryText == query && now - queryFetchedAtMs < SEARCH_DISCOVERY_TTL_MS) {
            return queryTracks
        }
        val source = registry.byId(_state.value.sourceId) ?: registry.primary()
        val tracks = runCatching {
            (source.search(query, SEARCH_DISCOVERY_COUNT) as? SourceResult.Success)?.data
        }.getOrNull().orEmpty()
        if (tracks.isNotEmpty()) {
            queryText = query
            queryFetchedAtMs = now
            queryTracks = tracks
        }
        return tracks
    }

    private suspend fun load(sourceId: SourceId) {
        if (_state.value.sourceId != sourceId) {
            // A different provider owns a different catalogue, so the cached
            // search radios are no longer answers to these searches.
            discoverySeedId = null
            discoveryFetchedAtMs = 0L
            discoveryTracks = emptyList()
            queryText = null
            queryFetchedAtMs = 0L
            queryTracks = emptyList()
        }
        _state.update { it.copy(loading = true, error = null, sourceId = sourceId) }
        val source = registry.byId(sourceId) ?: registry.primary()
        val recent = _state.value.recents.ifEmpty { library.recentlyPlayed.first() }

        when (val result = source.getHome()) {
            is SourceResult.Success -> {
                val feed = result.data
                lastLoadAtMs = System.currentTimeMillis()
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
     * Banner carousel source, padded to [BANNER_COUNT] in this order:
     *
     *  1. songs by the artists this device actually listens to - the liked
     *     songs and the last plays are the only taste profile the app keeps,
     *     and no artist takes more than two slides;
     *  2. the user's own recent plays;
     *  3. the rest of the provider's song shelves, rows that name an album
     *     first;
     *  4. the provider's hero rail, last: it only pads a short carousel.
     *
     * The hero rail used to lead. It is the first five playable rows of the
     * home page in provider order, so the first slide was whatever that page
     * happened to open with - often a video row with the song's name burnt into
     * the frame. A carousel that starts from the user's own history is a
     * recommendation; one that starts from the provider's page is a copy of
     * somebody else's home screen.
     *
     * Only playable rows reach the carousel. A home shelf can also carry album
     * and playlist cards, which are [Track]s with a collection id and no video
     * id; tapping Play on one of those answered "This track has no YouTube
     * video id" instead of opening the album it named.
     */
    private suspend fun banners(feed: HomeFeed, recents: List<Track>): List<Track> {
        val hero = feed.hero.map { it.track }.filter { it.isPlayable() }
        val recent = recents.filter { it.isPlayable() }
        val shelved = feed.sections.flatMap { it.tracks }.filter { it.isPlayable() }
        val pool = (shelved + hero).distinctBy { it.id }

        val taste = tasteArtists(recent)
        val perArtist = HashMap<String, Int>()
        val byArtist = if (taste.isEmpty()) {
            emptyList()
        } else {
            pool.filter { track ->
                val artist = track.artist.trim().lowercase(Locale.ROOT)
                if (artist !in taste) return@filter false
                val used = perArtist[artist] ?: 0
                if (used >= SLIDES_PER_ARTIST) return@filter false
                perArtist[artist] = used + 1
                true
            }
        }
        // A row that names its album is a catalogue entry; a bare video row is
        // whatever the page carried, so it only fills a carousel still short.
        val catalogued = pool.filterNot { it.album.isNullOrBlank() }

        return (byArtist + recent + catalogued + pool + hero)
            .distinctBy { it.id }
            .take(BANNER_COUNT)
    }

    /**
     * The artists this device listens to, most-evidenced first: the liked songs
     * and the last plays, counted per artist. Empty on a fresh install, where
     * the carousel falls back to the provider's order.
     */
    private suspend fun tasteArtists(recent: List<Track>): Set<String> {
        val liked = runCatching { library.likedTracks.first() }.getOrDefault(emptyList())
        return (liked + recent)
            .map { it.artist.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it.lowercase(Locale.ROOT) }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(TASTE_ARTIST_COUNT)
            .mapTo(HashSet()) { it.key }
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
        val hero = feed.hero.map { it.track }.filter { it.isPlayable() }
        val shelved = feed.sections.flatMap { it.tracks }.filter { it.isPlayable() }
        val all = (hero + shelved + feed.continuePlaying).filter { it.isPlayable() }.distinctBy { it.id }
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

/** How many different artists the carousel's taste picks may draw on. */
private const val TASTE_ARTIST_COUNT = 8

/** At most this many slides by one artist, so the carousel keeps some variety. */
private const val SLIDES_PER_ARTIST = 2

        /**
         * How stale the rails may be when the app is reopened. Below this, the
         * screen keeps what it has and nothing is fetched; above it, Home asks
         * the provider again so its suggestions are never yesterday's.
         */
        const val HOME_REFRESH_AFTER_MS = 60_000L
        const val NEW_SONG_COUNT = 10

        /** How much of a radio answer the search rail blends in. */
        const val SEARCH_DISCOVERY_COUNT = 20

        /** How long a search's radio answer stays usable before refetching. */
        const val SEARCH_DISCOVERY_TTL_MS = 10 * 60 * 1000L
    }
}

/**
 * A row the player can actually start.
 *
 * A Home shelf also carries album and playlist cards, which are [Track]s with a
 * collection id and no video id: tapping one opens a page, it does not play.
 * Feeding one to the player answered "This track has no YouTube video id", so
 * the rails that only know how to play - the banner carousel and the
 * suggestions rails - keep to playable rows.
 */
private fun Track.isPlayable(): Boolean = videoId != null || mediaUri != null
