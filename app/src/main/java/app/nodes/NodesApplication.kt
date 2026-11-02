/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes

import android.app.Application
import app.nodes.core.crash.AppLogger
import app.nodes.core.crash.CrashReport
import app.nodes.core.data.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NodesApplication : Application() {

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
            CrashReport.recordNonFatal(this@NodesApplication, error, "coroutine")
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
}
