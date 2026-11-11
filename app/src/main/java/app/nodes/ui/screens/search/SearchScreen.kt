/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.ui.components.Artwork
import app.nodes.ui.components.ArtistAvatar
import app.nodes.ui.components.EmptyState
import app.nodes.ui.components.ErrorState
import app.nodes.ui.components.GenreTile
import app.nodes.ui.components.LoadingRows
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.SearchField
import app.nodes.ui.components.SectionHeader
import app.nodes.ui.components.SegmentedFilter
import app.nodes.ui.components.SongRow
import app.nodes.ui.components.TrackMenuButton
import app.nodes.ui.components.glass
import app.nodes.ui.components.trackSupportingLine
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesSpacing
import app.nodes.ui.theme.NodesType
import app.nodes.ui.theme.NodesWhite

private data class BrowseEntry(
    val gradientIndex: Int,
    val label: String,
    val query: String,
)

/**
 * Search opens on browse tiles, then turns into grouped results as soon as
 * there is a query. Albums and artists are derived from the song results the
 * active provider returns, so one request still fills all three groups.
 */
@Composable
fun SearchScreen(
    onBack: (() -> Unit)? = null,
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by viewModel.recentQueries.collectAsStateWithLifecycle(initialValue = emptyList())
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()

    val genres = listOf(
        BrowseEntry(0, stringResource(R.string.search_genre_pop), "pop"),
        BrowseEntry(1, stringResource(R.string.search_genre_rock), "rock"),
        BrowseEntry(2, stringResource(R.string.search_genre_hiphop), "hip-hop"),
        BrowseEntry(3, stringResource(R.string.search_genre_electronic), "electronic"),
        BrowseEntry(4, stringResource(R.string.search_genre_jazz), "jazz"),
        BrowseEntry(5, stringResource(R.string.search_genre_classical), "classical"),
        BrowseEntry(6, stringResource(R.string.search_genre_lofi), "lo-fi"),
        BrowseEntry(7, stringResource(R.string.search_genre_indie), "indie"),
    )
    val browse = listOf(
        BrowseEntry(1, stringResource(R.string.search_browse_new_releases), "new releases"),
        BrowseEntry(3, stringResource(R.string.search_browse_charts), "charts"),
        BrowseEntry(5, stringResource(R.string.search_browse_chill), "chill"),
        BrowseEntry(0, stringResource(R.string.search_browse_focus), "focus"),
        BrowseEntry(4, stringResource(R.string.search_browse_workout), "workout"),
        BrowseEntry(6, stringResource(R.string.search_browse_discover), "discover"),
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        PageHeader(
            title = stringResource(R.string.search_title),
            subtitle = stringResource(R.string.search_idle),
            back = onBack,
        )

        SearchField(
            query = state.query,
            onQueryChange = viewModel::setQuery,
            onSearch = viewModel::submit,
            modifier = Modifier.padding(
                start = NodesSpacing.gutter,
                end = NodesSpacing.gutter,
                top = 12.dp,
            ),
        )

        if (state.query.isBlank() && recent.isNotEmpty()) {
            ChipRow(
                label = stringResource(R.string.settings_search_history),
                values = recent,
                onClick = viewModel::useRecent,
            )
        }

        // YouTube Music's own suggestions, offered while typing and dropped as
        // soon as a search is submitted.
        if (state.query.isNotBlank() && suggestions.isNotEmpty()) {
            ChipRow(
                values = suggestions,
                onClick = viewModel::useSuggestion,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = NodesSpacing.dockSpace),
        ) {
            if (state.query.isBlank()) {
                item(key = "genres-head") {
                    SectionHeader(title = stringResource(R.string.search_your_genres))
                }
                genreRows(genres) { entry ->
                    viewModel.useRecent(entry.query)
                }
                item(key = "browse-head") {
                    SectionHeader(title = stringResource(R.string.search_browse_music))
                }
                genreRows(browse) { entry ->
                    viewModel.useRecent(entry.query)
                }
                return@LazyColumn
            }

            state.error?.let { error ->
                item(key = "error") {
                    ErrorState(
                        error = error,
                        onRetry = viewModel::retry,
                    )
                }
            }

            if (state.loading && state.results.isEmpty()) {
                item(key = "loading") { LoadingRows() }
            }

            if (state.searched &&
                !state.loading &&
                state.error == null &&
                state.results.isEmpty()
            ) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.search_title),
                        description = stringResource(R.string.search_no_results, state.query),
                    )
                }
            }

            if (state.results.isNotEmpty()) {
                item(key = "filters") {
                    val filters = listOf(
                        stringResource(R.string.search_filter_all),
                        stringResource(R.string.search_group_songs),
                        stringResource(R.string.search_filter_albums),
                        stringResource(R.string.search_filter_artists),
                    )
                    SegmentedFilter(
                        options = filters,
                        selectedIndex = state.filter.ordinal,
                        onSelect = { index -> viewModel.setFilter(SearchFilter.entries[index]) },
                        modifier = Modifier.padding(
                            horizontal = NodesSpacing.gutter,
                            vertical = 10.dp,
                        ),
                    )
                }

                when (state.filter) {
                    SearchFilter.ALL -> {
                        if (state.artists.isNotEmpty()) {
                            item(key = "artists-head") {
                                SectionHeader(title = stringResource(R.string.search_group_artists))
                            }
                            item(key = "artists-rail") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = NodesSpacing.gutter),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(state.artists, key = { "artist-${it.name}" }) { artist ->
                                        ArtistAvatar(
                                            artist = artist.name,
                                            artworkUri = artist.artworkUri,
                                            onClick = { onOpenArtist(artist.name) },
                                            size = 76.dp,
                                        )
                                    }
                                }
                            }
                        }
                        if (state.albums.isNotEmpty()) {
                            item(key = "albums-head") {
                                SectionHeader(title = stringResource(R.string.search_group_albums))
                            }
                            item(key = "albums-rail") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = NodesSpacing.gutter),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(state.albums, key = { "album-${it.name}" }) { album ->
                                        AlbumResultCard(
                                            album = album,
                                            onClick = { onOpenAlbum(album.name) },
                                            modifier = Modifier.width(144.dp),
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "songs-head") {
                            SectionHeader(title = stringResource(R.string.search_group_songs))
                        }
                        songItems(
                            state = state,
                            likedIds = likedIds,
                            activeId = playerState.current?.id,
                            onPlay = { track -> playerViewModel.play(track, state.results) },
                            onToggleLike = viewModel::toggleLike,
                            onPlayNext = playerViewModel::playNext,
                            onAddToQueue = playerViewModel::addToQueue,
                            onOpenArtist = onOpenArtist,
                            onOpenAlbum = onOpenAlbum,
                        )
                    }

                    SearchFilter.SONGS -> songItems(
                        state = state,
                        likedIds = likedIds,
                        activeId = playerState.current?.id,
                        onPlay = { track -> playerViewModel.play(track, state.results) },
                        onToggleLike = viewModel::toggleLike,
                        onPlayNext = playerViewModel::playNext,
                        onAddToQueue = playerViewModel::addToQueue,
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                    )

                    SearchFilter.ALBUMS -> {
                        if (state.albums.isEmpty()) {
                            item(key = "albums-empty") {
                                EmptyState(
                                    title = stringResource(R.string.search_filter_albums),
                                    description = stringResource(
                                        R.string.search_no_results,
                                        state.query,
                                    ),
                                )
                            }
                        } else {
                            items(
                                items = state.albums.chunked(2),
                                key = { row -> "album-row-${row.first().name}" },
                            ) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = NodesSpacing.gutter,
                                            vertical = 8.dp,
                                        ),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    row.forEach { album ->
                                        AlbumResultCard(
                                            album = album,
                                            onClick = { onOpenAlbum(album.name) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    SearchFilter.ARTISTS -> {
                        if (state.artists.isEmpty()) {
                            item(key = "artists-empty") {
                                EmptyState(
                                    title = stringResource(R.string.search_filter_artists),
                                    description = stringResource(
                                        R.string.search_no_results,
                                        state.query,
                                    ),
                                )
                            }
                        } else {
                            items(state.artists, key = { "artist-row-${it.name}" }) { artist ->
                                ArtistResultRow(
                                    artist = artist,
                                    onClick = { onOpenArtist(artist.name) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(
    values: List<String>,
    onClick: (String) -> Unit,
    label: String? = null,
) {
    if (values.isEmpty()) return
    Column(Modifier.padding(top = 8.dp)) {
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = NodesGrey,
                modifier = Modifier.padding(horizontal = NodesSpacing.gutter),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = NodesSpacing.gutter, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(values, key = { it }) { term ->
                Box(
                    Modifier
                        .glass(CircleShape)
                        .clickable { onClick(term) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = term,
                        style = MaterialTheme.typography.labelMedium,
                        color = NodesWhite,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.genreRows(
    entries: List<BrowseEntry>,
    onSelect: (BrowseEntry) -> Unit,
) {
    entries.chunked(2).forEach { row ->
        item(key = "genre-row-${row.first().gradientIndex}-${row.first().label}") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NodesSpacing.gutter, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { entry ->
                    GenreTile(
                        title = entry.label,
                        onClick = { onSelect(entry) },
                        index = entry.gradientIndex,
                        modifier = Modifier
                            .weight(1f)
                            .height(94.dp),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.songItems(
    state: SearchUiState,
    likedIds: Set<String>,
    activeId: String?,
    onPlay: (app.nodes.core.model.Track) -> Unit,
    onToggleLike: (app.nodes.core.model.Track) -> Unit,
    onPlayNext: (app.nodes.core.model.Track) -> Unit,
    onAddToQueue: (app.nodes.core.model.Track) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    items(items = state.results, key = { "song-${it.id}" }) { track ->
        SongRow(
            track = track,
            onClick = { onPlay(track) },
            supportingText = trackSupportingLine(track),
            isLiked = track.id in likedIds,
            onToggleLike = { onToggleLike(track) },
            showDownload = false,
            active = track.id == activeId,
            menu = {
                TrackMenuButton(
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onViewArtist = if (track.artist.isNotBlank()) {
                        { onOpenArtist(track.artist) }
                    } else {
                        null
                    },
                    onViewAlbum = track.album?.takeIf { it.isNotBlank() }?.let { album ->
                        { onOpenAlbum(album) }
                    },
                )
            },
        )
    }
}

@Composable
private fun AlbumResultCard(
    album: SearchAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp),
    ) {
        Artwork(
            artworkUri = album.artworkUri,
            title = album.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(18.dp),
            targetPx = 512,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.name,
            style = NodesType.rowTitle,
            color = NodesWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.collection_track_count, album.trackCount),
            style = NodesType.rowMeta,
            color = NodesGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistResultRow(
    artist: SearchArtist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = NodesSpacing.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            artworkUri = artist.artworkUri,
            title = artist.name,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            targetPx = 256,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = NodesType.rowTitle,
                color = NodesWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.artist_songs_count, artist.trackCount),
                style = NodesType.rowMeta,
                color = NodesGrey,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = NodesGrey,
            modifier = Modifier.size(22.dp),
        )
    }
}
