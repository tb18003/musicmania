/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.leaderboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import io.github.tb18003.musicmania.core.model.FriendStats
import io.github.tb18003.musicmania.core.model.LeaderboardEntry
import io.github.tb18003.musicmania.core.model.RankedItem
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs

/** The ranking with a friend's dialog open, covers included (served by a fake image loader). */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-w411dp-h891dp-xxhdpi")
class FriendDialogScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun fakeCovers() {
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(RuntimeEnvironment.getApplication())
                .components { add(GeneratedCovers) }
                .coroutineContext(EmptyCoroutineContext)
                .build(),
        )
    }

    @After
    fun resetCovers() = SingletonImageLoader.reset()

    @Test
    fun friendDialogDark() = capture(dark = true, name = "friend_dialog_dark")

    @Test
    fun friendDialogLight() = capture(dark = false, name = "friend_dialog_light")

    private fun capture(dark: Boolean, name: String) {
        composeRule.setContent {
            MusicManiaTheme(darkTheme = dark, reduceMotion = true) {
                // The app draws the background behind every screen; here the test does.
                Surface(color = MaterialTheme.colorScheme.background) {
                    LeaderboardScreen(
                        state = LeaderboardUiState(
                            content = LeaderboardContent.Ranking(entries),
                            selected = SelectedFriend("ana", friend, isLoading = false),
                        ),
                        actions = LeaderboardActions(),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        fun cover(name: String) = "https://i.scdn.co/image/${name.hashCode()}"

        val entries = listOf(
            LeaderboardEntry(1, "ana", "Ana García", cover("ana"), 1_510, isMe = false),
            LeaderboardEntry(2, "me", "Tú", cover("me"), 1_240, isMe = true),
            LeaderboardEntry(3, "bruno", "Bruno", null, 860, isMe = false),
        )

        val friend = FriendStats(
            spotifyUserId = "ana",
            displayName = "Ana García",
            minutes = 1_510,
            streams = 520,
            uniqueArtists = 33,
            currentStreak = 30,
            topArtists = listOf("Nova Lights", "Coral Echo", "Velvet Static", "Paper Comets", "Los Satélites")
                .map { RankedItem(it, cover(it)) },
            // One track without a cover: the generated artwork stands in for it.
            topTracks = listOf("Low Tide", "Golden Hour Drive", "Las Luces de la Ciudad", "Static Hearts", "Echoes")
                .map { RankedItem(it, cover(it).takeIf { _ -> it != "Echoes" }) },
        )
    }
}

/** Answers every request with a gradient cover derived from its URL, so screenshots are stable. */
private object GeneratedCovers : Interceptor {
    private const val SIZE = 160
    private const val HUES = 360f

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val seed = abs(chain.request.data.toString().hashCode())
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val start = Color.HSVToColor(floatArrayOf(seed % HUES, 0.7f, 0.95f))
        val end = Color.HSVToColor(floatArrayOf((seed / 7 + 90) % HUES, 0.85f, 0.5f))
        canvas.drawPaint(
            Paint().apply {
                shader = LinearGradient(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), start, end, Shader.TileMode.CLAMP)
            },
        )
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 4f, stroke)
        return SuccessResult(image = bitmap.asImage(), request = chain.request, dataSource = DataSource.MEMORY)
    }
}
