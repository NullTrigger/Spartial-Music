/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.trending

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import app.nodes.ui.components.ErrorState
import app.nodes.ui.components.LoadingRows
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.SongRow
import app.nodes.ui.components.TrackMenuButton
import app.nodes.ui.components.trackSupportingLine
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesSpacing

/**
 * The provider's current chart. Rows carry the same save, menu and artist
 * actions as every other song list; the page itself is deliberately just a
 * ranked list, not a second discovery feed.
 */
@Composable
fun TrendingScreen(
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    viewModel: TrendingViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = NodesSpacing.dockSpace),
        ) {
            item(key = "header") {
                PageHeader(
                    title = stringResource(R.string.trending_title),
                    subtitle = state.sourceName.ifBlank {
                        stringResource(R.string.trending_subtitle)
                    },
                )
            }

            state.error?.let { error ->
                item(key = "error") {
                    ErrorState(
                        error = error,
                        onRetry = viewModel::refresh,
                    )
                }
            }

            if (state.loading && state.tracks.isEmpty()) {
                item(key = "loading") { LoadingRows(count = 6) }
            }

            if (!state.loading && state.tracks.isEmpty() && state.error == null) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.trending_title),
                        description = stringResource(R.string.home_empty),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = viewModel::refresh,
                    )
                }
            }

            if (state.tracks.isNotEmpty()) {
                item(key = "count") {
                    Text(
                        text = stringResource(R.string.tracks_count, state.tracks.size)
                            .uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NodesGrey,
                        modifier = Modifier.padding(
                            start = NodesSpacing.gutter,
                            end = NodesSpacing.gutter,
                            top = 14.dp,
                            bottom = 6.dp,
                        ),
                    )
                }
            }

            itemsIndexed(items = state.tracks, key = { _, track -> track.id }) { index, track ->
                SongRow(
                    track = track,
                    onClick = { playerViewModel.play(track, state.tracks) },
                    position = index + 1,
                    supportingText = trackSupportingLine(track),
                    isLiked = track.id in likedIds,
                    onToggleLike = { viewModel.toggleLike(track) },
                    showDownload = false,
                    active = track.id == playerState.current?.id,
                    activePlaying = track.id == playerState.current?.id &&
                        playerState.isPlaying,
                    menu = {
                        TrackMenuButton(
                            onPlayNext = { playerViewModel.playNext(track) },
                            onAddToQueue = { playerViewModel.addToQueue(track) },
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
    }
}
