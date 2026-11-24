/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.InterruptedIOException

/**
 * Handles HTTP 429. Short `Retry-After` waits are absorbed transparently (bounded
 * attempts); long ones — e.g. `QUOTA_EXCEEDED` on the shared Development Mode
 * quota — are surfaced to the caller as [io.github.tb18003.musicmania.core.common.result.AppError.RateLimited].
 */
class RateLimitInterceptor(
    private val maxInlineWaitSeconds: Long = 5,
    private val maxRetries: Int = 2,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        var attempt = 0
        while (response.code == TOO_MANY_REQUESTS && attempt < maxRetries) {
            val waitSeconds = response.header("Retry-After")?.toLongOrNull() ?: DEFAULT_WAIT_SECONDS
            if (waitSeconds > maxInlineWaitSeconds) return response
            response.close()
            try {
                sleeper(waitSeconds * MILLIS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Interrupted while rate limited").apply { initCause(e) }
            }
            attempt++
            response = chain.proceed(chain.request())
        }
        return response
    }

    private companion object {
        const val TOO_MANY_REQUESTS = 429
        const val DEFAULT_WAIT_SECONDS = 1L
        const val MILLIS = 1_000L
    }
}
