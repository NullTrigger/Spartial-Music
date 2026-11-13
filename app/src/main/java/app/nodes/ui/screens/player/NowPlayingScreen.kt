/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.player

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.data.LyricsResult
import app.nodes.core.model.LyricLine
import app.nodes.core.model.SourceId
import app.nodes.core.model.Track
import app.nodes.core.util.formatDuration
import app.nodes.playback.PlayerUiState
import app.nodes.playback.RepeatMode
import app.nodes.ui.components.Artwork
import app.nodes.ui.components.CastRouteButton
import app.nodes.ui.components.CircleIconButton
import app.nodes.ui.components.EmptyState
import app.nodes.ui.components.GlassBadge
import app.nodes.ui.components.KeepScreenOn
import app.nodes.ui.components.LoadingRows
import app.nodes.ui.components.PlayerBackdrop
import app.nodes.ui.components.SongRow
import app.nodes.ui.components.TrackMenuButton
import app.nodes.ui.components.glass
import app.nodes.ui.components.trackSupportingLine
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesGreyDim
import app.nodes.ui.theme.NodesSpacing
import app.nodes.ui.theme.NodesSurface
import app.nodes.ui.theme.NodesType
import app.nodes.ui.theme.NodesWhite
import app.nodes.ui.theme.rememberArtworkTint
import app.nodes.ui.theme.toPlayerAccent
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerCallback
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class PlayerMode { CONTROLS, LYRICS, QUEUE }

/**
 * Full Now Playing, built around the three views in the reference design:
 *
 *  - **Controls** — the cover fills the upper screen and dissolves into a
 *    saturated colour wash derived from its tint, with the title, actions,
 *    progress and transport sitting over that gradient.
 *  - **Lyrics** — the current line is sharp and white; upcoming lines are
 *    progressively blurred and dimmed so the eye stays on the line being sung.
 *    The backdrop is the same clean gradient, never a stretched copy of the
 *    cover.
 *  - **Queue** — “Playing from”, the next rows and their artwork menus.
 *
 * All three share one bottom dock, so the transport never moves. The screen has
 * three internal modes rather than three navigation routes. The embedded
 * YouTube player stays composed above every mode; leaving Now Playing pauses it
 * and drops the handle (YOUTUBE.md).
 *
 * The surface behind everything is a smooth gradient built from the artwork's
 * dominant colour, sampled locally by [rememberArtworkTint]; see
 * [PlayerBackdrop]. API 26 has no RenderEffect, and the design no longer needs
 * one: the wash is pure geometry, identical on every device.
 */
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    viewModel: NowPlayingViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val lyricsLoading by viewModel.lyricsLoading.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val lyricsAutoScroll by viewModel.lyricsAutoScroll.collectAsStateWithLifecycle()
    val track = playerState.current

    KeepScreenOn(keepScreenOn)

    var mode by rememberSaveable { mutableStateOf(PlayerMode.CONTROLS) }
    var followLyrics by rememberSaveable { mutableStateOf(true) }
    var showTrackOptions by remember { mutableStateOf(false) }

    LaunchedEffect(track?.id) {
        viewModel.onTrackChanged(track)
        followLyrics = true
    }

    BackHandler(enabled = mode != PlayerMode.CONTROLS) { mode = PlayerMode.CONTROLS }

    if (track == null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(NodesBlack)
                .statusBarsPadding()
                .padding(NodesSpacing.gutter),
        ) {
            CircleIconButton(
                icon = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.action_close),
                onClick = onBack,
            )
            EmptyState(
                title = stringResource(R.string.player_queue_empty_title),
                description = stringResource(R.string.player_queue_empty_body),
            )
        }
        return
    }

    val tint = rememberArtworkTint(track.artworkUri, track.title)
    val sourceName = playerSourceName(track.sourceId)
    val queueTitle = track.album?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.player_playing_from_queue)
    val playAll: (List<Track>) -> Unit = { tracks ->
        tracks.firstOrNull()?.let { playerViewModel.play(it, tracks) }
    }

    // Swipe the controls page down and the player follows the finger, then
    // closes past a third of the screen or on a downward fling. Only the
    // controls page owns the gesture: Lyrics and Queue hold scrollable lists
    // that need vertical drag for themselves.
    val screenHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    val dismissScope = rememberCoroutineScope()
    val dismissOffset = remember { Animatable(0f) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val dismissDragModifier = Modifier
        .draggable(
            state = rememberDraggableState { delta ->
                if (delta > 0f || dismissOffset.value > 0f) {
                    dismissScope.launch {
                        dismissOffset.snapTo((dismissOffset.value + delta).coerceIn(0f, screenHeightPx))
                    }
                }
            },
            orientation = Orientation.Vertical,
            enabled = mode == PlayerMode.CONTROLS && !dismissed,
            startDragImmediately = dismissOffset.value > 0f,
            onDragStopped = { velocity ->
                dismissScope.launch {
                    val shouldClose = dismissOffset.value > screenHeightPx / 3f || velocity > 1_400f
                    if (shouldClose) {
                        dismissed = true
                        dismissOffset.animateTo(screenHeightPx, tween(180))
                        onBack()
                        // The screen is leaving, but reset anyway: if the pop is
                        // refused or the composition survives the transition, a
                        // stuck flag would leave the player unable to swipe.
                        dismissed = false
                        dismissOffset.snapTo(0f)
                    } else {
                        dismissOffset.animateTo(0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
                    }
                }
            },
        )
        .graphicsLayer {
            translationY = dismissOffset.value
            val progress = (dismissOffset.value / screenHeightPx).coerceIn(0f, 1f)
            scaleX = 1f - progress * 0.06f
            scaleY = 1f - progress * 0.06f
            alpha = 1f - progress * 0.35f
        }

    Box(
        Modifier
            .fillMaxSize()
            .then(if (mode == PlayerMode.CONTROLS) dismissDragModifier else Modifier),
    ) {
        PlayerBackdrop(
            artworkUri = track.artworkUri,
            title = track.title,
            tint = tint,
            showArtwork = mode == PlayerMode.CONTROLS && playerState.embeddedVideoId == null,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                PlayerTopBar(
                    mode = mode,
                    sourceName = sourceName,
                    onBack = onBack,
                    onExitMode = { mode = PlayerMode.CONTROLS },
                )

                playerState.embeddedVideoId?.let { videoId ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NodesSpacing.gutter, vertical = 6.dp),
                    ) {
                        YouTubeEmbeddedCard(
                            videoId = videoId,
                            onStop = playerViewModel::stopEmbedded,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.player_youtube_notice),
                            style = MaterialTheme.typography.labelSmall,
                            color = NodesGrey,
                        )
                    }
                }

                when (mode) {
                    PlayerMode.CONTROLS -> ControlsMode(
                        track = track,
                        modifier = Modifier.weight(1f),
                    )

                    PlayerMode.LYRICS -> LyricsMode(
                        result = lyrics,
                        loading = lyricsLoading,
                        positionMs = playerState.positionMs,
                        autoScroll = lyricsAutoScroll,
                        follow = followLyrics,
                        onFollowChanged = { followLyrics = it },
                        onRetry = viewModel::refreshLyrics,
                        modifier = Modifier.weight(1f),
                    )

                    PlayerMode.QUEUE -> QueueMode(
                        state = playerState,
                        queueTitle = queueTitle,
                        onPlayIndex = playerViewModel::playQueueIndex,
                        onMove = playerViewModel::moveInQueue,
                        onRemove = playerViewModel::removeFromQueue,
                        onPlayNext = playerViewModel::playNext,
                        onAddToQueue = playerViewModel::addToQueue,
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                        onOpenControls = { mode = PlayerMode.CONTROLS },
                        onInfinite = playerViewModel::toggleInfiniteQueue,
                        modifier = Modifier.weight(1f),
                    )
                }

                PlayerDock(
                    mode = mode,
                    state = playerState,
                    track = track,
                    sourceName = sourceName,
                    liked = liked,
                    tint = tint,
                    onToggleLike = { viewModel.toggleLike(track) },
                    onPlayPause = playerViewModel::togglePlayPause,
                    onPrevious = playerViewModel::previous,
                    onNext = playerViewModel::next,
                    onSeek = playerViewModel::seekTo,
                    onShuffle = playerViewModel::toggleShuffle,
                    onRepeat = { playerViewModel.cycleRepeat() },
                    onOpenLyrics = { mode = PlayerMode.LYRICS },
                    onOpenQueue = { mode = PlayerMode.QUEUE },
                    onOpenControls = { mode = PlayerMode.CONTROLS },
                    onAddToQueue = { playerViewModel.addToQueue(track) },
                    onMore = { showTrackOptions = true },
                )
            }
        }
    }

    if (showTrackOptions) {
        TrackOptionsSheet(
            track = track,
            liked = liked,
            onDismiss = { showTrackOptions = false },
            onViewArtist = {
                showTrackOptions = false
                if (track.artist.isNotBlank()) onOpenArtist(track.artist)
            },
            onViewAlbum = {
                showTrackOptions = false
                track.album?.takeIf { it.isNotBlank() }?.let(onOpenAlbum)
            },
            onToggleLibrary = {
                showTrackOptions = false
                viewModel.toggleLike(track)
            },
            onPlayNext = {
                showTrackOptions = false
                playerViewModel.playNext(track)
            },
            onAddToQueue = {
                showTrackOptions = false
                playerViewModel.addToQueue(track)
            },
        )
    }
}

@Composable
private fun PlayerTopBar(
    mode: PlayerMode,
    sourceName: String,
    onBack: () -> Unit,
    onExitMode: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = if (mode == PlayerMode.CONTROLS) {
                Icons.Rounded.KeyboardArrowDown
            } else {
                Icons.AutoMirrored.Rounded.ArrowBack
            },
            contentDescription = if (mode == PlayerMode.CONTROLS) {
                stringResource(R.string.action_close)
            } else {
                stringResource(R.string.cd_back)
            },
            onClick = if (mode == PlayerMode.CONTROLS) onBack else onExitMode,
            filled = true,
            background = Color.Black.copy(alpha = 0.26f),
        )
        Spacer(Modifier.weight(1f))
        if (mode == PlayerMode.CONTROLS) {
            GlassBadge(text = sourceName.uppercase())
        }
    }
}

/**
 * The stage above the dock: the full-bleed cover is already painted by
 * [PlayerBackdrop] and dissolves into the colour wash, so this carries only
 * the title block over the transition. The official card takes over when a
 * YouTube track is playing, because the API terms require that video to stay
 * visible.
 */
@Composable
private fun ControlsMode(
    track: Track,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        TrackTitleBlock(
            track = track,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp)
                .padding(bottom = 18.dp),
        )
    }
}

@Composable
private fun TrackTitleBlock(track: Track, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineMedium,
            color = NodesWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = NodesWhite.copy(alpha = 0.76f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LyricsMode(
    result: LyricsResult,
    loading: Boolean,
    positionMs: Long,
    autoScroll: Boolean,
    follow: Boolean,
    onFollowChanged: (Boolean) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (loading) {
        LoadingRows(modifier)
        return
    }
    if (result.lines.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.player_lyrics_empty_title),
            description = stringResource(R.string.player_lyrics_empty_body),
            actionLabel = stringResource(R.string.player_lyrics_retry),
            onAction = onRetry,
            modifier = modifier,
        )
        return
    }

    val listState = rememberLazyListState()
    val activeIndex = result.lines.indexOfLast {
        it.timeMs <= positionMs.coerceAtLeast(0L)
    }.coerceIn(0, result.lines.lastIndex)
    val dragging by listState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(dragging) {
        if (dragging) onFollowChanged(false)
    }
    LaunchedEffect(autoScroll, follow, activeIndex, result.lines.size) {
        if (!autoScroll || !follow) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportSize.height
        val offset = if (viewport > 0) -(viewport / 6) else 0
        try {
            listState.animateScrollToItem(activeIndex, offset)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The lyrics list can be replaced while the animation runs.
        }
    }

    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 28.dp,
                end = 28.dp,
                top = 26.dp,
                bottom = 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(
                items = result.lines,
                key = { index, line -> "lyric-$index-${line.timeMs}" },
            ) { index, line ->
                LyricLineView(
                    line = line,
                    active = index == activeIndex,
                    distance = index - activeIndex,
                )
            }
        }

        AnimatedVisibility(
            visible = autoScroll && !follow,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            androidx.compose.material3.Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
            ) {
                Text(
                    text = stringResource(R.string.player_lyrics_resync),
                    style = NodesType.pill,
                    color = NodesWhite,
                    modifier = Modifier
                        .clickable { onFollowChanged(true) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * Current line sharp; the next lines get both a growing blur (Android 12+,
 * where RenderEffect exists) and a fading alpha (every API). The alpha fallback
 * is what keeps the effect readable on Android 8–11.
 */
@Composable
private fun LyricLineView(line: LyricLine, active: Boolean, distance: Int) {
    val upcoming = distance > 0
    val alpha = when {
        active -> 1f
        distance < 0 -> 0.30f
        distance == 1 -> 0.62f
        distance == 2 -> 0.46f
        distance == 3 -> 0.33f
        distance == 4 -> 0.23f
        distance == 5 -> 0.16f
        else -> 0.10f
    }
    val blurDp = if (upcoming) (distance * 2.4f).coerceAtMost(11f) else 0f
    Text(
        text = line.text.ifBlank { "♪" },
        style = if (active) {
            NodesType.lyricActive.copy(fontWeight = FontWeight.Bold)
        } else {
            NodesType.lyricIdle
        },
        color = NodesWhite.copy(alpha = alpha),
        modifier = Modifier
            .fillMaxWidth()
            .lyricBlur(blurDp),
    )
}

private fun Modifier.lyricBlur(radiusDp: Float): Modifier = graphicsLayer {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radiusDp > 0.1f) {
        val radiusPx = radiusDp * density
        renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Decal)
    }
}

@Composable
private fun QueueMode(
    state: PlayerUiState,
    queueTitle: String,
    onPlayIndex: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenControls: () -> Unit,
    onInfinite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.queue.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.player_queue_empty_title),
            description = stringResource(R.string.player_queue_empty_body),
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item(key = "queue-head") {
            QueueHeader(
                track = state.current,
                queueTitle = queueTitle,
                infiniteQueue = state.infiniteQueue,
                onOpenControls = onOpenControls,
                onInfinite = onInfinite,
            )
            Text(
                text = stringResource(R.string.player_playing_next_label),
                style = MaterialTheme.typography.titleMedium,
                color = NodesWhite.copy(alpha = 0.92f),
                modifier = Modifier.padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 8.dp,
                    bottom = 6.dp,
                ),
            )
        }
        itemsIndexed(
            items = state.queue,
            key = { index, track -> "queue-$index-${track.id}" },
        ) { index, track ->
            QueueRow(
                track = track,
                active = index == state.queueIndex,
                playing = index == state.queueIndex && state.isPlaying,
                onClick = { onPlayIndex(index) },
                menu = {
                    TrackMenuButton(
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onViewArtist = if (track.artist.isNotBlank()) {
                            { onOpenArtist(track.artist) }
                        } else {
                            null
                        },
                        onViewAlbum = track.album?.takeIf { it.isNotBlank() }?.let { album ->
                            { onOpenAlbum(album) }
                        },
                        onRemoveFromQueue = { onRemove(index) },
                        removeEnabled = index != state.queueIndex,
                        onMoveUp = if (index > 0) {
                            { onMove(index, index - 1) }
                        } else {
                            null
                        },
                        onMoveDown = if (index < state.queue.lastIndex) {
                            { onMove(index, index + 1) }
                        } else {
                            null
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun QueueHeader(
    track: Track?,
    queueTitle: String,
    infiniteQueue: Boolean,
    onOpenControls: () -> Unit,
    onInfinite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (track != null) {
            Artwork(
                artworkUri = track.artworkUri,
                title = track.title,
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(14.dp),
                targetPx = 256,
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.player_playing_from_label),
                style = MaterialTheme.typography.bodySmall,
                color = NodesWhite.copy(alpha = 0.64f),
            )
            Text(
                text = queueTitle,
                style = NodesType.sectionTitle,
                color = NodesWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onInfinite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AllInclusive,
                contentDescription = stringResource(R.string.player_infinite),
                tint = if (infiniteQueue) NodesWhite else NodesWhite.copy(alpha = 0.52f),
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onOpenControls),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = stringResource(R.string.player_now_playing),
                tint = NodesWhite.copy(alpha = 0.72f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun QueueRow(
    track: Track,
    active: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    menu: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (active) {
                    Modifier.background(NodesWhite.copy(alpha = 0.08f))
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            artworkUri = track.artworkUri,
            title = track.title,
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(13.dp),
            targetPx = 256,
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = NodesType.rowTitle,
                color = NodesWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = trackSupportingLine(track),
                style = NodesType.rowMeta,
                color = NodesWhite.copy(alpha = 0.56f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (active && playing) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = NodesWhite,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(2.dp))
        }
        menu()
    }
}

/**
 * The shared bottom dock. Controls shows the three-icon action pill from the
 * reference player; Lyrics and Queue show the current track pill instead. The
 * progress row and the five transport controls are identical in all modes.
 */
@Composable
private fun PlayerDock(
    mode: PlayerMode,
    state: PlayerUiState,
    track: Track,
    sourceName: String,
    liked: Boolean,
    tint: Color,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenControls: () -> Unit,
    onAddToQueue: () -> Unit,
    onMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            // The reference player leaves a band of empty backdrop below the
            // transport row - 62px of a 1280px screen, so about 5% of the
            // height - so the controls read as a row that sits on the screen
            // rather than as a row glued to its bottom edge. The system
            // navigation inset is part of that band; this is the app's share
            // of it, and it lands the whole gap at roughly the same 5%.
            .padding(bottom = NodesSpacing.lg),
    ) {
        if (mode == PlayerMode.CONTROLS) {
            ControlsActionRow(
                liked = liked,
                onToggleLike = onToggleLike,
                onAddToQueue = onAddToQueue,
                onMore = onMore,
                onOpenLyrics = onOpenLyrics,
                onOpenQueue = onOpenQueue,
            )
        } else {
            ModeSelectorRow(
                mode = mode,
                track = track,
                onOpenControls = onOpenControls,
                onOpenLyrics = onOpenLyrics,
                onOpenQueue = onOpenQueue,
            )
        }

        Spacer(Modifier.height(8.dp))
        PlayerProgress(
            state = state,
            sourceName = sourceName,
            tint = tint,
            onSeek = onSeek,
        )
        Spacer(Modifier.height(4.dp))
        PlayerTransport(
            state = state,
            tint = tint,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onShuffle = onShuffle,
            onRepeat = onRepeat,
        )
    }
}

@Composable
private fun ControlsActionRow(
    liked: Boolean,
    onToggleLike: () -> Unit,
    onAddToQueue: () -> Unit,
    onMore: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCircleButton(
            icon = Icons.Rounded.Lyrics,
            contentDescription = stringResource(R.string.player_lyrics),
            onClick = onOpenLyrics,
        )
        Row(
            modifier = Modifier
                .width(188.dp)
                .height(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .glass(shape = CircleShape, strong = true),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockAction(
                icon = if (liked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = stringResource(R.string.player_like),
                tint = NodesWhite,
                onClick = onToggleLike,
            )
            DockAction(
                icon = Icons.Rounded.MusicNote,
                contentDescription = stringResource(R.string.player_add_to_queue),
                onClick = onAddToQueue,
            )
            DockAction(
                icon = Icons.Rounded.MoreHoriz,
                contentDescription = stringResource(R.string.player_more),
                onClick = onMore,
            )
        }
        GlassCircleButton(
            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            contentDescription = stringResource(R.string.player_queue),
            onClick = onOpenQueue,
        )
    }
}

@Composable
private fun ModeSelectorRow(
    mode: PlayerMode,
    track: Track,
    onOpenControls: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCircleButton(
            icon = Icons.Rounded.Lyrics,
            contentDescription = stringResource(R.string.player_lyrics),
            active = mode == PlayerMode.LYRICS,
            onClick = onOpenLyrics,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .glass(shape = CircleShape, strong = true)
                .clickable(role = Role.Button, onClick = onOpenControls)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${track.title} • ${track.artist}",
                style = NodesType.pill,
                color = NodesWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GlassCircleButton(
            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            contentDescription = stringResource(R.string.player_queue),
            active = mode == PlayerMode.QUEUE,
            onClick = onOpenQueue,
        )
    }
}

@Composable
private fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (active) 0.20f else 0.08f))
            .glass(shape = CircleShape, strong = true),
        contentAlignment = Alignment.Center,
    ) {
        CircleIconButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = onClick,
            size = 52.dp,
            iconSize = 22.dp,
        )
    }
}

@Composable
private fun DockAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = NodesWhite,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PlayerProgress(
    state: PlayerUiState,
    sourceName: String,
    tint: Color,
    onSeek: (Long) -> Unit,
) {
    val fraction = state.progress.coerceIn(0f, 1f)
    val accent = tint.toPlayerAccent()
    // A flat bar with no knob, as in the reference: the filled part is the
    // only marker. Scrubbing tracks the finger and seeks when it lifts, so the
    // music does not restart on every pixel.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val shown = if (scrubbing) scrubFraction else fraction
    val seekable = state.durationMs > 0L

    val seekLabel = stringResource(R.string.player_seek_bar)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .semantics {
                contentDescription = seekLabel
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
            }
            .pointerInput(state.durationMs) {
                detectTapGestures { offset ->
                    if (seekable) {
                        onSeek(((offset.x / size.width).coerceIn(0f, 1f) * state.durationMs).toLong())
                    }
                }
            }
            .pointerInput(state.durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        scrubbing = true
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        scrubFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        scrubbing = false
                        if (seekable) onSeek((scrubFraction * state.durationMs).toLong())
                    },
                    onDragCancel = { scrubbing = false },
                )
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.22f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(shown)
                    .fillMaxHeight()
                    .background(if (seekable) accent else Color.White.copy(alpha = 0.35f)),
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDuration(state.positionMs.coerceAtLeast(0L)),
            style = MaterialTheme.typography.labelMedium,
            color = NodesWhite.copy(alpha = 0.72f),
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = NodesWhite.copy(alpha = 0.64f),
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = sourceName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = NodesWhite.copy(alpha = 0.60f),
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = formatDuration(state.durationMs.coerceAtLeast(0L)),
            style = MaterialTheme.typography.labelMedium,
            color = NodesWhite.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun PlayerTransport(
    state: PlayerUiState,
    tint: Color,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
) {
    val context = LocalContext.current
    val accent = tint.toPlayerAccent()
    // The reference transport has five controls: cast, previous, play, next and
    // one combined shuffle/repeat button. That button cycles
    // off -> shuffle -> repeat track -> off, so both modes stay reachable
    // without widening the row.
    val modeActive = state.shuffleOn || state.repeatMode != RepeatMode.OFF
    val modeIcon = when {
        state.repeatMode == RepeatMode.ONE -> Icons.Rounded.RepeatOne
        else -> Icons.Rounded.Shuffle
    }
    val onCycleMode: () -> Unit = {
        when {
            !state.shuffleOn && state.repeatMode == RepeatMode.OFF -> onShuffle()
            state.shuffleOn && state.repeatMode == RepeatMode.OFF -> {
                onShuffle()
                onRepeat()
            }
            else -> onRepeat()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            CastRouteButton(
                unavailable = state.embeddedVideoId != null,
                onUnavailableTap = {
                    Toast.makeText(
                        context,
                        R.string.player_cast_unavailable,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onChooserFailure = {
                    Toast.makeText(
                        context,
                        R.string.player_cast_chooser_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
        CircleIconButton(
            icon = Icons.Rounded.FastRewind,
            contentDescription = stringResource(R.string.action_previous),
            onClick = onPrevious,
            tint = accent,
            size = 52.dp,
            iconSize = 34.dp,
        )
        // A bare icon, as in the reference: the transport reads as glyphs on
        // the wash, not as a row of buttons. The whole 68dp square is the tap
        // target so the hit area does not shrink with the chrome.
        Box(
            modifier = Modifier
                .size(68.dp)
                .clickable(role = Role.Button, onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state.isPlaying) {
                    Icons.Rounded.Pause
                } else {
                    Icons.Rounded.PlayArrow
                },
                contentDescription = stringResource(
                    if (state.isPlaying) R.string.action_pause else R.string.action_play,
                ),
                tint = accent,
                modifier = Modifier.size(44.dp),
            )
        }
        CircleIconButton(
            icon = Icons.Rounded.FastForward,
            contentDescription = stringResource(R.string.action_next),
            onClick = onNext,
            tint = accent,
            size = 52.dp,
            iconSize = 34.dp,
        )
        // Bare glyph like its neighbours; the accent tint carries the active
        // state now that there is no pill behind it.
        CircleIconButton(
            icon = modeIcon,
            contentDescription = stringResource(
                if (state.repeatMode == RepeatMode.ONE) R.string.player_repeat else R.string.player_shuffle,
            ),
            onClick = onCycleMode,
            tint = if (modeActive) accent else accent.copy(alpha = 0.62f),
            filled = false,
            size = 52.dp,
            iconSize = 30.dp,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TrackOptionsSheet(
    track: Track,
    liked: Boolean,
    onDismiss: () -> Unit,
    onViewArtist: () -> Unit,
    onViewAlbum: () -> Unit,
    onToggleLibrary: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NodesSurface,
    ) {
        val context = LocalContext.current
        val clipboard = LocalClipboardManager.current
        val link = track.externalUrl
            ?: track.videoId?.let { videoId -> "https://music.youtube.com/watch?v=$videoId" }

        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium,
            color = NodesWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (track.artist.isNotBlank()) {
            SheetAction(
                label = stringResource(R.string.player_view_artist),
                icon = Icons.Rounded.Person,
                onClick = onViewArtist,
            )
        }
        if (!track.album.isNullOrBlank()) {
            SheetAction(
                label = stringResource(R.string.player_view_album),
                icon = Icons.Rounded.Album,
                onClick = onViewAlbum,
            )
        }
        SheetAction(
            label = stringResource(
                if (liked) R.string.player_remove_library else R.string.player_add_library,
            ),
            icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            onClick = onToggleLibrary,
        )
        SheetAction(
            label = stringResource(R.string.player_play_next),
            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            onClick = onPlayNext,
        )
        SheetAction(
            label = stringResource(R.string.player_add_to_queue),
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            onClick = onAddToQueue,
        )
        if (link != null) {
            SheetAction(
                label = stringResource(R.string.player_copy_link),
                icon = Icons.Rounded.Link,
                onClick = {
                    onDismiss()
                    clipboard.setText(AnnotatedString(link))
                    Toast.makeText(context, R.string.player_link_copied, Toast.LENGTH_SHORT)
                        .show()
                },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NodesWhite,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = NodesWhite,
        )
    }
}

/**
 * The official YouTube embedded player, kept visible above every mode as the
 * YouTube API Services Terms require. On dispose the video pauses and the
 * controller drops the embedded handle.
 */
@Composable
private fun YouTubeEmbeddedCard(videoId: String, onStop: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var loadedVideoId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(videoId) {
        onDispose {
            runCatching { player?.pause() }
            onStop()
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black),
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                YouTubePlayerView(context).also(lifecycle::addObserver).apply {
                    addYouTubePlayerListener(
                        object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                player = youTubePlayer
                                if (loadedVideoId != videoId) {
                                    youTubePlayer.loadVideo(videoId, 0f)
                                    loadedVideoId = videoId
                                }
                            }
                        },
                    )
                }
            },
            update = { view ->
                view.getYouTubePlayerWhenReady(
                    object : YouTubePlayerCallback {
                        override fun onYouTubePlayer(youTubePlayer: YouTubePlayer) {
                            player = youTubePlayer
                            if (loadedVideoId != videoId) {
                                youTubePlayer.loadVideo(videoId, 0f)
                                loadedVideoId = videoId
                            }
                        }
                    },
                )
            },
            onRelease = { view -> lifecycle.removeObserver(view) },
        )
    }
}

@Composable
private fun playerSourceName(sourceId: SourceId): String = stringResource(
    when (sourceId) {
        SourceId.LOCAL -> R.string.player_source_local
        SourceId.JAMENDO -> R.string.player_source_jamendo
        SourceId.YOUTUBE -> R.string.player_source_youtube
        SourceId.YOUTUBE_MUSIC -> R.string.player_source_youtube_music
    },
)
