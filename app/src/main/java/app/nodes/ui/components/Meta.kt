/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nodes.R
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesGreyDim

/** "48 min", "1 hr 42 min"; empty when the durations are unknown. */
@Composable
fun runtimeLabel(runtimeMs: Long): String {
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

/** A "10 tracks · 42 min" run, separated by small dots. */
@Composable
fun MetaRow(
    parts: List<String>,
    modifier: Modifier = Modifier,
) {
    val visible = parts.filter { it.isNotBlank() }
    if (visible.isEmpty()) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        visible.forEachIndexed { index, part ->
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
