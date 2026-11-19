/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.datastore.token

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.tb18003.musicmania.core.datastore.crypto.Encryptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedTokenStoreTest {
    private val tokens = AuthTokens(accessToken = "access-secret", refreshToken = "refresh-secret", expiresAtEpochMs = 1_000)
    private val dataStore = InMemoryPreferencesDataStore()

    private fun createStore(encryptor: Encryptor = XorEncryptor()) = EncryptedTokenStore(dataStore, encryptor, JvmBase64())

    @Test
    fun `saved tokens round-trip and are never stored in plaintext`() = runTest {
        val store = createStore()

        store.save(tokens)

        assertEquals(tokens, store.tokens.first())
        assertEquals(tokens, store.currentTokens())
        val persisted = dataStore.data.first().asMap().values.joinToString()
        assertFalse(persisted.contains("access-secret"))
        assertFalse(persisted.contains("refresh-secret"))
    }

    @Test
    fun `cold start reads tokens from storage once`() = runTest {
        createStore().save(tokens)

        val coldStore = createStore()

        assertEquals(tokens, coldStore.currentTokens())
    }

    @Test
    fun `clear removes tokens and flags expired sessions`() = runTest {
        val store = createStore()
        store.save(tokens)

        store.clear(sessionExpired = true)

        assertNull(store.tokens.first())
        assertNull(store.currentTokens())
        assertTrue(store.sessionExpired.first())
    }

    @Test
    fun `undecryptable data is treated as signed out`() = runTest {
        val store = createStore(FailingDecryptEncryptor())
        store.save(tokens)

        assertNull(store.tokens.first())
    }

    @Test
    fun `tokens are redacted from toString`() {
        assertFalse(tokens.toString().contains("secret"))
    }

    @Test
    fun `expiry honours skew`() {
        assertTrue(tokens.isExpired(nowEpochMs = 950, skewMs = 100))
        assertFalse(tokens.isExpired(nowEpochMs = 800, skewMs = 100))
    }

    /** File-free DataStore: avoids platform-specific file semantics in unit tests. */
    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    /** Toy reversible cipher; real encryption is Tink, which needs the Android Keystore. */
    private open class XorEncryptor : Encryptor {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray) =
            plaintext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray) = encrypt(ciphertext, associatedData)
    }

    private class FailingDecryptEncryptor : XorEncryptor() {
        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray = throw GeneralSecurityException("tampered")
    }

    private class JvmBase64 : Base64Codec {
        override fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        override fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
    }
}
