/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api.model

import io.github.tb18003.musicmania.api.http.ApiException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class StatsValidatorTest {
    private fun week(start: String = "2026-11-23", minutes: Int = 300, topArtists: List<TopItem> = emptyList()) = PeriodStats(
        kind = PeriodKind.WEEK,
        start = start,
        minutes = minutes,
        streams = 90,
        uniqueArtists = 20,
        uniqueTracks = 70,
        currentStreak = 3,
        longestStreak = 10,
        topArtists = topArtists,
    )

    private fun problemsOf(vararg periods: PeriodStats): String =
        assertThrows<ApiException.BadRequest> { StatsValidator.validate(StatsUpload(periods.toList())) }.message

    @Test
    fun `a well-formed upload passes`() {
        assertDoesNotThrow { StatsValidator.validate(StatsUpload(listOf(week(topArtists = listOf(TopItem("Nova Lights", "0abcDEF123")))))) }
    }

    @Test
    fun `periods must start on their first day`() {
        assertTrue(problemsOf(week(start = "2026-11-24")).contains("not the first day of a week"))
        assertTrue(problemsOf(week(start = "24/11/2026")).contains("ISO date"))
    }

    @Test
    fun `values must fit the period`() {
        assertTrue(problemsOf(week(minutes = 7 * 24 * 60 + 1)).contains("minutes"))
        assertTrue(problemsOf(week().copy(currentStreak = 11)).contains("currentStreak"))
    }

    @Test
    fun `top lists are capped and ids checked`() {
        val eleven = List(11) { TopItem("Artist $it") }
        assertTrue(problemsOf(week(topArtists = eleven)).contains("at most 10"))
        assertTrue(problemsOf(week(topArtists = listOf(TopItem("A", "not an id!")))).contains("spotifyId"))
    }

    @Test
    fun `cover URLs must be https and bounded`() {
        StatsValidator.validate(StatsUpload(listOf(week(topArtists = listOf(TopItem("A", imageUrl = "https://i.scdn.co/image/abc"))))))
        assertTrue(problemsOf(week(topArtists = listOf(TopItem("A", imageUrl = "http://i.scdn.co/image/abc")))).contains("imageUrl"))
        assertTrue(problemsOf(week(topArtists = listOf(TopItem("A", imageUrl = "javascript:alert(1)")))).contains("imageUrl"))
        assertTrue(problemsOf(week(topArtists = listOf(TopItem("A", imageUrl = "https://" + "a".repeat(2041))))).contains("imageUrl"))
    }

    @Test
    fun `uploads are bounded and free of duplicates`() {
        assertTrue(problemsOf(week(), week()).contains("duplicate"))
        assertTrue(problemsOf().contains("must not be empty"))
    }
}
