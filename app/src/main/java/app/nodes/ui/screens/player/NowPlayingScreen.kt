/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.player

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.model.LyricLine
import app.nodes.core.model.Track
import app.nodes.core.util.formatDuration
import app.nodes.playback.RepeatMode
import app.nodes.ui.components.Artwork
import app.nodes.ui.components.ArtworkBackdrop
import app.nodes.ui.components.CastRouteButton
import app.nodes.ui.components.KeepScreenOn
import app.nodes.ui.components.TrackMenuButton
import app.nodes.ui.components.TrackRow
import app.nodes.ui.components.glass
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesSurface
import app.nodes.ui.theme.NodesTeal
import app.nodes.ui.theme.NodesWhite
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerCallback
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.CancellationException

private enum class PlayerTab { QUEUE, LYRICS }

/**
 * Full Now Playing screen.
 *
 * The whole screen is one [LazyColumn]. It used to be a `verticalScroll` column
 * with a 260dp `LazyColumn` for the lyrics inside it and the entire queue
 * composed eagerly in a plain `Column` - two nested vertically scrollable
 * containers and an unbounded row count, which is exactly the shape that makes
 * Compose throw "Vertically scrollable component was measured with an infinite
 * maximum height" during a transition and makes a long queue allocate every
 * artwork at once. One lazy list removes both problems: every section is an item,
 * the queue composes only what is on screen, and nothing is nested.
 *
 * For YouTube tracks the official embedded player is rendered as a video card
 * above the controls. It is never hidden, never backgrounded, and no stream URL
 * is extracted - see YOUTUBE.md. Leaving this screen pauses the video.
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
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val lyricsAutoScroll by viewModel.lyricsAutoScroll.collectAsStateWithLifecycle()
    val track = playerState.current

    KeepScreenOn(keepScreenOn)

    var tab by remember { mutableStateOf(PlayerTab.QUEUE) }
    val listState = rememberLazyListState()
    var showTrackOptions by remember { mutableStateOf(false) }

    // Following is a per-song decision: the preference decides how a song
    // starts, and a manual drag pauses it until the chip is tapped or the next
    // track begins.
    var followLyrics by rememberSaveable { mutableStateOf(true) }
    val autoScroll = lyricsAutoScroll && followLyrics
    val isDraggingLyrics by listState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(track?.id) {
        viewModel.onTrackChanged(track)
        followLyrics = true
    }

    LaunchedEffect(isDraggingLyrics, tab) {
        if (isDraggingLyrics && tab == PlayerTab.LYRICS) followLyrics = false
    }

    val activeLyricIndex = if (lyrics.lines.isEmpty()) {
        -1
    } else {
        lyrics.lines.indexOfLast { it.timeMs <= playerState.positionMs.coerceAtLeast(0L) }
            .coerceIn(0, lyrics.lines.lastIndex)
    }

    // Lyrics are items of the screen's own lazy list, so the item to scroll to
    // is the active line plus every item above the lyrics section. The header
    // count changes when the embedded YouTube card replaces the seek bar.
    LaunchedEffect(tab, autoScroll, activeLyricIndex, playerState.embeddedVideoId, lyrics.lines.size) {
        if (tab != PlayerTab.LYRICS || !autoScroll || activeLyricIndex < 0) return@LaunchedEffect
        val headerItems = if (playerState.embeddedVideoId == null) 6 else 5
        val viewport = listState.layoutInfo.viewportSize.height
        val offset = if (viewport > 0) -(viewport / 3) else 0
        try {
            // A third of the viewport below the top keeps the current line
            // near the middle instead of pinning it to the very top.
            listState.animateScrollToItem(headerItems + activeLyricIndex, offset)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The lyrics can be replaced while the animation runs; the next
            // line change starts a fresh scroll.
        }
    }

    if (track == null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(NodesBlack)
                .statusBarsPadding()
                .padding(20.dp),
        ) {
            BackButton(onBack)
            Spacer(Modifier.height(40.dp))
            Text(
                text = stringResource(R.string.home_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = NodesGrey,
            )
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        ArtworkBackdrop(
            artworkUri = track.artworkUri,
            title = track.title,
            modifier = Modifier.fillMaxSize(),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "top") {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackButton(onBack)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.player_queue).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NodesGrey,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { showTrackOptions = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.player_more),
                            tint = NodesWhite,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            item(key = "media") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    val videoId = playerState.embeddedVideoId
                    if (videoId != null) {
                        YouTubeEmbeddedCard(
                            videoId = videoId,
                            onStop = playerViewModel::stopEmbedded,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.player_youtube_notice),
                            style = MaterialTheme.typography.labelMedium,
                            color = NodesGrey,
                        )
                    } else {
                        Artwork(
                            artworkUri = track.artworkUri,
                            title = track.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            shape = RoundedCornerShape(28.dp),
                            targetPx = 1280,
                        )
                    }
                }
            }

            item(key = "meta") {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
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
                        // The artist name under the song title is a link to the
                        // artist page; the album rides along as a second link.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NodesTeal,
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
                                    color = NodesGrey,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onOpenAlbum(album) },
                                )
                            }
                        }
                        track.licenseNote?.let { note ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = note,
                                style = MaterialTheme.typography.labelSmall,
                                color = NodesTeal.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(
                        imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.player_like),
                        tint = if (liked) NodesTeal else NodesWhite,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { viewModel.toggleLike(track) },
                    )
                }
            }

            if (playerState.embeddedVideoId == null) {
                item(key = "seek") {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        // A zero-length track has no meaningful fraction, and a
                        // Slider outside 0..1 throws. Both are normal while a
                        // stream is still resolving.
                        val fraction = playerState.progress.coerceIn(0f, 1f)
                        Slider(
                            value = fraction,
                            onValueChange = { value ->
                                val duration = playerState.durationMs
                                if (duration > 0L) {
                                    playerViewModel.seekTo((value * duration).toLong())
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = NodesWhite,
                                activeTrackColor = NodesWhite,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            ),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatDuration(playerState.positionMs.coerceAtLeast(0L)),
                                style = MaterialTheme.typography.labelSmall,
                                color = NodesGrey,
                            )
                            Text(
                                text = formatDuration(playerState.durationMs.coerceAtLeast(0L)),
                                style = MaterialTheme.typography.labelSmall,
                                color = NodesGrey,
                            )
                        }
                    }
                }
            }

            item(key = "controls") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = null,
                        tint = NodesWhite,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { playerViewModel.previous() },
                    )
                    Spacer(Modifier.width(28.dp))
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(NodesWhite)
                            .clickable { playerViewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = stringResource(R.string.action_play),
                            tint = NodesBlack,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Spacer(Modifier.width(28.dp))
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = null,
                        tint = NodesWhite,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { playerViewModel.next() },
                    )
                }
            }

            item(key = "secondary-controls") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val context = LocalContext.current
                    CastRouteButton(
                        // The official embedded player cannot be cast: its video
                        // lives in the YouTube iframe, not in the session.
                        unavailable = playerState.embeddedVideoId != null,
                        onUnavailableTap = {
                            Toast.makeText(
                                context,
                                R.string.player_cast_unavailable,
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    SecondaryControl(
                        icon = Icons.Rounded.Shuffle,
                        label = stringResource(R.string.player_shuffle),
                        active = playerState.shuffleOn,
                        onClick = { playerViewModel.toggleShuffle() },
                    )
                    SecondaryControl(
                        icon = Icons.Rounded.AllInclusive,
                        label = stringResource(R.string.player_infinite),
                        active = playerState.infiniteQueue,
                        onClick = { playerViewModel.toggleInfiniteQueue() },
                    )
                    SecondaryControl(
                        icon = when (playerState.repeatMode) {
                            RepeatMode.ONE -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        },
                        label = stringResource(R.string.player_repeat),
                        active = playerState.repeatMode != RepeatMode.OFF,
                        onClick = { playerViewModel.cycleRepeat() },
                    )
                }
            }

            item(key = "tabs") {
                Row(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .glass(CircleShape)
                        .padding(4.dp),
                ) {
                    PlayerTab.entries.forEach { entry ->
                        val selected = tab == entry
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(
                                    if (selected) Color.White.copy(alpha = 0.16f)
                                    else Color.Transparent,
                                )
                                .clickable { tab = entry }
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = if (entry == PlayerTab.QUEUE) {
                                    stringResource(R.string.player_queue)
                                } else {
                                    stringResource(R.string.player_lyrics)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) NodesWhite else NodesGrey,
                            )
                        }
                    }
                }
            }

            item(key = "tab-gap") { Spacer(Modifier.height(8.dp)) }

            when (tab) {
                PlayerTab.QUEUE -> {
                    if (playerState.queue.isEmpty()) {
                        item(key = "queue-empty") {
                            Text(
                                text = stringResource(R.string.home_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = NodesGrey,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    } else {
                        // YouTube Music radios happily repeat a video, so the id
                        // alone is not a unique key: LazyColumn throws "Key was
                        // already used" on the second copy. Position plus id is
                        // unique even when the queue is reordered under us.
                        itemsIndexed(
                            items = playerState.queue,
                            key = { index, item -> "queue-$index-${item.id}" },
                        ) { index, item ->
                            QueueRow(
                                item = item,
                                isCurrent = index == playerState.queueIndex,
                                onClick = { playerViewModel.playQueueIndex(index) },
                                onPlayNext = { playerViewModel.playNext(item) },
                                onAddToQueue = { playerViewModel.addToQueue(item) },
                                onRemove = { playerViewModel.removeFromQueue(index) },
                                onViewArtist = item.artist.takeIf { it.isNotBlank() }
                                    ?.let { artist -> { onOpenArtist(artist) } },
                                onViewAlbum = item.album?.takeIf { it.isNotBlank() }
                                    ?.let { album -> { onOpenAlbum(album) } },
                            )
                        }
                    }
                }

                PlayerTab.LYRICS -> LyricsItems(
                    lines = lyrics.lines,
                    positionMs = playerState.positionMs,
                )
            }

            item(key = "bottom") {
                Spacer(Modifier.navigationBarsPadding().height(8.dp))
            }
        }

        // Glossy's re-sync affordance: a manual scroll pauses following, and
        // this chip puts the current line back under the thumb. It is only
        // shown while the preference is on; turning it off is a settings
        // decision, not an accidental drag.
        AnimatedVisibility(
            visible = tab == PlayerTab.LYRICS &&
                lyricsAutoScroll &&
                !followLyrics &&
                lyrics.lines.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .glass(CircleShape)
                    .clickable { followLyrics = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    tint = NodesWhite,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = stringResource(R.string.player_auto_scroll),
                    style = MaterialTheme.typography.labelLarge,
                    color = NodesWhite,
                )
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
}

/**
 * The track menu for the Now Playing screen, as a bottom sheet rather than a
 * dropdown: it scrolls with nothing and stays above the transport gestures.
 * Same actions as the queue menu, plus adding the track to the library and
 * copying its public link.
 */
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
        // YouTube Music tracks carry a video id; Jamendo and contributed
        // libraries carry an externalUrl. Anything else has nothing public to
        // link to, so the item is simply not offered.
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

/** One tappable row in the track options sheet. */
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
 * A round ghost button in the secondary controls row under the play button.
 * Active modes light up in the teal accent so the state is visible without a
 * label underneath.
 */
@Composable
private fun SecondaryControl(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 5.dp)
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) NodesTeal else NodesWhite.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = stringResource(R.string.action_close),
            tint = NodesWhite,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun QueueRow(
    item: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemove: () -> Unit,
    onViewArtist: (() -> Unit)? = null,
    onViewAlbum: (() -> Unit)? = null,
) {
    Column {
        TrackRow(
            track = item,
            onClick = onClick,
            trailing = {
                TrackMenuButton(
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onViewArtist = onViewArtist,
                    onViewAlbum = onViewAlbum,
                    onRemoveFromQueue = onRemove,
                    removeEnabled = !isCurrent,
                )
            },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 86.dp, end = 20.dp)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f)),
        )
    }
}

/**
 * Lyrics as items of the screen's own lazy list, not as a list of their own.
 *
 * [activeIndex] is clamped: `indexOfLast` returns -1 before the first timestamp
 * and the list can be replaced between composition and draw when the track
 * changes, so an unchecked index would be an IndexOutOfBounds on the next frame.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.LyricsItems(
    lines: List<LyricLine>,
    positionMs: Long,
) {
    if (lines.isEmpty()) {
        item(key = "lyrics-empty") {
            Text(
                text = stringResource(R.string.player_no_lyrics),
                style = MaterialTheme.typography.bodyMedium,
                color = NodesGrey,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        return
    }
    val safePosition = positionMs.coerceAtLeast(0L)
    val activeIndex = lines.indexOfLast { it.timeMs <= safePosition }
        .coerceIn(0, lines.lastIndex)
    itemsIndexed(
        items = lines,
        key = { index, line -> "lyric-$index-${line.timeMs}" },
    ) { index, line ->
        val active = index == activeIndex
        Text(
            text = line.text.ifBlank { "♪" },
            // Lyrics are read from arm's length, so they get the larger body
            // size and a looser line height than the rest of the screen.
            style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 30.sp),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) NodesWhite else NodesGrey.copy(alpha = 0.6f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

/**
 * The official YouTube embedded player, kept visible above the controls as the
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
        AndroidView(
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
                // YouTubePlayerCallback is a Kotlin interface, so it needs an
                // explicit object rather than a SAM lambda.
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
