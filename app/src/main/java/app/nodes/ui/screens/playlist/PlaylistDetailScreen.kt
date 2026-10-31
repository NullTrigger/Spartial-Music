/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nodes.R
import app.nodes.core.data.LibraryRepository
import app.nodes.core.model.Track
import app.nodes.ui.theme.NodesWhite
import app.nodes.ui.components.EmptyPanel
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.TrackRow
import app.nodes.ui.components.glass
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.NodesBlack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val title: String = "",
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val library: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()

    fun load(playlistId: String) {
        viewModelScope.launch {
            val detail = library.playlistDetail(playlistId)
            _state.value = PlaylistUiState(
                title = detail?.playlist?.title ?: "Playlist",
                tracks = detail?.tracks.orEmpty(),
                loading = false,
            )
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(playlistId) { viewModel.load(playlistId) }

    Column(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        PageHeader(
            title = state.title,
            subtitle = "${state.tracks.size} tracks",
            back = onBack,
        )

        if (state.tracks.isNotEmpty()) {
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .glass(CircleShape)
                    .clickable { playerViewModel.play(state.tracks.first(), state.tracks) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = NodesWhite,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_play),
                    style = MaterialTheme.typography.labelLarge,
                    color = NodesWhite,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            items(items = state.tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    onClick = { playerViewModel.play(track, state.tracks) },
                )
            }
            if (!state.loading && state.tracks.isEmpty()) {
                item(key = "empty") {
                    EmptyPanel(
                        title = state.title,
                        description = stringResource(R.string.home_empty),
                    )
                }
            }
        }
    }
}
