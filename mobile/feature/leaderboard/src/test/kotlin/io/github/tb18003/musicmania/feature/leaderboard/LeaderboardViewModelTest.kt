/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.leaderboard

import androidx.lifecycle.SavedStateHandle
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.domain.usecase.BuildSharedStatsUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetFriendStatsUseCase
import io.github.tb18003.musicmania.core.domain.usecase.GetLeaderboardUseCase
import io.github.tb18003.musicmania.core.domain.usecase.PublishListeningStatsUseCase
import io.github.tb18003.musicmania.core.model.FriendStats
import io.github.tb18003.musicmania.core.model.LeaderboardEntry
import io.github.tb18003.musicmania.core.model.LeaderboardMetric
import io.github.tb18003.musicmania.core.model.LeaderboardPeriod
import io.github.tb18003.musicmania.core.testing.MainDispatcherRule
import io.github.tb18003.musicmania.core.testing.TestData
import io.github.tb18003.musicmania.core.testing.repository.FakeCloudRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeSessionRepository
import io.github.tb18003.musicmania.core.testing.repository.FakeUserRepository
import io.github.tb18003.musicmania.core.testing.repository.InMemoryHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LeaderboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cloud = FakeCloudRepository()
    private val clock = TestData.fixedClock

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = LeaderboardViewModel(
        savedStateHandle = handle,
        cloud = cloud,
        userRepository = FakeUserRepository(),
        getLeaderboard = GetLeaderboardUseCase(cloud, clock),
        getFriendStats = GetFriendStatsUseCase(cloud, clock),
        publishStats = PublishListeningStatsUseCase(
            cloud,
            FakeSessionRepository(),
            BuildSharedStatsUseCase(InMemoryHistoryRepository(), clock),
        ),
    )

    private val entries = listOf(
        LeaderboardEntry(1, "ana", "Ana", null, 1_510, isMe = false),
        LeaderboardEntry(2, "me", "Me", null, 1_240, isMe = true),
    )

    @Test
    fun `accounts outside the allowlist see their Spotify id to send to the admin`() = runTest {
        cloud.failure = AppError.NotAllowlisted
        val content = viewModel().uiState.value.content
        assertEquals(LeaderboardContent.NotAllowlisted(spotifyUserId = "user"), content)
    }

    @Test
    fun `nothing is shown until the user opts in`() = runTest {
        assertEquals(LeaderboardContent.SharingOff, viewModel().uiState.value.content)
    }

    @Test
    fun `opting in publishes right away and shows the ranking`() = runTest {
        cloud.entries = entries
        val viewModel = viewModel()

        viewModel.enableSharing()

        assertEquals(1, cloud.published.size, "my stats are uploaded so I appear immediately")
        assertEquals(LeaderboardContent.Ranking(entries), viewModel.uiState.value.content)
    }

    @Test
    fun `a failed upload when opting in offers a retry instead of an empty ranking`() = runTest {
        cloud.publishFailure = AppError.Network
        val viewModel = viewModel()

        viewModel.enableSharing()

        assertIs<LeaderboardContent.Error>(viewModel.uiState.value.content)
        assertEquals(false, viewModel.uiState.value.isEnablingSharing)
    }

    @Test
    fun `sharing but missing from the ranking uploads once and fetches again`() = runTest {
        cloud.profile = cloud.profile.copy(sharingEnabled = true)
        cloud.entries = entries.filterNot { it.isMe }

        val viewModel = viewModel()

        assertEquals(1, cloud.published.size, "my stats are uploaded so I can appear")
        assertEquals(2, cloud.leaderboardRequests.size, "the ranking is fetched again after the upload")
        viewModel.selectPeriod(LeaderboardPeriod.YEAR)
        assertEquals(1, cloud.published.size, "only one automatic upload per screen")
    }

    @Test
    fun `changing metric and period reloads and survives process death`() = runTest {
        cloud.profile = cloud.profile.copy(sharingEnabled = true)
        cloud.entries = entries
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)

        viewModel.selectMetric(LeaderboardMetric.STREAK)
        viewModel.selectPeriod(LeaderboardPeriod.YEAR)

        val (metric, period, _) = cloud.leaderboardRequests.last()
        assertEquals(LeaderboardMetric.STREAK, metric)
        assertEquals(LeaderboardPeriod.YEAR, period)
        val restored = viewModel(handle).uiState.value
        assertEquals(LeaderboardMetric.STREAK, restored.metric)
        assertEquals(LeaderboardPeriod.YEAR, restored.period)
    }

    @Test
    fun `tapping a friend expands their details, tapping again collapses`() = runTest {
        cloud.profile = cloud.profile.copy(sharingEnabled = true)
        cloud.entries = entries
        cloud.friends = mapOf("ana" to FriendStats("ana", "Ana", 1_510, 520, 33, 30, listOf("Coral Echo"), listOf("Low Tide")))
        val viewModel = viewModel()

        viewModel.toggleFriend("ana")
        assertEquals(listOf("Coral Echo"), viewModel.uiState.value.expanded?.stats?.topArtists)

        viewModel.toggleFriend("ana")
        assertNull(viewModel.uiState.value.expanded)
    }

    @Test
    fun `server errors offer a retry`() = runTest {
        cloud.failure = AppError.Network
        assertIs<LeaderboardContent.Error>(viewModel().uiState.value.content)
    }
}
