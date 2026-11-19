/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nodes.R
import app.nodes.core.model.Track
import app.nodes.ui.components.glassNavBackdrop
import app.nodes.ui.theme.SpartialType
import app.nodes.ui.theme.SpartialWhite
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

/**
 * Compact now-playing bar that sits above the floating tab bar. The whole
 * capsule opens the full player: by tap, or by dragging it upwards - the bar
 * follows the finger and opens on release, which is the gesture the full player
 * answers with a downward swipe. The transport controls remain separate touch
 * targets. A hairline at the top carries playback progress without adding a
 * second row of chrome. A long-press opens the track menu, where the current
 * track can be added to a playlist without opening the full player.
 */
@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    hazeState: HazeState? = null,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onAddToPlaylist: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    // How far the capsule has lifted toward the player: negative is up. It is
    // plain state, written straight from the pointer deltas, because launching
    // a coroutine per delta (as an Animatable forced) dropped frames on a slow
    // device. Settling is animated by writing the same state each frame.
    var lift by remember { mutableFloatStateOf(0f) }
    val openThreshold = 72f
    // How far the capsule travels when it hands over to the full player. It is
    // measured from the capsule itself, so it leaves the screen exactly.
    var capsuleHeightPx by remember { mutableIntStateOf(0) }
    var exiting by remember { mutableStateOf(false) }
    // Where the handover starts and ends, so the fade can follow the travel
    // instead of the absolute position.
    var exitFrom by remember { mutableFloatStateOf(0f) }
    var exitTo by remember { mutableFloatStateOf(0f) }

    /** Drives [lift] to [target] along [spec], then optionally runs [onEnd]. */
    fun settleLift(
        target: Float,
        spec: androidx.compose.animation.core.AnimationSpec<Float>,
        onEnd: () -> Unit = {},
    ) {
        scope.launch {
            androidx.compose.animation.core.animate(
                initialValue = lift,
                targetValue = target,
                animationSpec = spec,
            ) { value, _ -> lift = value }
            onEnd()
        }
    }

    val dragModifier = Modifier
        .draggable(
            state = rememberDraggableState { delta ->
                lift = (lift + delta).coerceIn(-openThreshold * 2f, 0f)
            },
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
                if (lift < -openThreshold || velocity < -900f) {
                    // The gesture continues into the handover: the capsule is
                    // absorbed upward and fades as it goes, while the player
                    // is already rising underneath. A spring rather than a
                    // linear tween, so it accelerates away instead of drifting.
                    exiting = true
                    exitFrom = lift
                    // Always further up than the finger already took it.
                    exitTo = minOf(
                        exitFrom - (if (capsuleHeightPx > 0) capsuleHeightPx.toFloat() else openThreshold),
                        -openThreshold * 2f,
                    )
                    settleLift(
                        target = exitTo,
                        spec = spring(dampingRatio = 0.9f, stiffness = 520f),
                        onEnd = onClick,
                    )
                } else {
                    // Slightly under-damped on the way back: the capsule settles
                    // with a small, quick breath instead of snapping flat.
                    settleLift(0f, spring(dampingRatio = 0.78f, stiffness = 520f))
                }
            },
        )
        .graphicsLayer {
            translationY = lift
            // Progress of the lift toward the opening threshold.
            val up = (-lift / (openThreshold * 3f)).coerceIn(0f, 1f)
            val alpha = if (exiting) {
                // Fades exactly as far as it has travelled out.
                val travel = (exitFrom - exitTo).coerceAtLeast(1f)
                val progress = ((exitFrom - lift) / travel).coerceIn(0f, 1f)
                1f - progress
            } else {
                1f - up * 0.6f
            }
            this.alpha = alpha
            // A whisper of shrink: the capsule recedes into the distance as
            // the player comes forward, which is what makes the handover read
            // as one object changing state rather than two animations.
            val scale = 1f - up * 0.06f
            scaleX = scale
            scaleY = scale
        }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .then(dragModifier)
            // The capsule's own height, so the handover animation can carry it
            // exactly off the screen.
            .onSizeChanged { size -> capsuleHeightPx = size.height }
            // The same real-blur glass as the bar, layout untouched.
            .then(glassNavBackdrop(Modifier, hazeState, RoundedCornerShape(22.dp)))
            // Tap opens the player; long-press opens the track menu.
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = if (onAddToPlaylist != null) {
                    { menuOpen = true }
                } else {
                    null
                },
            ),
    ) {
        if (progress > 0f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(SpartialWhite.copy(alpha = 0.8f)),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                artworkUri = track.artworkUri,
                title = track.title,
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(10.dp),
                targetPx = 256,
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = SpartialType.rowTitle.copy(fontSize = 15.sp),
                    color = SpartialWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = SpartialType.rowMeta.copy(fontSize = 13.sp, color = SpartialWhite.copy(alpha = 0.60f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Previous, play/pause and next, plain white filled glyphs at
            // ~28dp - no circle behind the play.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(role = Role.Button, onClick = onPrevious),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.action_previous),
                    tint = SpartialWhite,
                    modifier = Modifier.size(28.dp),
                )
            }
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = SpartialWhite,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(role = Role.Button, onClick = onToggle),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.action_pause else R.string.action_play,
                            ),
                            tint = SpartialWhite,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(role = Role.Button, onClick = onNext),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.action_next),
                    tint = SpartialWhite,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        TrackMenuDropdown(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}
