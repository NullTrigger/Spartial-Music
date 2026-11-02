/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.crash

import android.content.Context
import android.os.Build
import android.os.Process
import app.nodes.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The uncaught-exception handler, modelled on Glossy's `CrashHandler`.
 *
 * One deliberate difference: Glossy launches its crash page from the dying
 * process. Nodes writes the report first and shows it on the next launch,
 * because a crash on the main thread leaves no working looper to draw an
 * activity - the saved copy is the only reliable one.
 *
 * The handler runs on the crashing thread with the process about to die, so:
 *  - everything is synchronous (no coroutine, no callback),
 *  - every step is wrapped, because a failure here would replace the real crash
 *    with a fake one,
 *  - the previous handler is always called afterwards, so Android still shows
 *    its own "app has stopped" behaviour and the process ends normally.
 *
 * Nothing is uploaded. Reports stay in `files/crash_logs/` until the user shares
 * or deletes them, and anything that looks like a credential is stripped before
 * the text reaches disk.
 */
object CrashReport {

    /** Mirrors the "Save crash reports" toggle; read from the dying thread. */
    @Volatile
    var enabled: Boolean = true

    private const val LOGCAT_LINES = "300"
    /** 56 `=` characters; a literal so it can stay a compile-time constant. */
    private const val SECTION =
        "========================================================"

    /** Installs the process-wide handler; safe to call more than once. */
    fun install(context: Context) {
        val app = context.applicationContext
        AppLogger.init(app)
        val fallback = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                AppLogger.e("Crash", "Uncaught exception on ${thread.name}", error)
                if (enabled) {
                    val report = describe(thread, error)
                    CrashStore.write(app, report)?.let { CrashStore.markPending(app, it) }
                }
            }
            // Let the platform write its own logcat entry and end the process.
            fallback?.uncaughtException(thread, error)
        }
    }

    /** A caught-but-serious failure, reported the same way but never "pending". */
    fun recordNonFatal(context: Context, error: Throwable, origin: String) {
        if (!enabled) return
        runCatching {
            val report = describe(Thread.currentThread(), error, nonFatalOrigin = origin)
            CrashStore.write(context.applicationContext, report)
        }
    }

    /** The newest report, for the crash page. Null when none was ever saved. */
    fun saved(context: Context): String? =
        CrashStore.latest(context)?.let { CrashStore.read(it.file) }

    fun hasPending(context: Context): Boolean = CrashStore.hasPending(context)

    fun pendingReport(context: Context): CrashLogFile? = CrashStore.pendingReport(context)

    /** Marks the pending report as seen; the text stays available. */
    fun markSeen(context: Context) = CrashStore.markSeen(context)

    /**
     * Builds the text saved to disk. Public so it can be exercised without
     * crashing the process.
     */
    fun describe(thread: Thread, error: Throwable, nonFatalOrigin: String? = null): String =
        buildString {
            appendLine("Nodes crash report")
            appendLine(SECTION)
            appendLine()
            appendLine("Time: ${stamp(System.currentTimeMillis())}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build type: ${if (BuildConfig.DEBUG) "debug" else "release"}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Thread: ${thread.name}")
            appendLine("Screen: ${AppSnapshot.route ?: "unknown"}")
            appendLine("Player: ${AppSnapshot.player ?: "no playback yet"}")
            if (nonFatalOrigin != null) appendLine("Non-fatal: $nonFatalOrigin")
            appendLine()
            appendLine("Exception: ${safeDescription(error)}")
            appendLine()
            appendLine(SECTION)
            appendLine("Stack trace")
            appendLine(SECTION)
            appendLine()
            append(runCatching { error.stackTraceToString() }.getOrDefault("(stack trace unavailable)"))
            appendLine()
            appendLine(SECTION)
            appendLine("App log (last 300 lines)")
            appendLine(SECTION)
            appendLine()
            append(runCatching { AppLogger.tail(300) }.getOrDefault(""))
            appendLine()
            appendLine(SECTION)
            appendLine("Logcat (last $LOGCAT_LINES lines, filtered)")
            appendLine(SECTION)
            appendLine()
            append(runCatching { redact(logcatTail()) }.getOrDefault("(logcat unavailable)"))
        }

    /**
     * The process's own logcat, which is the only place a silenced exception or a
     * system warning shows up. `logcat -d -t 300 --pid=<pid>` needs no
     * permission for the app's own entries; a device that refuses it simply
     * yields an empty tail.
     */
    private fun logcatTail(): String {
        val out = StringBuilder()
        val process = runCatching {
            Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-t", LOGCAT_LINES, "--pid=${Process.myPid()}"),
            )
        }.getOrNull() ?: return ""

        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().use { stream ->
                    stream.forEachLine { line ->
                        if (out.length < 200_000) out.appendLine(line)
                    }
                }
            }
        }
        reader.isDaemon = true
        reader.start()
        // Never let a stuck logcat hold the dying process open.
        runCatching { reader.join(1_500L) }
        runCatching { process.destroy() }
        return out.toString()
    }

    /**
     * Removes anything that looks like a credential before the text is written.
     *
     * The logcat tail can contain request URLs, `Cookie:` headers and OAuth
     * tokens. A crash report is meant to be pasted into a chat, so it must never
     * carry a session cookie - even the app's own, and even when the user
     * explicitly shares it.
     */
    private fun redact(text: String): String {
        if (text.isEmpty()) return text
        var cleaned = SECRET_PAIR.replace(text) { match ->
            "${match.groupValues[1]}=[redacted]"
        }
        cleaned = BEARER.replace(cleaned, "Bearer [redacted]")
        cleaned = GOOGLE_KEY.replace(cleaned, "[redacted key]")
        return cleaned
    }

    private fun safeDescription(error: Throwable): String =
        runCatching { error.toString() }.getOrDefault(error.javaClass.name)

    private fun stamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(millis))

    /** `key=value` and `key: value` pairs for anything credential-shaped. */
    private val SECRET_PAIR = Regex(
        "(?i)(sapisid|apisid|hsid|ssid|psid|__secure-3psid|authorization|cookie|set-cookie|" +
            "access_token|refresh_token|id_token|token|api[_-]?key|client[_-]?secret|password)" +
            "\\s*[:=]\\s*[^\\s,;\"'&]+",
    )

    private val BEARER = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/-]{8,}=*")

    /** Google API keys are recognisable, so they get a pattern of their own. */
    private val GOOGLE_KEY = Regex("AIza[0-9A-Za-z_\\-]{20,}")
}
