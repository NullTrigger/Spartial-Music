/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.database.DatabaseProvider
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import app.nodes.R
import app.nodes.core.crash.AppLogger
import app.nodes.core.model.PlaybackHandle
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.SourceRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mode 2's user-initiated offline downloads. Media is kept in the app-private
 * Media3 cache; it is never exported to shared storage. Mode 1 is not accepted.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Singleton
class OfflineDownloads @Inject constructor(
    @ApplicationContext private val context: Context,
    cache: Cache,
    databaseProvider: DatabaseProvider,
    private val registry: SourceRegistry,
) {
    /** Outlives any screen: a queue of saves keeps going after the tap. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<Map<String, Download>>(emptyMap())
    val downloads: StateFlow<Map<String, Download>> = _downloads.asStateFlow()
    private val _errors = MutableStateFlow<Map<String, Int>>(emptyMap())
    val errors: StateFlow<Map<String, Int>> = _errors.asStateFlow()

    val cache: Cache = cache

    private val downloadUserAgents = ConcurrentHashMap<String, String>()
    private val downloadUpstreamFactory = ResolvingDataSource.Factory(
        DefaultHttpDataSource.Factory()
            .setUserAgent(Util.getUserAgent(context, context.packageName))
            // googlevideo answers a download with a redirect to the edge that
            // will serve it. Refusing to follow one would fail every save.
            .setAllowCrossProtocolRedirects(true),
    ) { spec ->
        val headers = downloadUserAgents[spec.uri.toString()]
            ?.let { mapOf("User-Agent" to it) }
            .orEmpty()
        spec.withAdditionalHeaders(headers)
    }

    val playbackDataSourceFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(downloadUpstreamFactory)
        .setCacheWriteDataSinkFactory(null)
        .setCacheKeyFactory { spec -> spec.key ?: spec.uri.toString() }

    private val downloadCacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(downloadUpstreamFactory)
        .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(cache))
        .setCacheKeyFactory { spec -> spec.key ?: spec.uri.toString() }

    /**
     * Runs the manager's own work - index writes, listener callbacks and the
     * downloaders themselves - on its own threads. The manager is built with
     * the constructor that takes one, because the shorter one falls back to the
     * calling thread, and a download there would be network I/O on the UI
     * thread.
     */
    private val downloadExecutor = Executors.newFixedThreadPool(2) { work ->
        Thread(work, "spartial-download").apply { isDaemon = true }
    }

    val manager = DownloadManager(
        context,
        databaseProvider,
        cache,
        downloadCacheDataSourceFactory,
        downloadExecutor,
    ).apply {
        maxParallelDownloads = 2
        addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) {
                refresh(downloadManager)
            }

            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                _downloads.value = _downloads.value.toMutableMap().apply {
                    put(download.request.id, download)
                }
                when (download.state) {
                    Download.STATE_COMPLETED -> _errors.value = _errors.value - download.request.id
                    Download.STATE_FAILED -> {
                        _errors.value = _errors.value +
                            (download.request.id to R.string.offline_download_error)
                        // The row only says "could not save"; the report says
                        // why (403 from the CDN, dead URL, no space).
                        AppLogger.e(
                            "Downloads",
                            "Saving ${download.request.id} failed " +
                                "(${download.failureReason})",
                            finalException,
                        )
                    }
                }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                _downloads.value = _downloads.value - download.request.id
                downloadUserAgents.remove(download.request.uri.toString())
            }
        })
    }

    init {
        // A DownloadManager starts paused and only moves when it is resumed.
        // This used to happen in onInitialized, which never ran: the callback
        // was delivered before the listener above was attached, so the manager
        // stayed paused for the life of the process and every save sat at
        // "Queued for offline saving" without ever starting.
        manager.resumeDownloads()
        // And the same race left the saved rows unread, so a track that was
        // already offline offered "Save offline" again. The index is a plain
        // table read, so it is safe to load it here rather than waiting for a
        // callback that may never come.
        runCatching { refresh(manager) }
    }

    /**
     * Saves a whole playlist or album.
     *
     * Runs on the singleton's own scope rather than the caller's, so leaving
     * the screen does not abandon the queue half way through, and skips what is
     * already stored or already in flight.
     */
    fun enqueueAll(tracks: List<Track>) {
        scope.launch {
            tracks.forEach { track ->
                val state = _downloads.value[track.id]?.state
                if (state == null || state == Download.STATE_FAILED) enqueue(track)
            }
        }
    }

    suspend fun enqueue(track: Track) = withContext(Dispatchers.IO) {
        if (track.sourceId != SourceId.YOUTUBE_MUSIC || track.videoId.isNullOrBlank()) {
            _errors.value = _errors.value + (track.id to R.string.offline_download_unavailable)
            return@withContext
        }
        val source = registry.byId(SourceId.YOUTUBE_MUSIC)
        if (source == null) {
            _errors.value = _errors.value + (track.id to R.string.offline_download_error)
            return@withContext
        }
        val result = runCatching { source.play(track) }.getOrElse {
            _errors.value = _errors.value + (track.id to R.string.offline_download_error)
            return@withContext
        }
        val handle = (result as? SourceResult.Success)?.data as? PlaybackHandle.Stream
        if (handle == null) {
            _errors.value = _errors.value + (track.id to R.string.offline_download_error)
            return@withContext
        }
        val uri = runCatching { Uri.parse(handle.url) }.getOrNull()
        if (uri?.scheme != "https" || uri.host.isNullOrBlank()) {
            _errors.value = _errors.value + (track.id to R.string.offline_download_error)
            return@withContext
        }
        rememberStream(handle.url, handle.userAgent)
        val requestData = handle.userAgent.orEmpty().toByteArray(Charsets.UTF_8)
        val request = DownloadRequest.Builder(track.id, uri)
            .setMimeType(trackAudioMimeType(uri))
            .setCustomCacheKey(track.id)
            .setData(requestData)
            .build()
        _errors.value = _errors.value - track.id
        manager.addDownload(request)
    }

    /**
     * Saves one track in the background: on the singleton's own scope, so
     * closing the menu or leaving the screen does not abandon the save halfway
     * through the stream lookup. The download must outlive the tap.
     */
    fun enqueueInBackground(track: Track) {
        scope.launch { enqueue(track) }
    }

    fun remove(trackId: String) {
        _errors.value = _errors.value - trackId
        manager.removeDownload(trackId)
    }

    fun removeAll() {
        downloadUserAgents.clear()
        _errors.value = emptyMap()
        manager.removeAllDownloads()
    }

    fun rememberStream(uri: String, userAgent: String?) {
        if (!userAgent.isNullOrBlank()) downloadUserAgents[uri] = userAgent
    }

    fun isCompleted(trackId: String): Boolean =
        _downloads.value[trackId]?.state == Download.STATE_COMPLETED

    suspend fun completedDownload(trackId: String): Download? = withContext(Dispatchers.IO) {
        runCatching { manager.downloadIndex.getDownload(trackId) }.getOrNull()
            ?.takeIf { it.state == Download.STATE_COMPLETED }
    }

    private fun trackAudioMimeType(uri: Uri): String? =
        runCatching { uri.getQueryParameter("mime") }
            .getOrNull()
            ?.takeIf { it.startsWith("audio/") }

    fun download(trackId: String): Download? = _downloads.value[trackId]

    /**
     * The download as the manager holds it: the live instance whose percentage
     * moves in place while bytes arrive. The map can hold a frozen copy read
     * from the index - fine for state, useless for progress.
     */
    fun liveDownload(trackId: String): Download? =
        manager.currentDownloads.firstOrNull { it.request.id == trackId }
            ?: _downloads.value[trackId]

    fun error(trackId: String): Int? = _errors.value[trackId]

    private fun refresh(downloadManager: DownloadManager) {
        val current = mutableMapOf<String, Download>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        try {
            while (cursor.moveToNext()) {
                val download = cursor.download ?: continue
                current[download.request.id] = download
            }
        } finally {
            cursor.close()
        }
        current.values.forEach { download ->
            decodeUserAgent(download.request.data)?.let { userAgent ->
                downloadUserAgents[download.request.uri.toString()] = userAgent
            }
        }
        // In-flight downloads must be the manager's live instances: the index
        // hands out frozen copies whose percentage never moves again.
        downloadManager.currentDownloads.forEach { live ->
            current[live.request.id] = live
        }
        _downloads.value = current
    }

    private fun decodeUserAgent(data: ByteArray): String? =
        data.toString(Charsets.UTF_8).takeIf(String::isNotBlank)
}
