/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.nodes.R
import app.nodes.core.util.thumbnailCandidates
import app.nodes.ui.theme.ArtAccents
import app.nodes.ui.theme.SpartialBlack
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * Album art with a deterministic, generated fallback.
 *
 * Nothing in this repository ships copyrighted cover art: when a track has no
 * artwork (or is offline), we draw a stable gradient derived from its title.
 *
 * Every surface passes the pixel size it actually needs ([targetPx]); the URL is
 * rewritten to that size first, so a 60px provider thumbnail is never stretched
 * over a 430dp hero. See `core/util/Thumbnails.kt`.
 */
@Composable
fun Artwork(
    artworkUri: String?,
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    contentScale: ContentScale = ContentScale.Crop,
    targetPx: Int = 512,
    /** How long the image fades in, in ms. 0 fades instantly. */
    crossfadeMillis: Int = 0,
    /** Whether the generated placeholder shows the small app mark. */
    showInitial: Boolean = true,
) {
    val clipped = modifier.clip(shape)
    if (artworkUri.isNullOrBlank()) {
        GeneratedArt(title = title, modifier = clipped, showInitial = showInitial)
        return
    }
    RemoteArtwork(
        artworkUri = artworkUri,
        title = title,
        targetPx = targetPx,
        contentScale = contentScale,
        modifier = clipped,
        crossfade = true,
        crossfadeMillis = crossfadeMillis,
        showInitial = showInitial,
    )
}

/**
 * Loads the sharpest candidate, and draws the next one when the provider does
 * not have it (YouTube's `maxresdefault.jpg` is missing for many older videos).
 */
@Composable
private fun RemoteArtwork(
    artworkUri: String,
    title: String,
    targetPx: Int,
    contentScale: ContentScale,
    modifier: Modifier,
    crossfade: Boolean,
    crossfadeMillis: Int = 0,
    showInitial: Boolean = true,
) {
    val context = LocalContext.current
    val candidates = remember(artworkUri, targetPx) { thumbnailCandidates(artworkUri, targetPx) }
    val primary = candidates.firstOrNull() ?: return

    SubcomposeAsyncImage(
        model = imageRequest(context, primary, targetPx, crossfade, crossfadeMillis),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
        loading = { GeneratedArt(title = title, modifier = Modifier.fillMaxSize(), showInitial = showInitial) },
        error = {
            val fallback = candidates.getOrNull(1)
            if (fallback == null) {
                GeneratedArt(title = title, modifier = Modifier.fillMaxSize(), showInitial = showInitial)
            } else {
                SubcomposeAsyncImage(
                    model = imageRequest(context, fallback, targetPx, crossfade = false),
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        GeneratedArt(title = title, modifier = Modifier.fillMaxSize(), showInitial = showInitial)
                    },
                    error = {
                        GeneratedArt(title = title, modifier = Modifier.fillMaxSize(), showInitial = showInitial)
                    },
                )
            }
        },
    )
}

private fun imageRequest(
    context: Context,
    url: String,
    targetPx: Int,
    crossfade: Boolean,
    crossfadeMillis: Int = 0,
): ImageRequest {
    val builder = ImageRequest.Builder(context)
        // Resolving the request at the size the surface needs (instead of the
        // 60-120px the provider advertises) is what removes the blur.
        .size(targetPx)
        .data(url)
    // Only fade when a duration was asked for. The loader has crossfade off by
    // default: a list row that fades reads as slower than one that appears.
    if (crossfade && crossfadeMillis > 0) builder.crossfade(crossfadeMillis)
    return builder.build()
}

/**
 * Stable gradient plus a faint app mark for a track without artwork. The
 * mark is the launcher foreground at low opacity, so an empty or still-loading
 * cover reads as "Spartial Music" rather than as a stray letter.
 */
@Composable
fun GeneratedArt(
    title: String,
    modifier: Modifier = Modifier,
    showInitial: Boolean = true,
) {
    val seed = title.hashCode()
    val accent = ArtAccents[(seed ushr 1) % ArtAccents.size]
    val secondary = ArtAccents[((seed ushr 5) + 3) % ArtAccents.size]
    val angle = (seed % 4).toFloat()
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(accent, secondary, SpartialBlack.copy(alpha = 0.92f)),
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(
                    if (angle >= 2f) 1200f else 200f,
                    if (angle == 1f || angle == 3f) 900f else 1200f,
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (showInitial) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                alpha = 0.16f,
                modifier = Modifier.fillMaxSize(0.52f),
            )
        }
    }
}

/**
 * A blurred, darkened artwork layer behind a glass surface. This is the
 * backdrop that makes glass read as glass; it is deliberately cheap (one draw
 * of the same image, scaled up) so it works on low-end devices.
 */
@Composable
fun ArtworkBackdrop(
    artworkUri: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (artworkUri.isNullOrBlank()) {
            GeneratedArt(title = title, modifier = Modifier.fillMaxSize(), showInitial = false)
        } else {
            RemoteArtwork(
                artworkUri = artworkUri,
                title = title,
                // The backdrop is a full screen behind blurred controls, so
                // it does not need the sharpest frame: a 640px copy is
                // indistinguishable once it is scaled and dimmed, and it
                // arrives in a fraction of the bytes.
                targetPx = 640,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                crossfade = false,
                showInitial = false,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.45f),
                            SpartialBlack.copy(alpha = 0.86f),
                        ),
                    ),
                ),
        )
    }
}

/** A small rounded swatch used for playlist placeholders. */
@Composable
fun ArtworkSwatch(title: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 56.dp) {
    GeneratedArt(title = title, modifier = modifier.size(size).clip(RoundedCornerShape(14.dp)))
}
