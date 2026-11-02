/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.model.LyricLine
import app.nodes.core.model.Track
import app.nodes.core.util.formatDuration
import app.nodes.ui.components.Artwork
import app.nodes.ui.components.ArtworkBackdrop
import app.nodes.ui.components.KeepScreenOn
import app.nodes.ui.components.TrackRow
import app.nodes.ui.components.glass
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesTeal
import app.nodes.ui.theme.NodesWhite
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerCallback
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

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
    viewModel: NowPlayingViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val track = playerState.current

    KeepScreenOn(keepScreenOn)

    var tab by remember { mutableStateOf(PlayerTab.QUEUE) }

    LaunchedEffect(track?.id) { viewModel.onTrackChanged(track) }

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
                    Spacer(Modifier.size(44.dp))
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
                        Text(
                            text = buildString {
                                append(track.artist)
                                if (track.album != null) append(" · ${track.album}")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = NodesGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                        ) { _, item ->
                            QueueRow(
                                item = item,
                                onClick = { playerViewModel.play(item, playerState.queue) },
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
private fun QueueRow(item: Track, onClick: () -> Unit) {
    Column {
        TrackRow(track = item, onClick = onClick)
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) NodesWhite else NodesGrey.copy(alpha = 0.6f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
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
