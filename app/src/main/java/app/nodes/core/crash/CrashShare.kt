/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.crash

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sharing and exporting crash reports.
 *
 * Share copies the text into `cache/crash/` and hands it over through the
 * FileProvider declared in the manifest (`<package>.crashreports`). If the
 * provider is missing for any reason, the same text is still shareable through
 * `EXTRA_TEXT`, so a broken manifest entry degrades instead of failing.
 */
object CrashShare {

    fun share(context: Context, report: String, subject: String, title: String) {
        val send = runCatching {
            val dir = File(context.cacheDir, "crash").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "nodes_crash_$stamp.txt")
            file.writeText(report)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.crashreports",
                file,
            )
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, report)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrElse {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, report)
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
        }
        runCatching { context.startActivity(Intent.createChooser(send, title)) }
    }

    /** MediaStore copy in `Downloads/Nodes/`; false on API < 29 or on failure. */
    fun saveToDownloads(context: Context, fileName: String, report: String): Boolean =
        CrashStore.saveToDownloads(context, fileName, report)
}
