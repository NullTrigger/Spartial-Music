/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.crash

import android.content.Context
import android.os.Build
import app.nodes.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects the last uncaught exception and keeps it on the device.
 *
 * Modelled on Glossy's `CrashHandler` + `CrashActivity` pair: every uncaught
 * exception is turned into a readable report and shown on a page with a share
 * button. Nodes differs in one way on purpose: instead of launching the page
 * from the dying process, the report is written to disk first and the page opens
 * on the next launch. A crash on the main thread leaves no working looper to
 * draw an activity, so the saved copy is the only reliable one.
 *
 * Nothing is uploaded anywhere. The report stays in `files/crash` until it is
 * shared by hand or replaced by the next crash.
 */
object CrashReport {

    private const val DIR = "crash"
    private const val LAST_REPORT = "last_crash.txt"
    private const val PENDING = "pending"

    /** Installs the process-wide handler; safe to call more than once. */
    fun install(context: Context) {
        val app = context.applicationContext
        val fallback = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(app, describe(thread, error)) }
            // Let the platform print to logcat and end the process as usual.
            fallback?.uncaughtException(thread, error)
        }
    }

    /** The report the page shows. Null when the app has never crashed. */
    fun saved(context: Context): String? = runCatching {
        val file = lastFile(context)
        if (!file.isFile) null else file.readText().takeIf { it.isNotBlank() }
    }.getOrNull()

    /** True when a report was written and has not been shown yet. */
    fun hasPending(context: Context): Boolean =
        runCatching { pendingFile(context).isFile }.getOrDefault(false)

    /** Marks the pending report as seen; the text stays available. */
    fun markSeen(context: Context) {
        runCatching { pendingFile(context).delete() }
    }

    /** Builds the text shown on the crash page. Public for unit tests. */
    fun describe(thread: Thread, error: Throwable): String = buildString {
        appendLine("Nodes crash report")
        appendLine("=".repeat(56))
        appendLine()
        appendLine("Time: ${stamp(System.currentTimeMillis())}")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
            if (BuildConfig.DEBUG) " debug" else "")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Thread: ${thread.name}")
        appendLine()
        appendLine("=".repeat(56))
        appendLine("Stack trace")
        appendLine("=".repeat(56))
        appendLine()
        append(error.stackTraceToString())
    }

    /** Writes the report and marks it unread. Never throws. */
    private fun save(context: Context, report: String) {
        val dir = dir(context)
        if (!dir.isDirectory && !dir.mkdirs()) return
        File(dir, LAST_REPORT).writeText(report)
        // An empty marker is enough: the timestamp lives in the report.
        File(dir, PENDING).writeText(stamp(System.currentTimeMillis()))
    }

    private fun stamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(millis))

    private fun dir(context: Context): File = File(context.filesDir, DIR)

    private fun lastFile(context: Context): File = File(dir(context), LAST_REPORT)

    private fun pendingFile(context: Context): File = File(dir(context), PENDING)
}
