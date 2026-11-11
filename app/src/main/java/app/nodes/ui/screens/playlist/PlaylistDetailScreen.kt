/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.playlist

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nodes.R
import app.nodes.core.data.LibraryRepository
import app.nodes.core.model.Playlist
import app.nodes.core.model.SourceErrorType
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.SourceRegistry
import app.nodes.ui.components.Artwork
import app.nodes.ui.components.CircleIconButton
import app.nodes.ui.components.DetailTopBar
import app.nodes.ui.components.EmptyState
import app.nodes.ui.components.ErrorState
import app.nodes.ui.components.LoadingRows
import app.nodes.ui.components.MetaRow
import app.nodes.ui.components.PillButton
import app.nodes.ui.components.PillStyle
import app.nodes.ui.components.SongRow
import app.nodes.ui.components.TintedBackdrop
import app.nodes.ui.components.TrackMenuButton
import app.nodes.ui.components.runtimeLabel
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesSpacing
import app.nodes.ui.theme.NodesType
import app.nodes.ui.theme.NodesWhite
import app.nodes.ui.theme.rememberArtworkTint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class PlaylistUiState(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = true,
    val error: SourceErrorType? = null,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val registry: SourceRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()

    val likedIds: StateFlow<Set<String>> = library.likedTracks
        .map { tracks -> tracks.mapTo(HashSet()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun load(playlistId: String) {
        viewModelScope.launch {
            _state.value = PlaylistUiState(loading = true)
            val detail = library.playlistDetail(playlistId)
            if (detail != null) {
                _state.value = PlaylistUiState(
                    playlist = detail.playlist,
                    tracks = detail.tracks,
                    loading = false,
                )
                return@launch
            }

            // Not a local playlist, so it is a YouTube Music album, playlist or
            // the liked-songs list: load it through the provider.
            when (val result = registry.byId(SourceId.YOUTUBE_MUSIC)?.getPlaylist(playlistId)) {
                is SourceResult.Success -> _state.value = PlaylistUiState(
                    playlist = result.data.playlist,
                    tracks = result.data.tracks,
                    loading = false,
                )

                is SourceResult.Failure -> _state.value = PlaylistUiState(
                    loading = false,
                    error = result.type,
                )

                null -> _state.value = PlaylistUiState(
                    loading = false,
                    error = SourceErrorType.UNKNOWN,
                )
            }
        }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch { library.toggleLike(track) }
    }

    fun setAllLiked(liked: Boolean) {
        val tracks = _state.value.tracks
        viewModelScope.launch {
            tracks.forEach { library.setLiked(it, liked) }
        }
    }
}

/**
 * A collection page whose colour comes from its cover. The cover, title and
 * actions scroll as one header; the song list below stays bare and readable on
 * the tinted wash.
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val tracks = state.tracks
    val playlist = state.playlist
    val title = playlist?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.playlist_kicker)
    val artworkUri = playlist?.artworkUri ?: tracks.firstOrNull()?.artworkUri
    val tint = rememberArtworkTint(artworkUri, title)
    val allLiked = tracks.isNotEmpty() && tracks.all { it.id in likedIds }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) { viewModel.load(playlistId) }

    val playAll: () -> Unit = {
        tracks.firstOrNull()?.let { playerViewModel.play(it, tracks) }
    }
    val shuffle: () -> Unit = {
        val shuffled = tracks.shuffled()
        shuffled.firstOrNull()?.let { playerViewModel.play(it, shuffled) }
    }
    val addAllToQueue: () -> Unit = {
        tracks.forEach(playerViewModel::addToQueue)
    }

    TintedBackdrop(tint = tint) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = NodesSpacing.dockSpace),
        ) {
            item(key = "top") {
                DetailTopBar(
                    onBack = onBack,
                    actions = {
                        Box {
                            CircleIconButton(
                                icon = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.playlist_more),
                                onClick = { menuOpen = true },
                            )
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                PlaylistMenuItem(
                                    label = stringResource(R.string.playlist_play_all),
                                    icon = Icons.Rounded.PlayArrow,
                                    enabled = tracks.isNotEmpty(),
                                ) {
                                    menuOpen = false
                                    playAll()
                                }
                                PlaylistMenuItem(
                                    label = stringResource(R.string.playlist_shuffle),
                                    icon = Icons.Rounded.Shuffle,
                                    enabled = tracks.isNotEmpty(),
                                ) {
                                    menuOpen = false
                                    shuffle()
                                }
                                PlaylistMenuItem(
                                    label = stringResource(R.string.player_add_to_queue),
                                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    enabled = tracks.isNotEmpty(),
                                ) {
                                    menuOpen = false
                                    addAllToQueue()
                                }
                                PlaylistMenuItem(
                                    label = stringResource(
                                        if (allLiked) {
                                            R.string.action_unstar
                                        } else {
                                            R.string.action_star
                                        },
                                    ),
                                    icon = if (allLiked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    enabled = tracks.isNotEmpty(),
                                ) {
                                    menuOpen = false
                                    viewModel.setAllLiked(!allLiked)
                                }
                            }
                        }
                    },
                )
            }

            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NodesSpacing.gutter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(10.dp))
                    Artwork(
                        artworkUri = artworkUri,
                        title = title,
                        modifier = Modifier.size(196.dp),
                        shape = RoundedCornerShape(26.dp),
                        targetPx = 900,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.playlist_kicker).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NodesGrey,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = NodesWhite,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    MetaRow(
                        parts = listOf(
                            stringResource(R.string.playlist_track_count, tracks.size),
                            runtimeLabel(tracks.sumOf { it.durationMs }),
                            playlist?.ownerName?.takeIf { it.isNotBlank() }?.let {
                                stringResource(R.string.playlist_featuring, it)
                            }.orEmpty(),
                            playlist?.createdAt?.let { createdAt ->
                                val date = remember(createdAt) {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM)
                                        .format(Date(createdAt))
                                }
                                stringResource(R.string.playlist_created, date)
                            }.orEmpty(),
                        ),
                    )
                    val description = playlist?.description?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.playlist_description_empty)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NodesGrey,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }

            if (tracks.isNotEmpty()) {
                item(key = "actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NodesSpacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PillButton(
                            text = stringResource(R.string.playlist_play_all),
                            onClick = playAll,
                            icon = Icons.Rounded.PlayArrow,
                            modifier = Modifier.weight(1f),
                        )
                        PillButton(
                            text = stringResource(R.string.playlist_shuffle),
                            onClick = shuffle,
                            icon = Icons.Rounded.Shuffle,
                            style = PillStyle.Glass,
                        )
                        CircleIconButton(
                            icon = if (allLiked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = stringResource(
                                if (allLiked) R.string.action_unstar else R.string.action_star,
                            ),
                            onClick = { viewModel.setAllLiked(!allLiked) },
                            tint = if (allLiked) NodesWhite else NodesGrey,
                            filled = true,
                        )
                        CircleIconButton(
                            icon = Icons.Rounded.Download,
                            contentDescription = stringResource(
                                R.string.action_download_unavailable,
                            ),
                            onClick = {},
                            enabled = false,
                            filled = true,
                        )
                    }
                }

                item(key = "tracks-head") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = NodesSpacing.gutter,
                                end = NodesSpacing.gutter,
                                top = 22.dp,
                                bottom = 6.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.playlist_tracks).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = NodesGrey,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.playlist_track_count, tracks.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = NodesGrey,
                        )
                    }
                }

                itemsIndexed(items = tracks, key = { _, track -> track.id }) { index, track ->
                    SongRow(
                        track = track,
                        onClick = { playerViewModel.play(track, tracks) },
                        position = index + 1,
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
                                onViewAlbum = track.album?.takeIf { it.isNotBlank() }?.let {
                                    { onOpenAlbum(it) }
                                },
                            )
                        },
                    )
                }
            }

            when {
                state.loading && tracks.isEmpty() -> item(key = "loading") {
                    LoadingRows()
                }

                state.error != null && tracks.isEmpty() -> item(key = "error") {
                    ErrorState(
                        error = state.error,
                        onRetry = { viewModel.load(playlistId) },
                    )
                }

                !state.loading && tracks.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        title = title,
                        description = stringResource(R.string.playlist_load_failed),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = { viewModel.load(playlistId) },
                    )
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun PlaylistMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) NodesGrey else NodesGrey.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp),
            )
        },
        enabled = enabled,
        onClick = onClick,
    )
}
