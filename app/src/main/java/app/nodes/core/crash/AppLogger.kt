/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.crash

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small, self-rotating log of everything that went wrong but did not kill the
 * app: a lyrics provider timing out, a stream that refused to resolve, a queue
 * that could not be restored.
 *
 * It exists because the interesting failures are the caught ones. Without a
 * running log the only evidence after a bug report is the crash itself, and
 * half the time the crash is the *second* thing to fail.
 *
 * Everything here is wrapped: a logger that can throw is worse than no logger.
 * The file lives next to the crash reports in the app's external files dir, is
 * capped at ~1 MB, and is trimmed from the oldest lines so a long session cannot
 * fill the disk.
 */
object AppLogger {

    private const val FILE_NAME = "app_log.txt"
    private const val MAX_BYTES = 1_000_000L
    private const val KEEP_AFTER_TRIM = 600_000L
    private const val MAX_MESSAGE = 4_000

    private val lock = Any()
    private val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logFile: File? = null

    /** Safe to call more than once; the first call wins. */
    fun init(context: Context) {
        synchronized(lock) {
            if (logFile != null) return
            runCatching {
                val base = context.getExternalFilesDir(null) ?: context.filesDir
                logFile = File(base, FILE_NAME)
            }
        }
    }

    fun d(tag: String, message: String) = write("D", tag, message, null)

    fun i(tag: String, message: String) = write("I", tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    /** The last [maxLines] lines, for the tail of a crash report. */
    fun tail(maxLines: Int = 300): String = runCatching {
        val file = logFile ?: return@runCatching ""
        if (!file.isFile) return@runCatching ""
        file.readLines().takeLast(maxLines.coerceAtLeast(0)).joinToString("\n")
    }.getOrDefault("")

    fun clear() {
        runCatching { logFile?.delete() }
    }

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        runCatching {
            val file = logFile ?: return@runCatching
            val body = message.take(MAX_MESSAGE)
            val line = buildString {
                append(synchronized(timestamp) { timestamp.format(Date()) })
                append(' ').append(level).append('/').append(tag).append(": ").append(body)
                if (error != null) {
                    append('\n').append(error.stackTraceToString().take(MAX_MESSAGE))
                }
                append('\n')
            }
            synchronized(lock) {
                file.appendText(line)
                if (file.length() > MAX_BYTES) trim(file)
            }
        }
    }

    /** Drops the oldest lines until the file is comfortably under the cap. */
    private fun trim(file: File) {
        runCatching {
            val keep = file.readText().takeLast(KEEP_AFTER_TRIM.toInt())
            // Cut at a line boundary so the file never starts mid-stack-trace.
            val cut = keep.indexOf('\n')
            file.writeText(if (cut >= 0) keep.substring(cut + 1) else keep)
        }
    }
}
