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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HANDOFF_NAVIGATE_MS = 90L

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
    // How far the fade had already come when the finger lifted, so the exit
    // continues that curve instead of restarting it.
    var exitStartUp by remember { mutableFloatStateOf(0f) }

    /** The fade progress reached by dragging alone, capped well short of gone. */
    fun dragProgress(): Float = (-lift / (openThreshold * 3f)).coerceIn(0f, 1f) * 0.45f

    /** Drives [lift] to [target] along [spec], then optionally runs [onEnd]. */
    fun settleLift(
        target: Float,
        spec: androidx.compose.animation.core.AnimationSpec<Float>,
        initialVelocity: Float = 0f,
        onEnd: () -> Unit = {},
    ) {
        scope.launch {
            androidx.compose.animation.core.animate(
                initialValue = lift,
                targetValue = target,
                animationSpec = spec,
                initialVelocity = initialVelocity,
            ) { value, _ -> lift = value }
            onEnd()
        }
    }

    /**
     * Hands the capsule over to the full player.
     *
     * The exit starts on the frame of release, and navigation follows ~90ms
     * later: the capsule is already most of the way faded and moving by then,
     * so the sheet arrives while the gesture is still visibly finishing instead
     * of after a dead pause (the old wait-for-the-animation-to-end order) or
     * instead of cutting the fade short (navigating on the release frame, which
     * disposes this composable before it can finish).
     *
     * [velocity] is the finger's own fling, so a fast swipe starts the exit
     * already moving and a slow release gets no jolt it did not ask for.
     */
    fun handover(velocity: Float) {
        if (exiting) return
        exiting = true
        exitFrom = lift
        exitStartUp = dragProgress()
        // Always further up than the finger already took it.
        exitTo = minOf(
            exitFrom - (if (capsuleHeightPx > 0) capsuleHeightPx.toFloat() else openThreshold),
            -openThreshold * 2f,
        )
        settleLift(
            target = exitTo,
            spec = spring(dampingRatio = 0.9f, stiffness = 520f),
            initialVelocity = velocity,
        )
        scope.launch {
            delay(HANDOFF_NAVIGATE_MS)
            onClick()
        }
    }

    val dragModifier = Modifier
        .draggable(
            state = rememberDraggableState { delta ->
                lift = if (delta > 0f) {
                    // Down past the rest position: hard stop, the capsule
                    // cannot be dragged into the tab bar.
                    (lift + delta).coerceAtMost(0f)
                } else {
                    // Up past the threshold the finger meets growing resistance
                    // instead of a wall, so an over-shoot still feels like the
                    // capsule is being pulled, not blocked.
                    val over = lift + delta
                    if (over < -openThreshold) {
                        -openThreshold + (over + openThreshold) * 0.45f
                    } else {
                        over
                    }.coerceAtLeast(-openThreshold * 2f)
                }
            },
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
                if (lift < -openThreshold || velocity < -900f) {
                    handover(velocity)
                } else {
                    // Slightly under-damped on the way back, and carrying the
                    // release velocity, so the settle continues the gesture
                    // instead of restarting it: the capsule breathes back to
                    // rest rather than snapping flat.
                    settleLift(
                        target = 0f,
                        spec = spring(dampingRatio = 0.78f, stiffness = 520f),
                        initialVelocity = velocity,
                    )
                }
            },
        )
        .graphicsLayer {
            translationY = lift
            // One continuous fade progress. While dragging it is a fraction of
            // the travel, so the capsule only dims slightly; from the moment of
            // release it climbs from exactly where the drag left it to fully
            // gone. Renormalising against the exit distance instead snapped the
            // alpha at release, which read as a jolt.
            val up = if (exiting) {
                val travel = (exitFrom - exitTo).coerceAtLeast(1f)
                val out = ((exitFrom - lift) / travel).coerceIn(0f, 1f)
                exitStartUp + out * (1f - exitStartUp)
            } else {
                dragProgress()
            }
            this.alpha = 1f - up
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
            .then(glassNavBackdrop(Modifier, hazeState, RoundedCornerShape(26.dp)))
            // Tap opens the player through the same handover the drag uses, so
            // both gestures fade the capsule out while the sheet rises.
            .combinedClickable(
                role = Role.Button,
                onClick = { handover(0f) },
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
                .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                artworkUri = track.artworkUri,
                title = track.title,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(12.dp),
                targetPx = 256,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = SpartialType.rowTitle.copy(fontSize = 16.sp),
                    color = SpartialWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = SpartialType.rowMeta.copy(fontSize = 14.sp, color = SpartialWhite.copy(alpha = 0.60f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Previous, play/pause and next, plain white filled glyphs at
            // ~30dp - no circle behind the play.
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clickable(role = Role.Button, onClick = onPrevious),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.action_previous),
                    tint = SpartialWhite,
                    modifier = Modifier.size(30.dp),
                )
            }
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = SpartialWhite,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clickable(role = Role.Button, onClick = onToggle),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.action_pause else R.string.action_play,
                            ),
                            tint = SpartialWhite,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clickable(role = Role.Button, onClick = onNext),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.action_next),
                    tint = SpartialWhite,
                    modifier = Modifier.size(30.dp),
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
