/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.datastore.token

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tb18003.musicmania.core.datastore.crypto.TinkEncryptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** End-to-end token persistence on a real device: Tink + Keystore + a DataStore file. */
@RunWith(AndroidJUnit4::class)
class EncryptedTokenStoreDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = File(context.filesDir, "datastore/token_store_device_test.preferences_pb")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    private val tokens = AuthTokens(
        accessToken = "access-secret-123",
        refreshToken = "refresh-secret-456",
        expiresAtEpochMs = 1_800_000_000_000,
        scope = "user-top-read",
    )

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    private fun store() = EncryptedTokenStore(dataStore, TinkEncryptor(context), AndroidBase64Codec())

    @Test
    fun tokensAreUnreadableOnDiskButRestoredByANewStore() = runTest {
        store().save(tokens)

        val onDisk = file.readBytes().decodeToString(throwOnInvalidSequence = false)
        assertTrue(onDisk.isNotEmpty())
        assertFalse(onDisk.contains(tokens.accessToken))
        assertFalse(onDisk.contains(tokens.refreshToken))
        assertEquals(tokens, store().tokens.first())
    }

    @Test
    fun clearRemovesTokensAndRecordsExpiry() = runTest {
        val store = store()
        store.save(tokens)

        store.clear(sessionExpired = true)

        assertNull(store.tokens.first())
        assertNull(store.currentTokens())
        assertTrue(store.sessionExpired.first())
    }
}
