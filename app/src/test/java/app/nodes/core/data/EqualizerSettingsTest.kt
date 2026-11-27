/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.nodes.core.model.EqualizerPresets
import app.nodes.core.model.EqualizerSettings
import app.nodes.core.model.decodeEqualizerSettings
import app.nodes.core.model.encode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Equalizer settings must survive a process restart: the JSON blob keeps
 * every field, and a corrupt or missing blob degrades to defaults instead of
 * breaking the settings screen.
 */
class EqualizerSettingsTest {

    @Test
    fun `round trip keeps every field`() {
        val original = EqualizerSettings(
            enabled = false,
            presetId = EqualizerPresets.CUSTOM,
            bandLevelsDb = listOf(-2.5f, 0f, 3.5f, 1f, -1f),
            bassBoostStrength = 400,
            virtualizerStrength = 250,
        )

        assertEquals(original, decodeEqualizerSettings(original.encode()))
    }

    @Test
    fun `blank or corrupt payloads fall back to defaults`() {
        assertEquals(EqualizerSettings(), decodeEqualizerSettings(null))
        assertEquals(EqualizerSettings(), decodeEqualizerSettings(""))
        assertEquals(EqualizerSettings(), decodeEqualizerSettings("{not json"))
    }

    @Test
    fun `repository round trip survives the data store`() = runTest {
        val dir = createTempDirectory("eq-settings-test").toFile()
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                produceFile = { File(dir, "test.preferences_pb") },
            )
            val repository = SettingsRepository(dataStore)
            val settings = EqualizerSettings(
                enabled = false,
                presetId = EqualizerPresets.ROCK,
                bandLevelsDb = listOf(4f, 2f, -1f),
                bassBoostStrength = 700,
                virtualizerStrength = 0,
            )

            repository.setEqualizerSettings(settings)

            assertEquals(settings, repository.equalizerSettings.first())
        } finally {
            dir.deleteRecursively()
        }
    }
}
