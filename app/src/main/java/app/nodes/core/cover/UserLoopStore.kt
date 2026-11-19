/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.cover

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The user's own video loops, stored app-private under `files/covers`.
 *
 * Only files the user picks through the system picker ever land here - never
 * URLs, never downloads. A single JSON sidecar (`index.json`) maps track id
 * to file name, so this feature costs no database migration. Size policy:
 * 20 MB per file, 200 MB total, oldest first out when the cap is reached.
 */
@Singleton
class UserLoopStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir = File(context.filesDir, "covers")
    private val indexFile = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true }

    /** The loop file for [trackId], or null. */
    fun fileFor(trackId: String): File? {
        val name = readIndex()[trackId] ?: return null
        val file = File(dir, name)
        return if (file.isFile && file.length() > 0L) file else null
    }

    /**
     * Copies the picked document into private storage. Returns false when the
     * file is not an mp4/webm, is over the per-file cap, or cannot be read -
     * the caller just leaves the generated cover in place.
     */
    suspend fun setCover(trackId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri)
        if (mime != "video/mp4" && mime != "video/webm") return@withContext false
        val extension = if (mime == "video/webm") "webm" else "mp4"
        val target = File(dir, safeName(trackId) + "." + extension)
        val ok = runCatching {
            dir.mkdirs()
            val source = context.contentResolver.openInputStream(uri) ?: error("no stream")
            source.use { input ->
                val size = availableBytes(input)
                if (size > MAX_FILE_BYTES) error("too large")
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FILE_BYTES) error("too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
            true
        }.getOrDefault(false)
        if (!ok) {
            target.delete()
            return@withContext false
        }
        evictIfNeeded(keep = target.name)
        writeIndex(readIndex() + (trackId to target.name))
        true
    }

    suspend fun removeCover(trackId: String) = withContext(Dispatchers.IO) {
        val index = readIndex()
        val name = index.remove(trackId) ?: return@withContext
        File(dir, name).delete()
        writeIndex(index)
    }

    /** Total bytes held; for the diagnostics line and the eviction check. */
    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        dir.listFiles()?.sumOf { if (it.isFile && it.name != "index.json") it.length() else 0L }
            ?: 0L
    }

    private fun evictIfNeeded(keep: String) {
        val files = dir.listFiles()?.filter {
            it.isFile && it.name != "index.json" && it.name != keep
        } ?: return
        var total = files.sumOf { it.length() } + File(dir, keep).length()
        if (total <= MAX_TOTAL_BYTES) return
        // Oldest first: lastModified is the write time, files are never touched.
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_TOTAL_BYTES) break
            total -= file.length()
            file.delete()
            removeFromIndex(file.name)
        }
    }

    private fun readIndex(): Map<String, String> = runCatching {
        val root = json.parseToJsonElement(indexFile.readText()) as? JsonObject ?: return emptyMap()
        root.mapNotNull { (key, value) ->
            val name = (value as? JsonPrimitive)?.contentOrNull
            name?.let { key to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun writeIndex(index: Map<String, String>) {
        runCatching {
            dir.mkdirs()
            indexFile.writeText(
                json.encodeToString(
                    kotlinx.serialization.json.JsonObject.serializer(),
                    buildJsonObject {
                        putJsonObject("tracks") {
                            index.forEach { (trackId, name) -> put(trackId, name) }
                        }
                    }.getJsonObject("tracks") ?: buildJsonObject { },
                ),
            )
        }
    }

    private fun removeFromIndex(fileName: String) {
        writeIndex(readIndex().filterValues { it != fileName })
    }

    private fun availableBytes(input: InputStream): Long = runCatching { input.available().toLong() }
        .getOrDefault(0L)

    private fun safeName(trackId: String): String =
        trackId.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .take(80)

    private companion object {
        const val MAX_FILE_BYTES = 20L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 200L * 1024 * 1024
    }
}

private fun kotlinx.serialization.json.JsonObject.getJsonObject(key: String) =
    this[key] as? kotlinx.serialization.json.JsonObject
