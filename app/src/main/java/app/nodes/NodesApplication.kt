/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes

import android.app.Application
import app.nodes.core.crash.CrashReport
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NodesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // First, before anything else can fail: every uncaught exception from
        // here on is saved and shown on the crash page.
        CrashReport.install(this)
    }
}
