/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.sessions

import androidx.lifecycle.ViewModel
import app.nodes.core.session.ListenTogetherManager
import app.nodes.core.session.ListenTogetherServers
import app.nodes.core.session.RoomUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
) : ViewModel() {

    val state: StateFlow<RoomUiState> = manager.state

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _server = MutableStateFlow(ListenTogetherServers.DEFAULT)
    val server: StateFlow<String> = _server.asStateFlow()

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

    fun create() = manager.create(_server.value, displayName())

    fun join() {
        val typed = _code.value
        if (typed.length != ROOM_CODE_LENGTH) {
            manager.reportNotice("Enter all $ROOM_CODE_LENGTH characters of the room code.")
            return
        }
        manager.join(_server.value, typed, displayName())
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
