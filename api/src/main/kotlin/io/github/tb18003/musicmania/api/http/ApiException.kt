/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api.http

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/** Expected failures, rendered as RFC 9457 problem details by the StatusPages configuration. */
sealed class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message) {
    class Unauthorized(
        code: String,
        message: String = "A valid Spotify access token is required.",
    ) : ApiException(HttpStatusCode.Unauthorized, code, message)

    class Forbidden(
        code: String,
        message: String,
    ) : ApiException(HttpStatusCode.Forbidden, code, message)

    class NotFound(
        code: String,
        message: String,
    ) : ApiException(HttpStatusCode.NotFound, code, message)

    class BadRequest(
        code: String,
        message: String,
    ) : ApiException(HttpStatusCode.BadRequest, code, message)

    class PayloadTooLarge(limitBytes: Int) :
        ApiException(HttpStatusCode.PayloadTooLarge, "payload_too_large", "The request body exceeds $limitBytes bytes.")

    class ServiceUnavailable(
        val retryAfterSeconds: Long?,
        message: String,
    ) : ApiException(HttpStatusCode.ServiceUnavailable, "upstream_unavailable", message)
}

@Serializable
data class Problem(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val code: String,
    val detail: String,
)
