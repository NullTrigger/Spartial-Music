/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.diagnostics

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.BuildConfig
import app.nodes.R
import app.nodes.core.crash.CrashLogFile
import app.nodes.core.crash.CrashShare
import app.nodes.ui.components.EmptyState
import app.nodes.ui.components.MessageBanner
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.glass
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialAccent
import app.nodes.ui.theme.SpartialWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings -> Diagnostics -> Crash reports.
 *
 * Lists every report the handler has saved, opens one as plain text, and offers
 * the three things a report is for: read it, share it, or throw it away. The
 * toggle at the top is the user's opt-out; turning it off stops new files being
 * written (the handler still logs to logcat).
 */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(SpartialBlack),
    ) {
        PageHeader(
            title = stringResource(R.string.diagnostics_title),
            back = onBack,
        )

        state.message?.let { text ->
            MessageBanner(message = text, onDismiss = viewModel::clearMessage)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "save-toggle") {
                ToggleRow(
                    title = stringResource(R.string.diagnostics_save_reports),
                    subtitle = stringResource(R.string.diagnostics_save_reports_subtitle),
                    checked = state.saveEnabled,
                    onCheckedChange = viewModel::setSaveEnabled,
                )
            }

            item(key = "actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = viewModel::copyLast) {
                        Text(stringResource(R.string.diagnostics_copy_last))
                    }
                    TextButton(
                        enabled = state.reports.isNotEmpty(),
                        onClick = viewModel::deleteAll,
                    ) {
                        Text(stringResource(R.string.diagnostics_delete_all))
                    }
                }
            }

            if (state.reports.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.diagnostics_title),
                        description = stringResource(R.string.diagnostics_empty),
                    )
                }
            }

            items(items = state.reports, key = { it.name }) { report ->
                ReportRow(
                    report = report,
                    onOpen = { viewModel.open(report) },
                    onDelete = { viewModel.delete(report) },
                )
            }

            if (BuildConfig.DEBUG) {
                item(key = "test-crash") {
                    DebugCrashRow(onCreate = viewModel::forceCrash)
                }
            }
        }
    }

    val openedText = state.openedText
    val openedName = state.openedName
    if (openedName != null && openedText != null) {
        val shareSubject = stringResource(R.string.crash_share_subject)
        val shareTitle = stringResource(R.string.crash_share_title)
        AlertDialog(
            onDismissRequest = viewModel::closeOpened,
            containerColor = Color(0xFF15171B),
            title = {
                Text(
                    text = openedName,
                    style = MaterialTheme.typography.labelMedium,
                    color = SpartialWhite,
                )
            },
            text = {
                Box(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = openedText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        ),
                        color = SpartialWhite,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        CrashShare.share(
                            context = context,
                            report = openedText,
                            subject = shareSubject,
                            title = shareTitle,
                        )
                    },
                ) {
                    Text(stringResource(R.string.diagnostics_share))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeOpened) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = SpartialWhite,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SpartialGrey,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = SpartialAccent),
        )
    }
}

@Composable
private fun ReportRow(
    report: CrashLogFile,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .glass(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = formatStamp(report.savedAt),
                style = MaterialTheme.typography.titleSmall,
                color = SpartialWhite,
            )
            Text(
                text = report.summary,
                style = MaterialTheme.typography.bodySmall,
                color = SpartialGrey,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onDelete) {
            Text(
                text = stringResource(R.string.diagnostics_delete),
                style = MaterialTheme.typography.labelMedium,
                color = SpartialGrey,
            )
        }
    }
}

@Composable
private fun DebugCrashRow(onCreate: () -> Unit) {
    val context = LocalContext.current
    val testLabel = stringResource(R.string.diagnostics_test_crash)
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            text = stringResource(R.string.diagnostics_test_crash_hint),
            style = MaterialTheme.typography.bodySmall,
            color = SpartialGrey,
        )
        TextButton(
            onClick = {
                Toast.makeText(
                    context,
                    testLabel,
                    Toast.LENGTH_SHORT,
                ).show()
                onCreate()
            },
        ) {
            Text(
                text = stringResource(R.string.diagnostics_test_crash),
                color = SpartialAccent,
            )
        }
    }
}

private fun formatStamp(millis: Long): String {
    val stamp = if (millis > 0L) millis else System.currentTimeMillis()
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(stamp))
}
