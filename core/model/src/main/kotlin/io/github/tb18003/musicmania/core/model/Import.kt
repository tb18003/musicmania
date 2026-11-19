/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.model

import java.time.Instant

data class ImportResult(
    val filesProcessed: Int,
    val eventsRead: Int,
    val eventsInserted: Int,
    val skippedEntries: Int,
    val earliest: Instant?,
    val latest: Instant?,
) {
    val duplicates: Int get() = (eventsRead - eventsInserted).coerceAtLeast(0)
}

sealed interface ImportProgress {
    data class Running(
        val currentFile: String,
        val filesProcessed: Int,
        val eventsRead: Int,
    ) : ImportProgress

    data class Completed(val result: ImportResult) : ImportProgress
}
