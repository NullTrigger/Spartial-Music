/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.suggestions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.model.Track
import app.nodes.ui.components.ArtworkCard
import app.nodes.ui.components.EmptyState
import app.nodes.ui.components.ErrorState
import app.nodes.ui.components.LoadingRows
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.SectionHeader
import app.nodes.ui.components.SongRow
import app.nodes.ui.components.TrackMenuButton
import app.nodes.ui.components.glass
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialSpacing
import app.nodes.ui.theme.SpartialType
import app.nodes.ui.theme.SpartialWhite

@Composable
fun SuggestionsScreen(
    onOpenArtist: (String) -> Unit = {},
    viewModel: SuggestionsViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.background(SpartialBlack)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = SpartialSpacing.dockSpace),
        ) {
            item(key = "header") {
                PageHeader(
                    title = stringResource(R.string.suggestions_title),
                    subtitle = state.sourceName.ifBlank {
                        stringResource(R.string.suggestions_subtitle)
                    },
                )
            }

            item(key = "intro") {
                SuggestionsIntro()
            }

            when {
                state.loading && state.isEmpty -> item(key = "loading") { LoadingRows() }

                state.error != null -> item(key = "error") {
                    ErrorState(error = state.error, onRetry = viewModel::refresh)
                }

                state.isEmpty -> item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.suggestions_title),
                        description = stringResource(R.string.suggestions_empty),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = viewModel::refresh,
                    )
                }

                else -> {
                    if (state.topPicks.isNotEmpty()) {
                        item(key = "top-header") {
                            SectionHeader(title = stringResource(R.string.suggestions_top_picks))
                        }
                        item(key = "top") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = SpartialSpacing.gutter),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.topPicks, key = { "top-${it.id}" }) { track ->
                                    ArtworkCard(
                                        track = track,
                                        onClick = {
                                            playerViewModel.play(track, state.topPicks)
                                        },
                                        width = 158.dp,
                                        showPlay = true,
                                    )
                                }
                            }
                        }
                    }

                    if (state.because.isNotEmpty()) {
                        item(key = "because-header") {
                            val seed = state.becauseSeed
                            SectionHeader(
                                title = if (seed != null) {
                                    stringResource(R.string.suggestions_because_recent, seed)
                                } else {
                                    stringResource(R.string.suggestions_top_picks)
                                },
                            )
                        }
                        items(state.because, key = { "because-${it.id}" }) { track ->
                            SuggestionSongRow(
                                track = track,
                                onClick = { playerViewModel.play(track, state.because) },
                                onOpenArtist = onOpenArtist,
                                playerViewModel = playerViewModel,
                            )
                        }
                    }

                    if (state.fresh.isNotEmpty()) {
                        item(key = "fresh-header") {
                            SectionHeader(title = stringResource(R.string.suggestions_fresh))
                        }
                        item(key = "fresh") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = SpartialSpacing.gutter),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.fresh, key = { "fresh-${it.id}" }) { track ->
                                    ArtworkCard(
                                        track = track,
                                        onClick = {
                                            playerViewModel.play(track, state.fresh)
                                        },
                                        width = 140.dp,
                                        showPlay = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionsIntro() {
    Row(
        modifier = Modifier
            .padding(horizontal = SpartialSpacing.gutter, vertical = 8.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), strong = true, scrim = 0.28f)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(SpartialWhite.copy(alpha = 0.1f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = SpartialWhite,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(
                text = stringResource(R.string.suggestions_title),
                style = SpartialType.rowTitle,
                color = SpartialWhite,
            )
            Text(
                text = stringResource(R.string.suggestions_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = SpartialGrey,
            )
        }
    }
}

@Composable
private fun SuggestionSongRow(
    track: Track,
    onClick: () -> Unit,
    onOpenArtist: (String) -> Unit,
    playerViewModel: PlayerViewModel,
) {
    SongRow(
        track = track,
        onClick = onClick,
        showDownload = false,
        showStar = false,
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
