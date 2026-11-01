/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.playlist

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nodes.R
import app.nodes.core.data.LibraryRepository
import app.nodes.core.model.SourceErrorType
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.SourceRegistry
import app.nodes.ui.components.Artwork
import app.nodes.ui.components.EmptyPanel
import app.nodes.ui.components.GeneratedArt
import app.nodes.ui.components.glass
import app.nodes.ui.components.sourceErrorMessage
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.GlassBorder
import app.nodes.ui.theme.GlassBorderStrong
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesGreyDim
import app.nodes.ui.theme.NodesTeal
import app.nodes.ui.theme.NodesWhite
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
    val error: SourceErrorType? = null,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val registry: SourceRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()

    fun load(playlistId: String) {
        viewModelScope.launch {
            val detail = library.playlistDetail(playlistId)
            if (detail != null) {
                _state.value = PlaylistUiState(
                    title = detail.playlist.title,
                    tracks = detail.tracks,
                    loading = false,
                )
                return@launch
            }

            // Not a local playlist, so it is a YouTube Music album, playlist or
            // the liked-songs list: load it through the provider.
            when (val result = registry.byId(SourceId.YOUTUBE_MUSIC)?.getPlaylist(playlistId)) {
                is SourceResult.Success -> _state.value = PlaylistUiState(
                    title = result.data.playlist.title,
                    tracks = result.data.tracks,
                    loading = false,
                )

                is SourceResult.Failure -> _state.value = PlaylistUiState(
                    title = "Playlist",
                    loading = false,
                    error = result.type,
                )

                null -> _state.value = PlaylistUiState(
                    title = "Playlist",
                    loading = false,
                    error = SourceErrorType.UNKNOWN,
                )
            }
        }
    }
}

/**
 * Playlist screen, "cover + glass ledger" direction.
 *
 * A generated cover card and the aggregate runtime come first, then the whole
 * queue lives inside one frosted slab: the first row rounds the top, the last
 * row rounds the bottom, and the side hairlines are drawn per row so the slab
 * reads as a single surface while the list stays lazy. The active track keeps a
 * teal rail, a tinted wash and a small equaliser, and a floating Play-all bar
 * mirrors the MiniPlayer's glass so the primary action never hides a row.
 *
 * The backdrop is gradient film only - no `Modifier.blur`, which API 26 cannot
 * afford - matching Glass.kt.
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val tracks = state.tracks

    LaunchedEffect(playlistId) { viewModel.load(playlistId) }

    val runtimeMs = tracks.sumOf { it.durationMs }
    val currentId = playerState.current?.id

    Box(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        LedgerBackdrop(Modifier.matchParentSize())

        Column(Modifier.fillMaxSize()) {
            LedgerBar(onBack = onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 122.dp),
            ) {
                item(key = "cover") {
                    PlaylistCover(
                        title = state.title,
                        trackCount = tracks.size,
                        runtimeMs = runtimeMs,
                    )
                }

                if (tracks.isNotEmpty()) {
                    item(key = "ledger-head") {
                        LedgerHeader(trackCount = tracks.size)
                    }
                    itemsIndexed(items = tracks, key = { _, track -> track.id }) { index, track ->
                        LedgerRow(
                            track = track,
                            active = track.id == currentId,
                            playing = playerState.isPlaying,
                            last = index == tracks.lastIndex,
                            onClick = { playerViewModel.play(track, tracks) },
                        )
                    }
                } else if (!state.loading) {
                    item(key = "empty") {
                        EmptyPanel(
                            title = state.title,
                            description = state.error
                                ?.let { sourceErrorMessage(it) }
                                ?: stringResource(R.string.playlist_load_failed),
                        )
                    }
                }
            }
        }

        if (tracks.isNotEmpty()) {
            PlayAllBar(
                trackCount = tracks.size,
                runtimeMs = runtimeMs,
                startsWith = tracks.first().title,
                onClick = { playerViewModel.play(tracks.first(), tracks) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Two warm film washes over near-black; purely decorative, like the mockup. */
@Composable
private fun LedgerBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.background(NodesBlack)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF5A3B57).copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(520f, -80f),
                        radius = 900f,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(NodesTeal.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(70f, 40f),
                        radius = 760f,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, NodesBlack.copy(alpha = 0.4f), NodesBlack),
                        startY = 640f,
                    ),
                ),
        )
    }
}

@Composable
private fun LedgerBar(onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
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
}

@Composable
private fun PlaylistCover(
    title: String,
    trackCount: Int,
    runtimeMs: Long,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 20.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            Modifier
                .size(152.dp)
                .shadow(24.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp)),
        ) {
            GeneratedArt(title = title, modifier = Modifier.fillMaxSize())
            // Drawn as a child, so the hairline sits on top of the gradient
            // instead of being painted over by GeneratedArt's background.
            Box(
                Modifier
                    .fillMaxSize()
                    .border(1.dp, GlassBorderStrong, RoundedCornerShape(22.dp)),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.playlist_kicker).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = NodesTeal,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = NodesWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.playlist_track_count, trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = NodesGrey,
            )
            val runtime = runtimeLabel(runtimeMs)
            if (runtime.isNotEmpty()) {
                Text(
                    text = runtime,
                    style = MaterialTheme.typography.bodySmall,
                    color = NodesGrey,
                )
            }
        }
    }
}

@Composable
private fun LedgerHeader(trackCount: Int) {
    Row(
        Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .clip(SlabTopShape)
            .background(SlabFill)
            .border(1.dp, GlassBorder, SlabTopShape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.playlist_up_next).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = NodesGrey,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.playlist_track_count, trackCount),
            style = MaterialTheme.typography.bodySmall,
            color = NodesGreyDim,
        )
    }
}

@Composable
private fun LedgerRow(
    track: Track,
    active: Boolean,
    playing: Boolean,
    last: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .then(if (last) Modifier.clip(SlabBottomShape) else Modifier)
            .background(SlabFill)
            .then(
                if (active) {
                    Modifier.background(
                        Brush.horizontalGradient(
                            listOf(NodesTeal.copy(alpha = 0.16f), Color.Transparent),
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .drawBehind {
                val hairline = 1.dp.toPx()
                drawLine(
                    color = GlassBorder,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = hairline,
                )
                drawLine(
                    color = GlassBorder,
                    start = Offset(size.width - hairline / 2f, 0f),
                    end = Offset(size.width - hairline / 2f, size.height),
                    strokeWidth = hairline,
                )
                if (last) {
                    drawLine(
                        color = GlassBorder,
                        start = Offset(0f, size.height - hairline / 2f),
                        end = Offset(size.width, size.height - hairline / 2f),
                        strokeWidth = hairline,
                    )
                }
                if (active) {
                    // Drawn, not laid out, so switching tracks never shifts the row.
                    drawRect(
                        color = NodesTeal,
                        size = Size(2.5.dp.toPx(), size.height),
                    )
                }
            }
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            artworkUri = track.artworkUri,
            title = track.title,
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(11.dp),
            targetPx = 200,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (active) NodesTeal else NodesWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (active) {
                    Spacer(Modifier.width(7.dp))
                    PlayingBars(color = NodesTeal, playing = playing)
                }
            }
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = if (active) NodesTeal.copy(alpha = 0.7f) else NodesGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.durationMs > 0L) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = app.nodes.core.util.formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = NodesGreyDim,
            )
        }
    }
}

/** Three bars that keep the row alive while the track is actually playing. */
@Composable
private fun PlayingBars(
    color: Color,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    // The transition is only created while playing, so a paused row is not
    // recomposing every frame for bars nobody is watching.
    if (playing) AnimatedBars(color = color, modifier = modifier) else StaticBars(color = color, modifier = modifier)
}

@Composable
private fun AnimatedBars(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "playing-bars")
    val heights = BarSeeds.mapIndexed { index, seed ->
        val value by transition.animateFloat(
            initialValue = seed * 0.45f,
            targetValue = seed,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 560 + index * 150,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "playing-bar-$index",
        )
        value
    }
    BarRow(heights = heights, color = color, modifier = modifier)
}

@Composable
private fun StaticBars(color: Color, modifier: Modifier = Modifier) {
    BarRow(heights = BarSeeds, color = color, modifier = modifier)
}

@Composable
private fun BarRow(
    heights: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        heights.forEach { height ->
            Box(
                Modifier
                    .width(2.5.dp)
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

private val BarSeeds = listOf(0.45f, 1f, 0.65f)

@Composable
private fun PlayAllBar(
    trackCount: Int,
    runtimeMs: Long,
    startsWith: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 18.dp)
            .glass(RoundedCornerShape(22.dp), strong = true)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(NodesTeal),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = NodesBlack,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.playlist_play_all),
                style = MaterialTheme.typography.titleSmall,
                color = NodesWhite,
            )
            Text(
                text = stringResource(R.string.playlist_starts_with, startsWith),
                style = MaterialTheme.typography.bodySmall,
                color = NodesGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val runtime = runtimeLabel(runtimeMs)
        if (runtime.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = runtime,
                style = MaterialTheme.typography.labelSmall,
                color = NodesGreyDim,
            )
        } else {
            Text(
                text = stringResource(R.string.playlist_track_count, trackCount),
                style = MaterialTheme.typography.labelSmall,
                color = NodesGreyDim,
            )
        }
    }
}

/** "1 hr 42 min", "48 min"; empty when the durations are unknown. */
@Composable
private fun runtimeLabel(runtimeMs: Long): String {
    if (runtimeMs <= 0L) return ""
    val totalMinutes = runtimeMs / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        totalMinutes <= 0L -> ""
        hours <= 0L -> stringResource(R.string.playlist_runtime_minutes, minutes)
        minutes == 0L -> stringResource(R.string.playlist_runtime_hours, hours)
        else -> stringResource(R.string.playlist_runtime_hours_minutes, hours, minutes)
    }
}

private val SlabTopShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
private val SlabBottomShape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)

/** One flat film for every slab row, so the list reads as a single surface. */
private val SlabFill = Color.White.copy(alpha = 0.07f)
