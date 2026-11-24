/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network

import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
private val HTTP_SERVER_ERRORS = 500..599

/** Executes a network call and maps every failure mode to a typed [AppError]. */
internal suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): DataResult<T> = try {
    DataResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    DataResult.Failure(httpError(e.code(), e.response()?.headers()?.get("Retry-After")))
} catch (_: IOException) {
    DataResult.Failure(AppError.Network)
} catch (e: SerializationException) {
    DataResult.Failure(AppError.InvalidData(e.message ?: "Malformed response"))
}

internal fun httpError(code: Int, retryAfter: String?): AppError = when (code) {
    HTTP_UNAUTHORIZED -> AppError.Unauthorized
    HTTP_FORBIDDEN -> AppError.Forbidden
    HTTP_NOT_FOUND -> AppError.NotFound
    HTTP_TOO_MANY_REQUESTS -> AppError.RateLimited(retryAfter?.toLongOrNull())
    in HTTP_SERVER_ERRORS -> AppError.Server(code)
    else -> AppError.Unknown("HTTP $code")
}
