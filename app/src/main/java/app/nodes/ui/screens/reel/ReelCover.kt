/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.reel

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.nodes.core.cover.AnimatedCover
import app.nodes.core.model.Track
import app.nodes.ui.components.Artwork
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.rememberArtworkTint

/**
 * The Reel's cover stack, bottom to top:
 * 1. the base: the track's artwork, or the generated cover (three slow
 *    colour blobs over a gradient) when there is none;
 * 2. the animated cover, when one exists, is allowed, and the device may
 *    decode it - crossfaded in only after the first frame, so there is never
 *    a black flash;
 * 3. the design's scrim: ~70% dark at the status area, clear in the middle,
 *    ~92% at the bottom.
 *
 * Reduce-motion (animator scale 0) never starts the video player at all.
 */
@Composable
fun ReelCover(
    track: Track,
    cover: AnimatedCover?,
    allowAnimated: Boolean,
    pageVisible: Boolean,
    modifier: Modifier = Modifier,
    onCoverFailure: () -> Unit = {},
) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val tint = rememberArtworkTint(track.artworkUri, track.title)

    // One failure is remembered for this track for the session: no retry
    // loop, the generated cover simply stays.
    var failed by remember(track.id) { mutableStateOf(false) }
    var firstFrame by remember(track.id) { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(true) }

    val showVideo = cover != null && allowAnimated && !reduceMotion && !failed

    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxPixels = (constraints.maxWidth * constraints.maxHeight).toInt()

        // Base layer.
        if (track.artworkUri.isNullOrBlank()) {
            GeneratedCover(tint = tint, animate = !reduceMotion, modifier = Modifier.fillMaxSize())
        } else {
            Artwork(
                artworkUri = track.artworkUri,
                title = track.title,
                modifier = Modifier.fillMaxSize(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                targetPx = 900,
                showInitial = false,
            )
        }

        // Video layer, crossfaded in on the first rendered frame.
        if (showVideo && cover != null) {
            val player = remember(cover.url) {
                ReelCoverPlayer.create(
                    context = context,
                    maxPixels = maxPixels,
                    onFirstFrame = { firstFrame = true },
                    onError = {
                        failed = true
                        onCoverFailure()
                    },
                )
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, cover.url) {
                player.load(cover)
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) foreground = false
                    if (event == Lifecycle.Event.ON_START) foreground = true
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    player.release()
                }
            }
            LaunchedEffect(pageVisible, foreground) {
                player.setPlaying(pageVisible && foreground)
            }
            val videoAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (firstFrame) 1f else 0f,
                animationSpec = tween(240, easing = LinearEasing),
                label = "coverFade",
            )
            AndroidView(
                factory = { viewContext ->
                    android.view.TextureView(viewContext).also { player.attach(it) }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = videoAlpha },
            )
        }

        ReelScrim(modifier = Modifier.fillMaxSize())
    }
}

/** The design's vertical scrim: 70% top, clear middle band, 92% bottom. */
@Composable
fun ReelScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to SpartialBlack.copy(alpha = 0.70f),
                0.30f to Color.Transparent,
                0.58f to Color.Transparent,
                0.86f to SpartialBlack.copy(alpha = 0.92f),
                1f to SpartialBlack.copy(alpha = 0.94f),
            ),
        ),
    )
}

/**
 * The generated cover (design 3e): three blurred colour blobs derived from
 * the artwork tint, drifting on long loops over a base gradient. Always
 * available, needs no network, and stops dead under reduce-motion.
 */
@Composable
fun GeneratedCover(
    tint: Color,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val base = tint.copy(alpha = 0.9f)
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    base.copy(alpha = 0.55f),
                    SpartialBlack.copy(alpha = 0.92f),
                    SpartialBlack,
                ),
            ),
        ),
    ) {
        val transition = rememberInfiniteTransition(label = "blobs")
        fun drift(seed: Int, range: Float): Float {
            if (!animate) return seed * 13f
            val anim by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 16_000 + seed * 4_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "blob$seed",
            )
            return (anim * 2f - 1f) * range + seed * 13f
        }
        val shifted = remember(tint) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(tint.toArgb(), hsv)
            hsv[0] = (hsv[0] + 40f) % 360f
            Color(android.graphics.Color.HSVToColor(hsv))
        }
        val blobs = listOf(
            tint to drift(0, 0.22f),
            shifted to drift(1, 0.3f),
            Color.White to drift(2, 0.18f),
        )
        blobs.forEachIndexed { index, (color, offset) ->
            Box(
                Modifier
                    .size(if (index == 2) 260.dp else 340.dp)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationX = offset * 100f
                        translationY = (offset * (index + 1) * 60f) - 120f * index
                        transformOrigin = TransformOrigin.Center
                    }
                    .background(
                        Brush.radialGradient(
                            listOf(
                                color.copy(alpha = if (index == 2) 0.10f else 0.34f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
    }
}

private fun Color.toArgb(): Int = androidx.compose.ui.graphics.ColorUtils.toArgb(this, 1f)
