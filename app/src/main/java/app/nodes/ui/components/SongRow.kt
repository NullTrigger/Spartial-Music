/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nodes.R
import app.nodes.core.model.Track
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialGreyDim
import app.nodes.ui.theme.SpartialType
import app.nodes.ui.theme.SpartialWhite

/**
 * The list row used by Search, Library, Queue and detail screens.
 *
 * The row is one large click target; the download, star and more controls are
 * separate 44dp targets so actions never steal the row tap. Download is
 * deliberately disabled for tracks a provider does not permit saving (every
 * YouTube mode is in that set) rather than pretending to save them.
 */
@Composable
fun SongRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    position: Int? = null,
    isLiked: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    downloadEnabled: Boolean = false,
    showDownload: Boolean = true,
    showStar: Boolean = true,
    active: Boolean = false,
    activePlaying: Boolean = false,
    activeTint: Color = SpartialWhite,
    supportingText: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    menu: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (active) {
                    Modifier.background(activeTint.copy(alpha = 0.12f))
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 68.dp)
            .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (position != null) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                if (active && activePlaying) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = activeTint,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = position.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) activeTint else SpartialGreyDim,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
        }
        Artwork(
            artworkUri = track.artworkUri,
            title = track.title,
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(14.dp),
            targetPx = 256,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = SpartialType.rowTitle,
                color = if (active) activeTint else SpartialWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supportingText ?: track.artist,
                style = SpartialType.rowMeta,
                color = SpartialGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            if (showDownload) {
                val description = stringResource(
                    if (downloadEnabled) R.string.action_download else R.string.action_download_unavailable,
                )
                RowAction(
                    icon = Icons.Rounded.Download,
                    contentDescription = description,
                    enabled = downloadEnabled,
                    onClick = { onDownload?.invoke() },
                )
            }
            if (showStar) {
                RowAction(
                    icon = if (isLiked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = stringResource(
                        if (isLiked) R.string.action_unstar else R.string.action_star,
                    ),
                    enabled = onToggleLike != null,
                    onClick = { onToggleLike?.invoke() },
                    tint = if (isLiked) SpartialWhite else SpartialGrey,
                )
            }
            if (menu != null) {
                menu()
            }
        }
    }
}

@Composable
private fun RowAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = SpartialGrey,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else SpartialGreyDim.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Small "year • album" line for the queue, which shows more provenance. */
@Composable
fun trackSupportingLine(track: Track): String = buildString {
    append(track.artist)
    track.album?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
}
