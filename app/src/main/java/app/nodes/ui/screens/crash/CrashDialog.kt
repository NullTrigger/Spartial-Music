/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.crash

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nodes.R
import app.nodes.core.crash.CrashLogFile
import app.nodes.core.crash.CrashShare
import app.nodes.core.crash.CrashStore
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesWhite

/**
 * "Spartial Music crashed last time", shown once on the launch after a crash.
 *
 * Reading the file is I/O, so it happens in an effect rather than during
 * composition; until it lands the dialog still offers Dismiss, which is the one
 * action that never depends on the file being readable.
 */
@Composable
fun CrashDialog(
    report: CrashLogFile?,
    onView: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (report == null) return

    val context = LocalContext.current
    // Read in composition: LocalContext is not configuration-aware, so
    // querying it from a click lambda can hand back a stale string.
    val shareSubject = stringResource(R.string.crash_share_subject)
    val shareTitle = stringResource(R.string.crash_share_title)
    val savedMessage = stringResource(R.string.crash_saved_downloads)
    val failedMessage = stringResource(R.string.crash_save_failed)
    var text by remember(report.name) { mutableStateOf<String?>(null) }

    LaunchedEffect(report.name) {
        text = CrashStore.read(report.file)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF15171B),
        title = { Text(stringResource(R.string.crash_last_time_title), color = NodesWhite) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.crash_last_time_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NodesGrey,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = report.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = NodesGrey.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onView) {
                        Text(stringResource(R.string.crash_view))
                    }
                    TextButton(
                        enabled = text != null,
                        onClick = {
                            val body = text ?: return@TextButton
                            CrashShare.share(
                                context = context,
                                report = body,
                                subject = shareSubject,
                                title = shareTitle,
                            )
                        },
                    ) {
                        Text(stringResource(R.string.crash_share))
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    TextButton(
                        enabled = text != null,
                        onClick = {
                            val body = text ?: return@TextButton
                            val saved = CrashShare.saveToDownloads(context, report.name, body)
                            Toast.makeText(
                                context,
                                if (saved) savedMessage else failedMessage,
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                    ) {
                        Text(stringResource(R.string.crash_save_downloads))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.crash_dismiss))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}
