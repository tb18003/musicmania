/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.common.result

/**
 * Explicit success/failure type used across layers instead of throwing.
 * Failures always carry a typed [AppError] the UI can map to a message.
 */
sealed interface DataResult<out T> {
    data class Success<T>(val data: T) : DataResult<T>

    data class Failure(val error: AppError) : DataResult<Nothing>
}

inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(data))
    is DataResult.Failure -> this
}

inline fun <T> DataResult<T>.onSuccess(action: (T) -> Unit): DataResult<T> {
    if (this is DataResult.Success) action(data)
    return this
}

inline fun <T> DataResult<T>.onFailure(action: (AppError) -> Unit): DataResult<T> {
    if (this is DataResult.Failure) action(error)
    return this
}

fun <T> DataResult<T>.getOrNull(): T? = (this as? DataResult.Success)?.data

val DataResult<*>.isSuccess: Boolean get() = this is DataResult.Success
