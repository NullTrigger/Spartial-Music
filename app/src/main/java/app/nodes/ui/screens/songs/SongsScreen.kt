/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.ui.components.EmptyState
import app.nodes.ui.components.LoadingRows
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.SearchField
import app.nodes.ui.components.SegmentedFilter
import app.nodes.ui.components.SongRow
import app.nodes.ui.components.TrackMenuButton
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialSpacing

@Composable
fun SongsScreen(
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    viewModel: SongsViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val sortLabels = listOf(
        stringResource(R.string.library_sort_recent),
        stringResource(R.string.library_sort_title),
        stringResource(R.string.library_sort_artist),
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(SpartialBlack),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = SpartialSpacing.dockSpace),
        ) {
            item(key = "header") {
                PageHeader(
                    title = stringResource(R.string.songs_title),
                    subtitle = stringResource(R.string.tracks_count, state.total),
                    back = onBack,
                )
            }

            item(key = "search") {
                SearchField(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    onSearch = {},
                    placeholder = stringResource(R.string.songs_search),
                    modifier = Modifier
                        .padding(horizontal = SpartialSpacing.gutter)
                        .padding(top = 10.dp),
                )
            }

            item(key = "sort") {
                SegmentedFilter(
                    options = sortLabels,
                    selectedIndex = state.sort.ordinal,
                    onSelect = { index -> viewModel.setSort(SongSort.entries[index]) },
                    modifier = Modifier.padding(horizontal = SpartialSpacing.gutter, vertical = 10.dp),
                )
            }

            when {
                state.loading -> item(key = "loading") { LoadingRows() }

                state.tracks.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.songs_title),
                        description = if (state.query.isBlank()) {
                            stringResource(R.string.songs_empty)
                        } else {
                            stringResource(R.string.library_empty_search, state.query)
                        },
                    )
                }

                else -> items(state.tracks, key = { it.id }) { track ->
                    SongRow(
                        track = track,
                        onClick = { playerViewModel.play(track, state.tracks) },
                        isLiked = track.id in state.likedIds,
                        onToggleLike = { viewModel.toggleLike(track) },
                        showDownload = false,
                        active = track.id == playerState.current?.id,
                        menu = {
                            TrackMenuButton(
                                onPlayNext = { playerViewModel.playNext(track) },
                                onAddToQueue = { playerViewModel.addToQueue(track) },
                                onViewArtist = if (track.artist.isNotBlank()) {
                                    { onOpenArtist(track.artist) }
                                } else {
                                    null
                                },
                            )
                        },
                    )
                }
            }

            item(key = "count-footer") {
                if (state.tracks.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.tracks_count, state.tracks.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = SpartialGrey,
                        modifier = Modifier.padding(
                            horizontal = SpartialSpacing.gutter,
                            vertical = 16.dp,
                        ),
                    )
                }
            }
        }
    }
}
