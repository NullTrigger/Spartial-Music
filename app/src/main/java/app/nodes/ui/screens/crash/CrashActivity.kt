/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nodes.R
import app.nodes.core.crash.CrashReport
import app.nodes.core.crash.CrashShare
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.glass
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesTheme
import app.nodes.ui.theme.NodesWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The full crash page, in the spirit of Glossy's `CrashActivity`.
 *
 * Opened from the "crashed last time" dialog and from Diagnostics. It shows one
 * report large enough to read, and offers the two actions that matter on a
 * phone: share it, or copy it somewhere.
 */
class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val report = intent.getStringExtra(EXTRA_REPORT) ?: CrashReport.saved(this)
        CrashReport.markSeen(this)

        setContent {
            NodesTheme {
                CrashScreen(
                    report = report,
                    onClose = { finish() },
                    onShare = { text ->
                        CrashShare.share(
                            context = this,
                            report = text,
                            subject = getString(R.string.crash_share_subject),
                            title = getString(R.string.crash_share_title),
                        )
                    },
                    onCopy = { text -> copy(text) },
                    onSave = { text -> save(text) },
                )
            }
        }
    }

    private fun copy(report: String) {
        runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText(getString(R.string.crash_title), report),
            )
            Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun save(report: String) {
        val name = runCatching {
            "nodes_crash_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"
        }.getOrDefault("nodes_crash.txt")
        val saved = CrashShare.saveToDownloads(this, name, report)
        Toast.makeText(
            this,
            getString(if (saved) R.string.crash_saved_downloads else R.string.crash_save_failed),
            Toast.LENGTH_LONG,
        ).show()
    }

    companion object {
        /** Optional: a report handed over directly instead of the saved one. */
        const val EXTRA_REPORT = "app.nodes.extra.CRASH_REPORT"
    }
}

@Composable
fun CrashScreen(
    report: String?,
    onClose: () -> Unit,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    onSave: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        PageHeader(
            title = stringResource(R.string.crash_title),
            back = onClose,
        )

        Text(
            text = stringResource(
                if (report == null) R.string.crash_none else R.string.crash_description,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = NodesGrey,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        if (report == null) {
            Spacer(Modifier.height(24.dp))
        } else {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .glass(RoundedCornerShape(22.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text(
                        text = report,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        ),
                        color = NodesWhite,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { onCopy(report) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.crash_copy))
                    }
                    OutlinedButton(
                        onClick = { onSave(report) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.crash_save_downloads))
                    }
                }
                Button(
                    onClick = { onShare(report) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.crash_share))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
