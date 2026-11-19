/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network.auth

import io.github.tb18003.musicmania.core.datastore.token.AuthTokens
import io.github.tb18003.musicmania.core.datastore.token.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeTokenStore(initial: AuthTokens?) : TokenStore {
    private val state = MutableStateFlow(initial)
    private val expired = MutableStateFlow(false)
    override val tokens: StateFlow<AuthTokens?> = state
    override val sessionExpired: StateFlow<Boolean> = expired

    override fun currentTokens(): AuthTokens? = state.value

    override suspend fun save(tokens: AuthTokens) {
        state.value = tokens
        expired.value = false
    }

    override suspend fun clear(sessionExpired: Boolean) {
        state.value = null
        expired.value = sessionExpired
    }
}
