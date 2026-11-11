/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.player

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
import app.nodes.ui.components.PillButton
import app.nodes.ui.components.PillStyle
import app.nodes.ui.components.SongRow
import app.nodes.ui.components.TintedBackdrop
import app.nodes.ui.components.TrackMenuButton
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesGreyDim
import app.nodes.ui.theme.NodesSpacing
import app.nodes.ui.theme.NodesSurface
import app.nodes.ui.theme.NodesType
import app.nodes.ui.theme.NodesWhite
import app.nodes.ui.theme.rememberArtworkTint
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerCallback
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.CancellationException

private enum class PlayerMode { CONTROLS, LYRICS, QUEUE }

/**
 * Full Now Playing.
 *
 * The screen has three internal modes rather than three navigation routes:
 * controls stay one tap away, and the Queue/Lyrics buttons at the bottom do
 * not push a new destination that would hide the official YouTube card. The
 * embedded YouTube player is composed above all three modes and is never
 * backgrounded; leaving Now Playing pauses it and drops the handle (YOUTUBE.md).
 *
 * The surface behind everything is the artwork's dominant colour fading into
 * near-black, sampled locally by [rememberArtworkTint]; no third-party service
 * is asked for a palette.
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
    val playAll: (List<Track>) -> Unit = { tracks ->
        tracks.firstOrNull()?.let { playerViewModel.play(it, tracks) }
    }

    TintedBackdrop(tint = tint) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            PlayerTopBar(
                track = track,
                mode = mode,
                sourceName = sourceName,
                onBack = onBack,
                onExitMode = { mode = PlayerMode.CONTROLS },
                onMore = { showTrackOptions = true },
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
                    state = playerState,
                    track = track,
                    liked = liked,
                    sourceName = sourceName,
                    onToggleLike = { viewModel.toggleLike(track) },
                    onPlayPause = playerViewModel::togglePlayPause,
                    onPrevious = playerViewModel::previous,
                    onNext = playerViewModel::next,
                    onSeek = playerViewModel::seekTo,
                    onShuffle = playerViewModel::toggleShuffle,
                    onRepeat = { playerViewModel.cycleRepeat() },
                    onInfinite = playerViewModel::toggleInfiniteQueue,
                    onOpenArtist = onOpenArtist,
                    onOpenAlbum = onOpenAlbum,
                    onOpenLyrics = { mode = PlayerMode.LYRICS },
                    onOpenQueue = { mode = PlayerMode.QUEUE },
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
                    onPlayIndex = playerViewModel::playQueueIndex,
                    onMove = playerViewModel::moveInQueue,
                    onRemove = playerViewModel::removeFromQueue,
                    onPlayNext = playerViewModel::playNext,
                    onAddToQueue = playerViewModel::addToQueue,
                    onOpenArtist = onOpenArtist,
                    onOpenAlbum = onOpenAlbum,
                    onPlayAll = { playAll(playerState.queue) },
                    modifier = Modifier.weight(1f),
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
    track: Track,
    mode: PlayerMode,
    sourceName: String,
    onBack: () -> Unit,
    onExitMode: () -> Unit,
    onMore: () -> Unit,
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
            background = NodesBlack.copy(alpha = 0.28f),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when (mode) {
                    PlayerMode.CONTROLS -> stringResource(R.string.player_playing_from, sourceName)
                    PlayerMode.LYRICS -> stringResource(R.string.player_lyrics)
                    PlayerMode.QUEUE -> stringResource(R.string.player_queue)
                }.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = NodesGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.title,
                style = NodesType.rowTitle,
                color = NodesWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CircleIconButton(
            icon = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.player_more),
            onClick = onMore,
            filled = true,
            background = NodesBlack.copy(alpha = 0.28f),
        )
    }
}

@Composable
private fun ControlsMode(
    state: PlayerUiState,
    track: Track,
    liked: Boolean,
    sourceName: String,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onInfinite: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (state.embeddedVideoId == null) {
            item(key = "artwork") {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 34.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val artSize = minOf(maxWidth, 380.dp)
                    Box(Modifier.size(artSize)) {
                        Artwork(
                            artworkUri = track.artworkUri,
                            title = track.title,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(28.dp),
                            targetPx = 1280,
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GlassBadge(text = sourceName.uppercase())
                            if (track.isExplicit) {
                                GlassBadge(text = "E")
                            }
                        }
                    }
                }
            }
        }

        item(key = "meta") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NodesSpacing.gutter, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = NodesWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NodesGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(
                                enabled = track.artist.isNotBlank(),
                            ) { onOpenArtist(track.artist) },
                        )
                        track.album?.takeIf { it.isNotBlank() }?.let { album ->
                            Text(
                                text = " · $album",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NodesGreyDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { onOpenAlbum(album) },
                            )
                        }
                    }
                    track.licenseNote?.let { note ->
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.labelSmall,
                            color = NodesGreyDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                CircleIconButton(
                    icon = if (liked) {
                        Icons.Rounded.Favorite
                    } else {
                        Icons.Rounded.FavoriteBorder
                    },
                    contentDescription = stringResource(R.string.player_like),
                    onClick = onToggleLike,
                    tint = if (liked) NodesWhite else NodesGrey,
                    filled = true,
                    background = Color.White.copy(alpha = if (liked) 0.18f else 0.08f),
                )
            }
        }

        if (state.embeddedVideoId == null) {
            item(key = "seek") {
                Column(Modifier.padding(horizontal = NodesSpacing.gutter)) {
                    val fraction = state.progress.coerceIn(0f, 1f)
                    Slider(
                        value = fraction,
                        onValueChange = { value ->
                            val duration = state.durationMs
                            if (duration > 0L) onSeek((value * duration).toLong())
                        },
                        enabled = state.durationMs > 0L,
                        colors = SliderDefaults.colors(
                            thumbColor = NodesWhite,
                            activeTrackColor = NodesWhite,
                            inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                        ),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatDuration(state.positionMs.coerceAtLeast(0L)),
                            style = MaterialTheme.typography.labelSmall,
                            color = NodesGrey,
                        )
                        Text(
                            text = formatDuration(state.durationMs.coerceAtLeast(0L)),
                            style = MaterialTheme.typography.labelSmall,
                            color = NodesGrey,
                        )
                    }
                }
            }
        }

        item(key = "transport") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NodesSpacing.gutter, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(
                    icon = Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.player_shuffle),
                    onClick = onShuffle,
                    tint = if (state.shuffleOn) NodesWhite else NodesGrey,
                    filled = state.shuffleOn,
                )
                CircleIconButton(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.action_previous),
                    onClick = onPrevious,
                    size = 50.dp,
                    iconSize = 28.dp,
                )
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(NodesWhite)
                        .clickable(onClick = onPlayPause),
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
                        tint = NodesBlack,
                        modifier = Modifier.size(34.dp),
                    )
                }
                CircleIconButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.action_next),
                    onClick = onNext,
                    size = 50.dp,
                    iconSize = 28.dp,
                )
                CircleIconButton(
                    icon = if (state.repeatMode == RepeatMode.ONE) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    },
                    contentDescription = stringResource(R.string.player_repeat),
                    onClick = onRepeat,
                    tint = if (state.repeatMode != RepeatMode.OFF) NodesWhite else NodesGrey,
                    filled = state.repeatMode != RepeatMode.OFF,
                )
            }
        }

        item(key = "secondary") {
            val context = LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(
                    icon = Icons.Rounded.AllInclusive,
                    contentDescription = stringResource(R.string.player_infinite),
                    onClick = onInfinite,
                    tint = if (state.infiniteQueue) NodesWhite else NodesGrey,
                    filled = state.infiniteQueue,
                )
                Spacer(Modifier.width(10.dp))
                CastRouteButton(
                    unavailable = state.embeddedVideoId != null,
                    onUnavailableTap = {
                        Toast.makeText(
                            context,
                            R.string.player_cast_unavailable,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }

        item(key = "modes") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NodesSpacing.gutter, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PillButton(
                    text = stringResource(R.string.player_lyrics),
                    onClick = onOpenLyrics,
                    icon = Icons.Rounded.Lyrics,
                    style = PillStyle.Glass,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = stringResource(R.string.player_queue),
                    onClick = onOpenQueue,
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    style = PillStyle.Glass,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
        val offset = if (viewport > 0) -(viewport / 3) else 0
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
                top = 32.dp,
                bottom = 140.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(
                items = result.lines,
                key = { index, line -> "lyric-$index-${line.timeMs}" },
            ) { index, line ->
                LyricLineView(
                    line = line,
                    active = index == activeIndex,
                    past = index < activeIndex,
                )
            }
        }

        AnimatedVisibility(
            visible = autoScroll && !follow,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PillButton(
                text = stringResource(R.string.player_lyrics_resync),
                onClick = { onFollowChanged(true) },
                icon = Icons.Rounded.Sync,
                style = PillStyle.Glass,
            )
        }
    }
}

@Composable
private fun LyricLineView(line: LyricLine, active: Boolean, past: Boolean) {
    Text(
        text = line.text.ifBlank { "♪" },
        style = if (active) NodesType.lyricActive else NodesType.lyricIdle,
        color = when {
            active -> NodesWhite
            past -> NodesWhite.copy(alpha = 0.42f)
            else -> NodesWhite.copy(alpha = 0.28f)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun QueueMode(
    state: PlayerUiState,
    onPlayIndex: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlayAll: () -> Unit,
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
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item(key = "queue-head") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NodesSpacing.gutter, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.playlist_up_next),
                    style = NodesType.sectionTitle,
                    color = NodesWhite,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = stringResource(R.string.playlist_play_all),
                    onClick = onPlayAll,
                    icon = Icons.Rounded.PlayArrow,
                    style = PillStyle.Glass,
                )
            }
            Text(
                text = stringResource(R.string.playlist_track_count, state.queue.size),
                style = MaterialTheme.typography.bodySmall,
                color = NodesGrey,
                modifier = Modifier.padding(horizontal = NodesSpacing.gutter),
            )
        }
        itemsIndexed(
            items = state.queue,
            key = { index, track -> "queue-$index-${track.id}" },
        ) { index, track ->
            SongRow(
                track = track,
                onClick = { onPlayIndex(index) },
                position = index + 1,
                showDownload = false,
                showStar = false,
                active = index == state.queueIndex,
                activeTint = NodesWhite,
                activePlaying = index == state.queueIndex && state.isPlaying,
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
