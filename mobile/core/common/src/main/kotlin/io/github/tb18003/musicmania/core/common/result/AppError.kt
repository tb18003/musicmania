/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.common.result

/** Typed, user-facing error taxonomy. Never carries tokens or raw response bodies. */
sealed interface AppError {
    /** No connectivity or the request timed out. */
    data object Network : AppError

    /** Session is missing or could not be refreshed. */
    data object Unauthorized : AppError

    /**
     * 403 from Spotify. In Development Mode this usually means the account is not in
     * the app's user allowlist (max. 5 users) or the app owner has no Premium.
     */
    data object Forbidden : AppError

    data object NotFound : AppError

    /** The MusicMania cloud API does not know this Spotify account (it is not on the allowlist). */
    data object NotAllowlisted : AppError

    data class RateLimited(val retryAfterSeconds: Long?) : AppError

    data class Server(val code: Int) : AppError

    /** Malformed data, e.g. an unexpected file during import. */
    data class InvalidData(val reason: String) : AppError

    data object Storage : AppError

    data class Unknown(val description: String?) : AppError
}
