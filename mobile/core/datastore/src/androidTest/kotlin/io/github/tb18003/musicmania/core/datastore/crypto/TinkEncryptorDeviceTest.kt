/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.datastore.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.security.KeyStore
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the real Android Keystore + Tink stack, which Robolectric cannot emulate.
 * JVM tests use a fake [Encryptor]; this is the only place the production one runs.
 */
@RunWith(AndroidJUnit4::class)
class TinkEncryptorDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val plaintext = "refresh-token-value".encodeToByteArray()
    private val associatedData = "purpose:v1".encodeToByteArray()

    @Test
    fun roundTripsAndNeverExposesThePlaintext() {
        val encryptor = TinkEncryptor(context)

        val ciphertext = encryptor.encrypt(plaintext, associatedData)

        assertFalse(ciphertext.decodeToString(throwOnInvalidSequence = false).contains("refresh-token-value"))
        assertContentEquals(plaintext, encryptor.decrypt(ciphertext, associatedData))
    }

    @Test
    fun usesFreshNoncesSoEqualInputsDoNotProduceEqualCiphertexts() {
        val encryptor = TinkEncryptor(context)

        val first = encryptor.encrypt(plaintext, associatedData)
        val second = encryptor.encrypt(plaintext, associatedData)

        assertFalse(first.contentEquals(second))
    }

    @Test
    fun rejectsTamperedCiphertextAndForeignAssociatedData() {
        val encryptor = TinkEncryptor(context)
        val ciphertext = encryptor.encrypt(plaintext, associatedData)
        val tampered = ciphertext.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }

        assertFailsWith<GeneralSecurityException> { encryptor.decrypt(tampered, associatedData) }
        assertFailsWith<GeneralSecurityException> { encryptor.decrypt(ciphertext, "other:v1".encodeToByteArray()) }
    }

    @Test
    fun keysetSurvivesANewInstanceAndIsWrappedByAKeystoreKey() {
        val ciphertext = TinkEncryptor(context).encrypt(plaintext, associatedData)

        // A new instance simulates a process restart: the keyset must be reloaded and unwrapped.
        assertContentEquals(plaintext, TinkEncryptor(context).decrypt(ciphertext, associatedData))
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(keyStore.containsAlias("musicmania_master_key"))
    }
}
