/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes

import android.app.Application
import app.nodes.core.crash.AppLogger
import app.nodes.core.crash.CrashReport
import app.nodes.core.data.SettingsRepository
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SpartialApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var settings: SettingsRepository

    /**
     * App-level scope for work that outlives a screen. The handler is the reason
     * it exists: a coroutine started here that fails has no caller to catch it,
     * and without this its exception is swallowed by the default handler while
     * the app keeps running in a broken state.
     */
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            AppLogger.e("App", "Uncaught coroutine error", error)
            CrashReport.recordNonFatal(this@SpartialApplication, error, "coroutine")
        },
    )

    override fun onCreate() {
        super.onCreate()
        // The logger first, so the crash handler has somewhere to record what it
        // was doing, then the handler itself, before any Hilt graph is built.
        AppLogger.init(this)
        CrashReport.install(this)

        appScope.launch {
            settings.settings.collect { current ->
                // The crash handler cannot read DataStore, so the toggle is
                // mirrored into a volatile field it can see.
                CrashReport.enabled = current.crashReportsEnabled
            }
        }
    }

    /**
     * One image loader for the whole app: lists, heroes and the palette sampler
     * all hit the same memory and disk caches, so a cover is fetched once.
     *
     * Coil's defaults are tuned for a small photo app, not for a catalogue that
     * scrolls through hundreds of covers:
     *  - [ImageLoader.Builder.respectCacheHeaders] off. YouTube's image hosts
     *    frequently answer without a cache lifetime, and Coil then refuses to
     *    write the bytes to disk at all - every scroll re-downloaded the same
     *    cover. Artwork URLs are content-addressed (`=w256-h256`), so the bytes
     *    behind a URL do not change; caching them is safe.
     *  - a 256 MB disk cache instead of the 2%-of-free-space default, which on
     *    a full phone can be only a few megabytes.
     *  - no default crossfade. A row that fades in over 100ms reads as slower
     *    than one that appears at once; surfaces that want the fade ask for it.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024L * 1024L)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(false)
        .build()
}
