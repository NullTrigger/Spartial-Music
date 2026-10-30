/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nodes.R
import app.nodes.core.model.SourceErrorType
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesWhite

/** Shared, human-readable explanation for provider failures. */
@Composable
fun sourceErrorMessage(type: SourceErrorType?): String = when (type) {
    SourceErrorType.NO_CONFIGURATION -> stringResource(R.string.source_needs_key)
    SourceErrorType.QUOTA_EXCEEDED -> stringResource(R.string.source_quota_exceeded)
    SourceErrorType.NETWORK -> stringResource(R.string.source_network_error)
    SourceErrorType.UNAUTHORIZED -> stringResource(R.string.source_unauthorized)
    SourceErrorType.NOT_FOUND -> stringResource(R.string.source_not_found)
    SourceErrorType.PARSE, SourceErrorType.UNKNOWN, null -> stringResource(R.string.source_error)
}

/** Floating one-line notice (quota, unavailable track, ...). */
@Composable
fun MessageBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .glass(RoundedCornerShape(20.dp), strong = true)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = NodesWhite,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = NodesGrey,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
