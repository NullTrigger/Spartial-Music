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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.ui.components.EmptyPanel
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.TrackMenuButton
import app.nodes.ui.components.TrackRow
import app.nodes.ui.components.glass
import app.nodes.ui.components.sourceErrorMessage
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesTeal
import app.nodes.ui.theme.NodesWhite

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by viewModel.recentQueries.collectAsStateWithLifecycle(initialValue = emptyList())
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        PageHeader(
            title = stringResource(R.string.search_title),
            back = onBack,
        )

        SearchField(
            query = state.query,
            onQueryChange = viewModel::setQuery,
            onSearch = viewModel::submit,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        // Typing starts a search on its own; this bar makes the wait visible
        // instead of leaving the previous results frozen under new text.
        if (state.loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                color = NodesTeal,
            )
        }

        if (state.query.isBlank() && recent.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(recent, key = { it }) { term ->
                    Box(
                        Modifier
                            .glass(CircleShape)
                            .clickable { viewModel.useRecent(term) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = term,
                            style = MaterialTheme.typography.labelMedium,
                            color = NodesWhite,
                        )
                    }
                }
            }
        }

        // YouTube Music's own suggestions, offered while typing and dropped as
        // soon as a search is submitted.
        if (state.query.isNotBlank() && suggestions.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestions, key = { it }) { term ->
                    Box(
                        Modifier
                            .glass(CircleShape)
                            .clickable { viewModel.useSuggestion(term) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = term,
                            style = MaterialTheme.typography.labelMedium,
                            color = NodesWhite,
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // The mini player floats over this screen too, so leave room for it.
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            state.error?.let { error ->
                item(key = "error") {
                    EmptyPanel(
                        title = stringResource(R.string.source_error),
                        description = sourceErrorMessage(error),
                    )
                }
            }

            items(items = state.results, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    onClick = { playerViewModel.play(track, state.results) },
                    trailing = {
                        TrackMenuButton(
                            onPlayNext = { playerViewModel.playNext(track) },
                            onAddToQueue = { playerViewModel.addToQueue(track) },
                        )
                    },
                )
            }

            if (state.searched && !state.loading && state.results.isEmpty() && state.error == null) {
                item(key = "empty") {
                    EmptyPanel(
                        title = stringResource(R.string.search_title),
                        description = stringResource(R.string.search_no_results, state.query),
                    )
                }
            }

            if (state.query.isBlank() && recent.isEmpty()) {
                item(key = "idle") {
                    EmptyPanel(
                        title = stringResource(R.string.search_title),
                        description = stringResource(R.string.search_idle),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .glass(CircleShape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = NodesGrey,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NodesGrey,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = NodesWhite),
                cursorBrush = SolidColor(NodesTeal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = NodesGrey,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onQueryChange("") },
            )
        }
    }
}
