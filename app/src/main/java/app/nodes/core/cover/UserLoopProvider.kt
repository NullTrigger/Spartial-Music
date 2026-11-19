/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.cover

import app.nodes.core.model.Track
import javax.inject.Inject

/** The user's own picked loop, always first in line. */
class UserLoopProvider @Inject constructor(
    private val store: UserLoopStore,
) : AnimatedCoverProvider {
    override val id: String = CoverProviderIds.USER_LOOP

    override suspend fun find(track: Track): AnimatedCover? =
        store.fileFor(track.id)?.absolutePath?.let {
            AnimatedCover(url = it, providerId = id, isLocalFile = true)
        }
}
