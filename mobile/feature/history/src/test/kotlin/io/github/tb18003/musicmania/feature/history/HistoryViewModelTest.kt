/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.history

import androidx.paging.testing.asSnapshot
import io.github.tb18003.musicmania.core.testing.MainDispatcherRule
import io.github.tb18003.musicmania.core.testing.TestData
import io.github.tb18003.musicmania.core.testing.repository.FakeSyncScheduler
import io.github.tb18003.musicmania.core.testing.repository.InMemoryHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertEquals

class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `plays are grouped under day headers, newest first`() = runTest(mainDispatcherRule.testDispatcher) {
        val history = InMemoryHistoryRepository().apply {
            setEvents(
                listOf(
                    TestData.play(track = "Old", playedAt = TestData.NOW.minus(Duration.ofDays(1))),
                    TestData.play(track = "New A", playedAt = TestData.NOW),
                    TestData.play(track = "New B", playedAt = TestData.NOW.minus(Duration.ofHours(1))),
                ),
            )
        }
        val viewModel = HistoryViewModel(history, FakeSyncScheduler(), TestData.fixedClock)

        val snapshot = viewModel.items.asSnapshot()

        val today = LocalDate.of(2026, 6, 15)
        assertEquals(
            listOf("header:$today", "New A", "New B", "header:${today.minusDays(1)}", "Old"),
            snapshot.map {
                when (it) {
                    is HistoryItem.DayHeader -> "header:${it.date}"
                    is HistoryItem.Play -> it.event.trackName
                }
            },
        )
    }

    @Test
    fun `sync now delegates to the scheduler`() {
        val scheduler = FakeSyncScheduler()
        HistoryViewModel(InMemoryHistoryRepository(), scheduler, TestData.fixedClock).syncNow()
        assertEquals(1, scheduler.syncNowRequests)
    }
}
