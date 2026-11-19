/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.datastore.token

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.tb18003.musicmania.core.datastore.crypto.Encryptor
import io.github.tb18003.musicmania.core.datastore.di.SecureSessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

interface TokenStore {
    val tokens: Flow<AuthTokens?>

    /** True after a refresh token was rejected, until the next successful sign-in. */
    val sessionExpired: Flow<Boolean>

    /**
     * Synchronous, cached read for OkHttp threads (interceptors/authenticator).
     * Decrypts from disk only on the first call of the process.
     */
    fun currentTokens(): AuthTokens?

    suspend fun save(tokens: AuthTokens)

    suspend fun clear(sessionExpired: Boolean = false)
}

/** Base64 codec seam: `android.util.Base64` is not available in plain JVM tests. */
interface Base64Codec {
    fun encode(bytes: ByteArray): String

    fun decode(value: String): ByteArray
}

class AndroidBase64Codec @Inject constructor() : Base64Codec {
    override fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    override fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}

@Singleton
class EncryptedTokenStore @Inject constructor(
    @param:SecureSessionStore private val dataStore: DataStore<Preferences>,
    private val encryptor: Encryptor,
    private val base64: Base64Codec,
) : TokenStore {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: AuthTokens? = null

    @Volatile
    private var cacheLoaded = false

    override val tokens: Flow<AuthTokens?> = dataStore.data
        .map { prefs -> prefs[KEY_TOKENS]?.let(::decryptOrNull) }
        .distinctUntilChanged()

    override val sessionExpired: Flow<Boolean> = dataStore.data.map { it[KEY_EXPIRED] ?: false }.distinctUntilChanged()

    override fun currentTokens(): AuthTokens? {
        if (!cacheLoaded) {
            synchronized(this) {
                if (!cacheLoaded) {
                    cache = runBlocking { tokens.first() }
                    cacheLoaded = true
                }
            }
        }
        return cache
    }

    override suspend fun save(tokens: AuthTokens) {
        val plaintext = json.encodeToString(AuthTokens.serializer(), tokens).encodeToByteArray()
        val blob = base64.encode(encryptor.encrypt(plaintext, ASSOCIATED_DATA))
        dataStore.edit {
            it[KEY_TOKENS] = blob
            it[KEY_EXPIRED] = false
        }
        cache = tokens
        cacheLoaded = true
    }

    override suspend fun clear(sessionExpired: Boolean) {
        dataStore.edit {
            it.remove(KEY_TOKENS)
            it[KEY_EXPIRED] = sessionExpired
        }
        cache = null
        cacheLoaded = true
    }

    /**
     * Undecryptable data (e.g. Keystore key wiped, restored from another device)
     * is treated as "signed out" rather than crashing the app.
     */
    private fun decryptOrNull(blob: String): AuthTokens? = try {
        val plaintext = encryptor.decrypt(base64.decode(blob), ASSOCIATED_DATA)
        json.decodeFromString(AuthTokens.serializer(), plaintext.decodeToString())
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        val KEY_TOKENS = stringPreferencesKey("tokens_v1")
        val KEY_EXPIRED = booleanPreferencesKey("session_expired")

        /** Binds ciphertext to its purpose so it cannot be swapped with other encrypted data. */
        val ASSOCIATED_DATA = "musicmania:spotify_tokens:v1".encodeToByteArray()
    }
}
