/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nodes.R
import app.nodes.core.model.SourceErrorType
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialType
import app.nodes.ui.theme.SpartialWhite

/** Friendly empty state used by every redesigned screen. */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = SpartialType.sectionTitle,
            color = SpartialWhite,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = SpartialGrey,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            PillButton(
                text = actionLabel,
                onClick = onAction,
                style = PillStyle.Glass,
            )
        }
    }
}

/** A provider failure with the shared human-readable explanation. */
@Composable
fun ErrorState(
    error: SourceErrorType?,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.source_error),
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .glass(RoundedCornerShape(22.dp), scrim = 0.35f)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = SpartialGrey,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = SpartialWhite,
            textAlign = TextAlign.Center,
        )
        Text(
            text = sourceErrorMessage(error),
            style = MaterialTheme.typography.bodySmall,
            color = SpartialGrey,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(4.dp))
            PillButton(
                text = stringResource(R.string.action_retry),
                onClick = onRetry,
                style = PillStyle.Glass,
            )
        }
    }
}

/** Compact loading indicator that does not shift the layout when data lands. */
@Composable
fun LoadingRows(
    modifier: Modifier = Modifier,
    count: Int = 4,
) {
    val transition = rememberInfiniteTransition(label = "skeletons")
    val alpha by transition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = alpha + 0.04f),
                                    Color.White.copy(alpha = alpha),
                                ),
                            ),
                            RoundedCornerShape(14.dp),
                        ),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.64f)
                            .height(12.dp)
                            .background(Color.White.copy(alpha = alpha + 0.04f), CircleShape),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.4f)
                            .height(10.dp)
                            .background(Color.White.copy(alpha = alpha), CircleShape),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                color = SpartialGrey,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.label_loading),
                style = MaterialTheme.typography.labelMedium,
                color = SpartialGrey,
            )
        }
    }
}

/** A one-line status used at the end of lists. */
@Composable
fun InlineStatus(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = SpartialGrey,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
    )
}
