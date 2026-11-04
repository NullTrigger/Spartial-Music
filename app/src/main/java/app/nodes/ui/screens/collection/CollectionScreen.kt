/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.collection

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nodes.R
import app.nodes.core.data.LibraryRepository
import app.nodes.core.model.Track
import app.nodes.ui.components.EmptyPanel
import app.nodes.ui.components.Artwork
import app.nodes.ui.components.GeneratedArt
import app.nodes.ui.components.TrackMenuButton
import app.nodes.ui.components.TrackRow
import app.nodes.ui.components.glass
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesGreyDim
import app.nodes.ui.theme.NodesTeal
import app.nodes.ui.theme.NodesTealDeep
import app.nodes.ui.theme.NodesWhite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}

/**
 * An artist or album page.
 *
 * The hero is the same generated-cover language as the playlist screen: a
 * seeded gradient, a teal kicker naming the kind, the oversized title over a
 * bottom scrim, the teal Play-all pill and the glass shuffle button. Below it
 * the bare list of every cached track from that artist or album. Nothing is
 * personalised or fetched from a provider here: it is a Room query.
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
    val tracks = state.tracks
    val playAll: () -> Unit = {
        tracks.firstOrNull()?.let { playerViewModel.play(it, tracks) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "hero") {
                CollectionHero(
                    kind = viewModel.kind,
                    title = viewModel.name,
                    artworkUri = tracks.firstOrNull()?.artworkUri,
                    trackCount = tracks.size,
                    runtimeMs = tracks.sumOf { it.durationMs },
                    onBack = onBack,
                )
                CollectionActions(
                    enabled = tracks.isNotEmpty(),
                    onPlayAll = playAll,
                    onShuffle = {
                        val shuffled = tracks.shuffled()
                        shuffled.firstOrNull()?.let { playerViewModel.play(it, shuffled) }
                    },
                )
            }

            if (tracks.isNotEmpty()) {
                itemsIndexed(items = tracks, key = { _, track -> track.id }) { _, track ->
                    TrackRow(
                        track = track,
                        onClick = { playerViewModel.play(track, tracks) },
                        trailing = {
                            TrackMenuButton(
                                onPlayNext = { playerViewModel.playNext(track) },
                                onAddToQueue = { playerViewModel.addToQueue(track) },
                                onViewArtist = if (track.artist.isNotBlank() && track.artist != viewModel.name) {
                                    { onOpenArtist(track.artist) }
                                } else {
                                    null
                                },
                                onViewAlbum = if (track.album != null && track.album != viewModel.name) {
                                    { onOpenAlbum(track.album) }
                                } else {
                                    null
                                },
                            )
                        },
                    )
                }
            } else if (!state.loading) {
                item(key = "empty") {
                    EmptyPanel(
                        title = viewModel.name.ifBlank {
                            stringResource(R.string.collection_none)
                        },
                        description = stringResource(R.string.collection_empty),
                    )
                }
            }
        }
    }
}

/** Full-bleed generated cover with kicker, title and a tracks/runtime meta row. */
@Composable
private fun CollectionHero(
    kind: CollectionKind,
    title: String,
    artworkUri: String?,
    trackCount: Int,
    runtimeMs: Long,
    onBack: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(268.dp),
    ) {
        if (artworkUri.isNullOrBlank()) {
            // No track artwork for this artist: fall back to the seeded
            // gradient and show the artist's initial so the hero isn't blank.
            GeneratedArt(
                title = title,
                modifier = Modifier
                    .fillMaxSize()
                    .background(NodesBlack),
                showInitial = true,
            )
        } else {
            Artwork(
                artworkUri = artworkUri,
                title = title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
                targetPx = 1080,
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to NodesBlack.copy(alpha = 0.62f),
                        0.4f to NodesBlack.copy(alpha = 0.14f),
                        0.82f to NodesBlack.copy(alpha = 0.9f),
                        1f to NodesBlack,
                    ),
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, top = 6.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .glass(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = NodesWhite,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 22.dp, end = 22.dp, bottom = 20.dp),
        ) {
            Text(
                text = stringResource(
                    if (kind == CollectionKind.ARTIST) {
                        R.string.collection_kicker_artist
                    } else {
                        R.string.collection_kicker_album
                    },
                ).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = NodesTeal,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title.ifBlank { stringResource(R.string.collection_none) },
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    letterSpacing = (-0.9).sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = NodesWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            val parts = buildList {
                add(stringResource(R.string.collection_track_count, trackCount))
                val runtime = runtimeLabel(runtimeMs)
                if (runtime.isNotEmpty()) add(runtime)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                parts.forEachIndexed { index, part ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .size(3.dp)
                                .align(Alignment.CenterVertically)
                                .clip(CircleShape)
                                .background(NodesGreyDim),
                        )
                    }
                    Text(
                        text = part,
                        style = MaterialTheme.typography.bodySmall,
                        color = NodesGrey,
                    )
                }
            }
        }
    }
}

/** Teal Play-all pill plus the glass shuffle button, once the list has rows. */
@Composable
private fun CollectionActions(
    enabled: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    if (!enabled) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.verticalGradient(listOf(NodesTeal, NodesTealDeep)))
                .clickable(onClick = onPlayAll),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = NodesBlack,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.playlist_play_all),
                    style = MaterialTheme.typography.titleSmall,
                    color = NodesBlack,
                )
            }
        }
        Box(
            Modifier
                .size(50.dp)
                .glass(CircleShape)
                .clickable(onClick = onShuffle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.playlist_shuffle),
                tint = NodesWhite,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Reuses the playlist runtime wording; the displayed text is identical. */
@Composable
private fun runtimeLabel(runtimeMs: Long): String {
    if (runtimeMs <= 0L) return ""
    val totalMinutes = runtimeMs / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours <= 0L -> stringResource(R.string.playlist_runtime_minutes, minutes)
        minutes == 0L -> stringResource(R.string.playlist_runtime_hours, hours)
        else -> stringResource(R.string.playlist_runtime_hours_minutes, hours, minutes)
    }
}
