/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.datastore.crypto

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/** Authenticated encryption abstraction; swapped by a fake in JVM tests. */
interface Encryptor {
    /** @throws GeneralSecurityException on failure. */
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray

    /** @throws GeneralSecurityException if the ciphertext was tampered with or the key changed. */
    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray
}

/**
 * AES-256-GCM via Google Tink. The Tink keyset is stored in a private SharedPreferences
 * file, itself encrypted ("wrapped") by a non-exportable master key living in the
 * Android Keystore (hardware-backed when available). Both files are excluded from
 * backups because the Keystore key never leaves the device.
 *
 * Replaces the deprecated `androidx.security:security-crypto` (EncryptedSharedPreferences).
 */
@Singleton
class TinkEncryptor @Inject constructor(@param:ApplicationContext private val context: Context) : Encryptor {
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray = aead.encrypt(plaintext, associatedData)

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray = aead.decrypt(ciphertext, associatedData)

    companion object {
        const val KEYSET_PREFS_FILE = "musicmania_tink_keyset"
        private const val KEYSET_NAME = "token_keyset"
        private const val MASTER_KEY_URI = "android-keystore://musicmania_master_key"
    }
}
