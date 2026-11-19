/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.common.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataResultTest {
    @Test
    fun `map transforms success and keeps failure`() {
        val success: DataResult<Int> = DataResult.Success(2)
        val failure: DataResult<Int> = DataResult.Failure(AppError.Network)

        assertEquals(DataResult.Success(4), success.map { it * 2 })
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun `getOrNull returns data only on success`() {
        assertEquals("a", DataResult.Success("a").getOrNull())
        assertNull(DataResult.Failure(AppError.NotFound).getOrNull())
    }

    @Test
    fun `callbacks run only for matching branch`() {
        var successCalled = false
        var failureError: AppError? = null

        DataResult.Success(1).onSuccess { successCalled = true }.onFailure { failureError = it }
        assertTrue(successCalled)
        assertNull(failureError)

        DataResult.Failure(AppError.Forbidden).onFailure { failureError = it }
        assertEquals(AppError.Forbidden, failureError)
    }
}
