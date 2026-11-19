/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.usecase

import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.repository.PlaybackRepository
import io.github.tb18003.musicmania.core.model.NowPlaying
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Polls the currently playing track while collected. The UI interpolates progress
 * locally between polls, so a slow interval keeps the API quota low and still
 * looks real-time. Errors are swallowed (the card simply hides) and polling backs off.
 */
class ObserveNowPlayingUseCase(
    private val playback: PlaybackRepository,
    private val interval: Duration,
    private val maxBackoff: Duration,
) {
    @javax.inject.Inject
    constructor(playback: PlaybackRepository) : this(playback, DEFAULT_INTERVAL, MAX_BACKOFF)

    operator fun invoke(): Flow<NowPlaying?> = flow {
        var wait = interval
        while (true) {
            when (val result = playback.currentlyPlaying()) {
                is DataResult.Success -> {
                    emit(result.data)
                    wait = interval
                }
                is DataResult.Failure -> {
                    emit(null)
                    wait = (wait * 2).coerceAtMost(maxBackoff)
                }
            }
            delay(wait)
        }
    }.distinctUntilChanged()

    companion object {
        val DEFAULT_INTERVAL = 10.seconds
        val MAX_BACKOFF = 120.seconds
    }
}
