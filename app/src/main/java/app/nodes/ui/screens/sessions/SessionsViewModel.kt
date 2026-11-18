/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.SettingsRepository
import app.nodes.core.session.ListenTogetherManager
import app.nodes.core.session.ListenTogetherServers
import app.nodes.core.session.RoomUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Holds the room state for [SessionsScreen] plus the two fields the user types.
 *
 * The room itself belongs to [ListenTogetherManager], a singleton: closing this
 * screen must not drop the session, because navigating away to pick a song is
 * the normal thing to do while hosting.
 */
@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val manager: ListenTogetherManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<RoomUiState> = manager.state

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _server = MutableStateFlow(ListenTogetherServers.DEFAULT)
    val server: StateFlow<String> = _server.asStateFlow()

    /**
     * The connection the user asked for while the one-time privacy dialog is
     * still open. Null means no dialog; the connection only happens after the
     * user confirms, and the question is asked only once per install.
     */
    private val _pendingConnect = MutableStateFlow<(() -> Unit)?>(null)
    val pendingConnect: StateFlow<(() -> Unit)?> = _pendingConnect.asStateFlow()

    fun setName(value: String) {
        _name.value = value
        manager.announceName(value)
    }

    fun setCode(value: String) {
        // Room codes are a fixed length and upper case on every server in this
        // family, so the field cannot be filled with something that will never
        // match.
        _code.value = value.uppercase().take(ROOM_CODE_LENGTH)
    }

    fun setServer(value: String) {
        _server.value = ListenTogetherServers.normalise(value)
    }

    fun create() = connect { manager.create(_server.value, displayName()) }

    fun join() {
        val typed = _code.value
        if (typed.length != ROOM_CODE_LENGTH) {
            manager.reportNotice("Enter all $ROOM_CODE_LENGTH characters of the room code.")
            return
        }
        connect { manager.join(_server.value, typed, displayName()) }
    }

    /**
     * Shared listening is the one feature that talks to a third-party server,
     * so the first connection asks once what is sent there. A blank server is
     * refused outright: nothing ever connects without a deliberate choice.
     */
    private fun connect(block: () -> Unit) {
        if (_server.value.isBlank()) {
            manager.reportNotice("Pick a server first - nothing is sent until you do.")
            return
        }
        viewModelScope.launch {
            if (settings.sessionsConsentShown.first()) {
                block()
            } else {
                _pendingConnect.value = block
            }
        }
    }

    /** The dialog's confirmation: remembers the answer and connects. */
    fun confirmConnect() {
        viewModelScope.launch { settings.setSessionsConsentShown() }
        _pendingConnect.value?.invoke()
        _pendingConnect.value = null
    }

    fun dismissConnect() {
        _pendingConnect.value = null
    }

    fun approve(userId: String) = manager.approveJoin(userId)

    fun decline(userId: String) = manager.rejectJoin(userId)

    fun leave() = manager.leave()

    fun clearNotice() = manager.clearNotice()

    private fun displayName(): String = _name.value.trim().ifBlank { DEFAULT_NAME }

    override fun onCleared() {
        // The room outlives the screen; the manager decides what else to drop.
        manager.onScreenClosed()
        super.onCleared()
    }

    private companion object {
        const val DEFAULT_NAME = "Guest"

        /** Room codes are six characters on every server in this protocol family. */
        const val ROOM_CODE_LENGTH = 6
    }
}
