/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.core.content.FileProvider
import app.nodes.R
import app.nodes.core.crash.CrashReport
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.glass
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesTheme
import app.nodes.ui.theme.NodesWhite
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The crash page, in the spirit of Glossy's `CrashActivity`.
 *
 * It shows the report saved by [CrashReport] and lets the user copy it or share
 * it as a text file. Opened automatically on the first launch after a crash, and
 * from Settings at any time.
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
                    onShare = { text -> share(text) },
                    onCopy = { text -> copy(text) },
                )
            }
        }
    }

    private fun share(report: String) {
        val send = runCatching {
            val dir = File(cacheDir, "crash").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "nodes_crash_$stamp.txt")
            file.writeText(report)
            val uri = FileProvider.getUriForFile(this, "$packageName.crashreports", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, report)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_share_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrElse {
            // No provider, no file: the text is still shareable.
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, report)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_share_subject))
            }
        }
        runCatching {
            startActivity(Intent.createChooser(send, getString(R.string.crash_share_title)))
        }
    }

    private fun copy(report: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.crash_title), report))
        Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show()
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
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onCopy(report) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.crash_copy))
                }
                Button(
                    onClick = { onShare(report) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.crash_share))
                }
            }
        }
    }
}
