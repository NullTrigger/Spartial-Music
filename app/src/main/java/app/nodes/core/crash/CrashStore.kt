/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.crash

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One saved report: its file, when it happened, and a one-line summary. */
data class CrashLogFile(
    val file: File,
    val savedAt: Long,
    val summary: String,
) {
    val name: String get() = file.name
}

/**
 * Crash reports on disk, in `Android/data/<package>/files/crash_logs/`.
 *
 * That directory needs no storage permission and is readable by a file manager
 * on Android 11+ through the system's own "Android/data" picker, which is the
 * trade-off for not asking for broad storage access. "Save to Downloads" writes
 * a copy somewhere easier to find via MediaStore instead.
 *
 * Reports are plain text, named `crash_YYYY-MM-DD_HH-mm-ss.txt`, and the newest
 * 20 are kept so a crash loop cannot fill the device.
 */
object CrashStore {

    private const val DIR = "crash_logs"
    private const val PENDING = "pending_crash.txt"
    private const val MAX_REPORTS = 20
    private const val SUMMARY_BYTES = 2_000
    private const val SUMMARY_MARKER = "Exception:"

    fun dir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, DIR)

    /** Writes [report] as a new dated file. Null when it could not be saved. */
    fun write(context: Context, report: String): File? = runCatching {
        val dir = dir(context)
        if (!dir.isDirectory && !dir.mkdirs()) return@runCatching null
        val stamp = fileNameStamp(System.currentTimeMillis())
        var file = File(dir, "crash_$stamp.txt")
        var suffix = 1
        // Two crashes inside one second still deserve two files.
        while (file.exists()) {
            file = File(dir, "crash_${stamp}_$suffix.txt")
            suffix++
        }
        file.writeText(report)
        trim(context)
        file
    }.getOrNull()

    /** Newest first. */
    fun reports(context: Context): List<CrashLogFile> = runCatching {
        dir(context).listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash_") && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }
            ?.map { file ->
                CrashLogFile(
                    file = file,
                    savedAt = file.lastModified(),
                    summary = summary(file),
                )
            }
            .orEmpty()
    }.getOrDefault(emptyList())

    fun read(file: File): String? = runCatching {
        if (file.isFile) file.readText() else null
    }.getOrNull()

    fun delete(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

    fun deleteAll(context: Context) {
        runCatching { reports(context).forEach { delete(it.file) } }
    }

    fun latest(context: Context): CrashLogFile? = reports(context).firstOrNull()

    // ---- pending marker ----------------------------------------------------

    /** Remembers that a report has not been shown to the user yet. */
    fun markPending(context: Context, file: File) {
        runCatching {
            val dir = dir(context)
            if (!dir.isDirectory && !dir.mkdirs()) return@runCatching
            File(dir, PENDING).writeText(file.name)
        }
    }

    fun hasPending(context: Context): Boolean = pendingFile(context).isFile

    /** The unseen report, or null once it was dismissed. */
    fun pendingReport(context: Context): CrashLogFile? = runCatching {
        val marker = pendingFile(context)
        if (!marker.isFile) return@runCatching null
        val name = marker.readText().trim()
        val file = File(dir(context), name)
        if (name.isBlank() || !file.isFile) {
            marker.delete()
            return@runCatching null
        }
        CrashLogFile(file, file.lastModified(), summary(file))
    }.getOrNull()

    fun markSeen(context: Context) {
        runCatching { pendingFile(context).delete() }
    }

    // ---- export ------------------------------------------------------------

    /**
     * Copies a report to `Downloads/Spartial Music/` through MediaStore, so it shows up in
     * a file manager without the app holding storage permissions. Returns false
     * on API < 29, where that route does not exist, and on any failure.
     */
    fun saveToDownloads(context: Context, fileName: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return saveToDownloadsQ(context, fileName, text)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloadsQ(context: Context, fileName: String, text: String): Boolean =
        runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/Spartial Music",
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(text.toByteArray())
            } ?: return@runCatching false
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            true
        }.getOrDefault(false)

    // ---- helpers -----------------------------------------------------------

    private fun trim(context: Context) {
        runCatching {
            val all = dir(context).listFiles()
                ?.filter { it.isFile && it.name.startsWith("crash_") }
                ?.sortedByDescending { it.name }
                .orEmpty()
            all.drop(MAX_REPORTS).forEach { it.delete() }
        }
    }

    private fun summary(file: File): String = runCatching {
        val head = file.inputStream().bufferedReader().use { reader ->
            val buffer = CharArray(SUMMARY_BYTES)
            val read = reader.read(buffer)
            if (read <= 0) "" else String(buffer, 0, read)
        }
        head.lineSequence()
            .firstOrNull { it.startsWith(SUMMARY_MARKER) }
            ?.removePrefix(SUMMARY_MARKER)
            ?.trim()
            ?.take(140)
            ?: "Crash report"
    }.getOrDefault("Crash report")

    private fun pendingFile(context: Context): File = File(dir(context), PENDING)

    private fun fileNameStamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(millis))
}
