/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.R
import app.nodes.core.crash.CrashLogFile
import app.nodes.core.crash.CrashStore
import app.nodes.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val reports: List<CrashLogFile> = emptyList(),
    val saveEnabled: Boolean = true,
    val openedName: String? = null,
    val openedText: String? = null,
    val message: String? = null,
)

/**
 * Backs the Diagnostics screen: the report list, the save toggle and the one
 * destructive action that needs a confirmation (delete all).
 *
 * Files are read on the main thread deliberately: a crash report is a few
 * hundred kilobytes at most and there are never more than twenty. Keeping the
 * state in a ViewModel means rotating the phone does not re-list the directory.
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            settings.settings.collect { current ->
                _state.update { it.copy(saveEnabled = current.crashReportsEnabled) }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(reports = CrashStore.reports(context)) }
    }

    fun setSaveEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setCrashReportsEnabled(enabled) }
    }

    fun open(report: CrashLogFile) {
        _state.update {
            it.copy(openedName = report.name, openedText = CrashStore.read(report.file))
        }
    }

    fun closeOpened() {
        _state.update { it.copy(openedName = null, openedText = null) }
    }

    fun delete(report: CrashLogFile) {
        CrashStore.delete(report.file)
        refresh()
    }

    fun deleteAll() {
        CrashStore.deleteAll(context)
        refresh()
        _state.update { it.copy(message = context.getString(R.string.diagnostics_deleted_all)) }
    }

    fun copyLast() {
        val text = CrashStore.latest(context)?.let { CrashStore.read(it.file) }
        if (text == null) {
            _state.update { it.copy(message = context.getString(R.string.crash_none)) }
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Spartial Music crash report", text))
        _state.update { it.copy(message = context.getString(R.string.diagnostics_copied)) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    /**
     * Debug builds only: crashes on purpose so the whole pipeline - handler,
     * file, dialog - can be tested on a real device.
     */
    fun forceCrash() {
        throw IllegalStateException("Test crash from Diagnostics")
    }
}
