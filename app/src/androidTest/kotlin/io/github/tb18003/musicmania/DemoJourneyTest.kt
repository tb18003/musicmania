/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import io.github.tb18003.musicmania.feature.auth.R as AuthR
import io.github.tb18003.musicmania.feature.home.R as HomeR
import io.github.tb18003.musicmania.feature.insights.R as InsightsR
import io.github.tb18003.musicmania.feature.top.R as TopR

/**
 * Smoke test of the whole app on a device: real Hilt graph, real Room database seeded by demo
 * mode, real navigation. The orchestrator clears app data first, so it always starts signed out.
 */
@RunWith(AndroidJUnit4::class)
class DemoJourneyTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun text(@StringRes id: Int) = compose.activity.getString(id)

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun tab(@StringRes label: Int) = compose.onNode(hasText(text(label)) and isTab)

    private fun waitForText(@StringRes id: Int) = compose.waitUntil(TIMEOUT_MS) {
        compose.onAllNodes(hasText(text(id))).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun demoModeOpensHomeAndEveryTopLevelDestination() {
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodes(hasText(text(AuthR.string.feature_auth_demo))).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(AuthR.string.feature_auth_demo)).performClick()

        waitForText(HomeR.string.feature_home_minutes)
        tab(R.string.nav_home).assertIsSelected()

        tab(R.string.nav_top).performClick()
        tab(R.string.nav_top).assertIsSelected()
        waitForText(TopR.string.feature_top_tab_artists)

        tab(R.string.nav_history).performClick()
        tab(R.string.nav_history).assertIsSelected()

        tab(R.string.nav_insights).performClick()
        tab(R.string.nav_insights).assertIsSelected()
        waitForText(InsightsR.string.feature_insights_clock)
    }

    private companion object {
        /** Demo seeding writes ~14 months of plays on first launch. */
        const val TIMEOUT_MS = 20_000L
    }
}
