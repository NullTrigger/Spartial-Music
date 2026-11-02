/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.trending

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import app.nodes.ui.components.EmptyPanel
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.TrackRow
import app.nodes.ui.components.sourceErrorMessage
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey

@Composable
fun TrendingScreen(
    viewModel: TrendingViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        PageHeader(
            title = stringResource(R.string.trending_title),
            subtitle = state.sourceName.ifBlank { stringResource(R.string.trending_subtitle) },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 176.dp),
        ) {
            state.error?.let { error ->
                item(key = "error") {
                    EmptyPanel(
                        title = stringResource(R.string.source_error),
                        description = sourceErrorMessage(error),
                    )
                }
            }

            if (!state.loading && state.tracks.isEmpty() && state.error == null) {
                item(key = "empty") {
                    EmptyPanel(
                        title = stringResource(R.string.trending_title),
                        description = stringResource(R.string.home_empty),
                    )
                }
            }

            itemsIndexed(items = state.tracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    track = track,
                    position = index + 1,
                    onClick = { playerViewModel.play(track, state.tracks) },
                )
            }

            if (state.loading) {
                item(key = "loading") {
                    Text(
                        text = stringResource(R.string.label_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = NodesGrey,
                        modifier = Modifier
                            .background(NodesBlack)
                            .padding(20.dp),
                    )
                }
            }
        }
    }
}
