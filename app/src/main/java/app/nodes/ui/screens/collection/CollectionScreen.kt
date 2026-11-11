/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.collection

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nodes.R
import app.nodes.core.data.LibraryRepository
import app.nodes.core.model.Track
import app.nodes.ui.components.Artwork
import app.nodes.ui.components.CircleIconButton
import app.nodes.ui.components.DetailTopBar
import app.nodes.ui.components.EmptyState
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
import javax.inject.Inject

/**
 * Which collection a [CollectionScreen] shows. Both are plain Room joins over
 * the cached tracks table - the same data search and playlists read.
 */
enum class CollectionKind(val key: String) {
    ARTIST("artist"),
    ALBUM("album"),
    ;

    companion object {
        fun from(key: String): CollectionKind =
            entries.firstOrNull { it.key == key } ?: ARTIST
    }
}

data class CollectionUiState(
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = true,
)

/**
 * One ViewModel serves both kinds: the route carries which one it is, the
 * repository picks the matching column.
 */
@HiltViewModel
class CollectionViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val library: LibraryRepository,
) : ViewModel() {

    val kind: CollectionKind = CollectionKind.from(saved.get<String>("kind") ?: "artist")
    val name: String = saved.get<String>("name").orEmpty()

    private val _state = MutableStateFlow(CollectionUiState())
    val state: StateFlow<CollectionUiState> = _state.asStateFlow()

    val likedIds: StateFlow<Set<String>> = library.likedTracks
        .map { tracks -> tracks.mapTo(HashSet()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            val flow = if (kind == CollectionKind.ARTIST) {
                library.artistTracks(name)
            } else {
                library.albumTracks(name)
            }
            flow.collect { tracks ->
                _state.value = CollectionUiState(tracks = tracks, loading = false)
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
 * An artist or album page. Both reuse the artwork tint, a cover, a compact
 * action row and a bare list of every cached track. Nothing is fetched here:
 * this is a Room query over music the app has already seen.
 */
@Composable
fun CollectionScreen(
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    viewModel: CollectionViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val tracks = state.tracks
    val title = viewModel.name.ifBlank { stringResource(R.string.collection_none) }
    val artworkUri = tracks.firstOrNull()?.artworkUri
    val tint = rememberArtworkTint(artworkUri, title)
    val allLiked = tracks.isNotEmpty() && tracks.all { it.id in likedIds }

    val playAll: () -> Unit = {
        tracks.firstOrNull()?.let { playerViewModel.play(it, tracks) }
    }
    val shuffle: () -> Unit = {
        val shuffled = tracks.shuffled()
        shuffled.firstOrNull()?.let { playerViewModel.play(it, shuffled) }
    }

    TintedBackdrop(tint = tint) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = NodesSpacing.dockSpace),
        ) {
            item(key = "top") {
                DetailTopBar(onBack = onBack)
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
                        modifier = Modifier.size(if (viewModel.kind == CollectionKind.ARTIST) 156.dp else 190.dp),
                        shape = if (viewModel.kind == CollectionKind.ARTIST) {
                            CircleShape
                        } else {
                            RoundedCornerShape(26.dp)
                        },
                        targetPx = 900,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = stringResource(
                            if (viewModel.kind == CollectionKind.ARTIST) {
                                R.string.collection_kicker_artist
                            } else {
                                R.string.collection_kicker_album
                            },
                        ).uppercase(),
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
                        parts = when (viewModel.kind) {
                            CollectionKind.ARTIST -> listOf(
                                stringResource(R.string.artist_songs_count, tracks.size),
                            )

                            CollectionKind.ALBUM -> listOf(
                                tracks.firstOrNull()?.artist.orEmpty(),
                                stringResource(R.string.collection_track_count, tracks.size),
                                runtimeLabel(tracks.sumOf { it.durationMs }),
                            )
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = when (viewModel.kind) {
                            CollectionKind.ARTIST -> stringResource(R.string.artist_bio)
                            CollectionKind.ALBUM -> stringResource(
                                R.string.album_description,
                                tracks.firstOrNull()?.artist ?: title,
                            )
                        },
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
                                if (viewModel.kind == CollectionKind.ARTIST) {
                                    R.string.artist_star
                                } else {
                                    if (allLiked) R.string.action_unstar else R.string.action_star
                                },
                            ),
                            onClick = { viewModel.setAllLiked(!allLiked) },
                            tint = if (allLiked) NodesWhite else NodesGrey,
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
                            text = stringResource(
                                if (viewModel.kind == CollectionKind.ARTIST) {
                                    R.string.artist_top_songs
                                } else {
                                    R.string.playlist_tracks
                                },
                            ).uppercase(),
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
                        supportingText = if (viewModel.kind == CollectionKind.ARTIST) {
                            track.album ?: track.artist
                        } else {
                            null
                        },
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
                                onViewArtist = if (track.artist.isNotBlank() &&
                                    track.artist != viewModel.name
                                ) {
                                    { onOpenArtist(track.artist) }
                                } else {
                                    null
                                },
                                onViewAlbum = track.album?.takeIf {
                                    it.isNotBlank() && it != viewModel.name
                                }?.let { album ->
                                    { onOpenAlbum(album) }
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

                !state.loading && tracks.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        title = title,
                        description = stringResource(R.string.collection_empty),
                    )
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(8.dp)) }
        }
    }
}
