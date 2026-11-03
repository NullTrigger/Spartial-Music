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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.nodes.ui.components.TrackMenuButton
import app.nodes.ui.components.glass
import app.nodes.ui.components.sourceErrorMessage
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.GlassBorder
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
 * Playlist screen, "Aurora" direction.
 *
 * A full-bleed generated cover fills the top of the screen and the oversized
 * title sits on its lowest third, above a teal Play-all pill and a glass
 * shuffle button. The queue itself is deliberately bare: numbered rows with a
 * hairline inset under the artwork, the active track carrying a teal rail, a
 * tinted wash and a live equaliser. At the bottom, the floating dock mirrors
 * the MiniPlayer glass: while something is playing it becomes a now-playing
 * bar with a progress hairline, pause and next; otherwise it stays a plain
 * Play-all bar.
 *
 * The backdrop is gradient film only - no `Modifier.blur`, which API 26 cannot
 * afford - matching Glass.kt.
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit = {},
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val tracks = state.tracks

    LaunchedEffect(playlistId) { viewModel.load(playlistId) }

    val runtimeMs = tracks.sumOf { it.durationMs }
    val currentId = playerState.current?.id
    val playAll: () -> Unit = {
        tracks.firstOrNull()?.let { playerViewModel.play(it, tracks) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        AuroraBackdrop(Modifier.matchParentSize())

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 132.dp),
        ) {
            item(key = "hero") {
                Column {
                    AuroraHero(
                        title = state.title,
                        trackCount = tracks.size,
                        runtimeMs = runtimeMs,
                        onBack = onBack,
                        onPlayAll = playAll,
                        onShuffle = {
                            val shuffled = tracks.shuffled()
                            shuffled.firstOrNull()?.let { playerViewModel.play(it, shuffled) }
                        },
                    )
                    if (tracks.isNotEmpty()) {
                        AuroraActions(
                            onPlayAll = playAll,
                            onShuffle = {
                                val shuffled = tracks.shuffled()
                                shuffled.firstOrNull()?.let { playerViewModel.play(it, shuffled) }
                            },
                        )
                    }
                }
            }

            if (tracks.isNotEmpty()) {
                item(key = "tracks-head") {
                    AuroraListHeader(trackCount = tracks.size)
                }
                itemsIndexed(items = tracks, key = { _, track -> track.id }) { index, track ->
                    AuroraTrackRow(
                        index = index,
                        track = track,
                        active = track.id == currentId,
                        playing = playerState.isPlaying,
                        last = index == tracks.lastIndex,
                        onClick = { playerViewModel.play(track, tracks) },
                        onPlayNext = { playerViewModel.playNext(track) },
                        onAddToQueue = { playerViewModel.addToQueue(track) },
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

        val current = playerState.current
        when {
            current != null -> NowPlayingDock(
                current = current,
                isPlaying = playerState.isPlaying,
                isBuffering = playerState.isBuffering,
                progress = playerState.progress,
                hasDuration = playerState.durationMs > 0L,
                onOpenPlayer = onOpenPlayer,
                onToggle = playerViewModel::togglePlayPause,
                onNext = playerViewModel::next,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            tracks.isNotEmpty() -> PlayAllBar(
                trackCount = tracks.size,
                runtimeMs = runtimeMs,
                startsWith = tracks.first().title,
                onClick = playAll,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Two warm film washes over near-black; purely decorative, like the mockup. */
@Composable
private fun AuroraBackdrop(modifier: Modifier = Modifier) {
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
                        colors = listOf(NodesTeal.copy(alpha = 0.14f), Color.Transparent),
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

/**
 * Full-bleed generated cover with the oversized title on its lowest third.
 * The header row floats over the top edge; the copy is bottom-aligned so long
 * artwork names never push it down.
 */
@Composable
private fun AuroraHero(
    title: String,
    trackCount: Int,
    runtimeMs: Long,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(430.dp)
            .clipToBounds(),
    ) {
        HeroArt(title = title, modifier = Modifier.matchParentSize())

        // Scrim: light veil on top (system bar contrast), heavy fade at the
        // bottom so the title always reads, plus a left-side darkening pass.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to NodesBlack.copy(alpha = 0.62f),
                        0.26f to NodesBlack.copy(alpha = 0.08f),
                        0.48f to NodesBlack.copy(alpha = 0.18f),
                        0.82f to NodesBlack.copy(alpha = 0.9f),
                        1f to NodesBlack,
                    ),
                ),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(NodesBlack.copy(alpha = 0.38f), Color.Transparent),
                    ),
                ),
        )

        AuroraTopBar(
            moreEnabled = true,
            onBack = onBack,
            onPlayAll = onPlayAll,
            onShuffle = onShuffle,
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 22.dp, end = 22.dp, bottom = 22.dp),
        ) {
            Text(
                text = stringResource(R.string.playlist_kicker).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = NodesTeal,
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = title.ifBlank { stringResource(R.string.playlist_kicker) },
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 38.sp,
                    lineHeight = 42.sp,
                    letterSpacing = (-1.2).sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = NodesWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            val runtime = runtimeLabel(runtimeMs)
            if (runtime.isNotEmpty()) {
                MetaDots(
                    parts = listOf(
                        stringResource(R.string.playlist_track_count, trackCount),
                        runtime,
                    ),
                )
            } else {
                Text(
                    text = stringResource(R.string.playlist_track_count, trackCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = NodesGrey,
                )
            }
        }
    }
}

/** Seeded gradient + glyph, drawn oversized and cropped by the hero box. */
@Composable
private fun HeroArt(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(NodesBlack)) {
        GeneratedArt(title = title, modifier = Modifier.matchParentSize(), showInitial = false)

        // Halo plus two concentric rings, upper right, over the gradient.
        Canvas(Modifier.matchParentSize()) {
            val haloCenter = Offset(size.width * 0.87f, size.height * 0.47f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NodesTeal.copy(alpha = 0.5f),
                        NodesTeal.copy(alpha = 0.1f),
                        Color.Transparent,
                    ),
                    center = haloCenter,
                    radius = size.width * 0.47f,
                ),
                center = haloCenter,
                radius = size.width * 0.47f,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.13f),
                center = Offset(size.width * 0.83f, size.height * 0.71f),
                radius = size.width * 0.57f,
                style = Stroke(1.dp.toPx()),
            )
            drawCircle(
                color = NodesTeal.copy(alpha = 0.22f),
                center = Offset(size.width * 0.83f, size.height * 0.71f),
                radius = size.width * 0.35f,
                style = Stroke(1.dp.toPx()),
            )
        }

        Text(
            text = title.firstOrNull()?.uppercase() ?: "N",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 220.sp,
                lineHeight = 205.sp,
                color = Color.White.copy(alpha = 0.08f),
                fontWeight = FontWeight.ExtraBold,
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 14.dp, y = 46.dp),
        )
    }
}

/** Back on the left, overflow menu on the right; floats over the artwork. */
@Composable
private fun AuroraTopBar(
    moreEnabled: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp),
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
        if (moreEnabled) {
            Spacer(Modifier.weight(1f))
            var expanded by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .size(40.dp)
                    .glass(CircleShape)
                    .clickable { expanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.cd_more),
                    tint = NodesWhite,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_play_all)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = NodesGrey,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        expanded = false
                        onPlayAll()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_shuffle)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = NodesGrey,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        expanded = false
                        onShuffle()
                    },
                )
            }
        }
    }
}

/** Rounded "10 tracks · 42 min" run, separated by hairline dots. */
@Composable
private fun MetaDots(
    parts: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                Box(
                    Modifier
                        .size(3.dp)
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

/** Teal Play-all pill plus a glass shuffle button; the survey's primary row. */
@Composable
private fun AuroraActions(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(NodesTeal, NodesTealDeep),
                    ),
                )
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

/** "TRACKS" on the left, plain count on the right, directly over the rows. */
@Composable
private fun AuroraListHeader(
    trackCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.playlist_tracks).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = NodesGreyDim,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.playlist_track_count, trackCount),
            style = MaterialTheme.typography.bodySmall,
            color = NodesGreyDim,
        )
    }
}

/**
 * A numbered queue row. The hairline under an idle row starts under the
 * artwork, the playing row keeps a teal rail, tinted wash and equaliser, and
 * nothing is drawn as a card - the list breathes on the dark backdrop.
 */
@Composable
private fun AuroraTrackRow(
    index: Int,
    track: Track,
    active: Boolean,
    playing: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                if (active) {
                    drawRect(
                        color = NodesTeal,
                        topLeft = Offset(0f, 8.dp.toPx()),
                        size = Size(2.5.dp.toPx(), size.height - 16.dp.toPx()),
                    )
                }
                if (!last && !active) {
                    drawLine(
                        color = GlassBorder,
                        start = Offset(49.dp.toPx(), size.height),
                        end = Offset(size.width - 22.dp.toPx(), size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                PlayingBars(color = NodesTeal, playing = playing)
            } else {
                Text(
                    text = (index + 1).toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelSmall,
                    color = NodesGreyDim,
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Artwork(
            artworkUri = track.artworkUri,
            title = track.title,
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(11.dp),
            targetPx = 200,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (active) NodesTeal else NodesWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = if (active) NodesTeal.copy(alpha = 0.72f) else NodesGrey,
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
        TrackMenuButton(
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
        )
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

/** Floating glass Play-all bar for when nothing is playing yet. */
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

/**
 * Floating glass now-playing dock. The artwork and title open the Now Playing
 * screen, the hairline along the top edge carries playback progress, and the
 * two round buttons mirror the mini player controls.
 */
@Composable
private fun NowPlayingDock(
    current: Track,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: Float,
    hasDuration: Boolean,
    onOpenPlayer: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 18.dp)
            .glass(RoundedCornerShape(20.dp), strong = true, scrim = 0.55f)
            .clickable(onClick = onOpenPlayer),
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (hasDuration) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(NodesWhite.copy(alpha = 0.08f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .background(NodesTeal),
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    artworkUri = current.artworkUri,
                    title = current.title,
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(9.dp),
                    targetPx = 200,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = NodesWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = current.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = NodesGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(6.dp))
                PlayingBars(color = NodesTeal, playing = isPlaying, modifier = Modifier.height(14.dp))
                Spacer(Modifier.width(8.dp))
                DockIconButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    description = if (isPlaying) {
                        stringResource(R.string.action_pause)
                    } else {
                        stringResource(R.string.action_play)
                    },
                    solid = true,
                    buffering = isBuffering,
                    onClick = onToggle,
                )
                Spacer(Modifier.width(4.dp))
                DockIconButton(
                    icon = Icons.Rounded.SkipNext,
                    description = stringResource(R.string.action_next),
                    onClick = onNext,
                )
            }
        }
    }
}

/** 34dp round control; [solid] is the teal accent version. */
@Composable
private fun DockIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    buffering: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .then(if (solid) Modifier.background(NodesTeal) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (buffering) {
            CircularProgressIndicator(
                color = NodesBlack,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (solid) NodesBlack else NodesWhite,
                modifier = Modifier.size(if (solid) 18.dp else 20.dp),
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
