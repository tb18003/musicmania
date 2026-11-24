/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.openid.appauth.RedirectUriReceiverActivity
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The OAuth redirect must reach AppAuth's receiver through the HTTPS App Link declared in the
 * merged manifest, and nothing broader than the configured callback path may be claimed.
 */
@RunWith(AndroidJUnit4::class)
class OAuthRedirectTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val redirect = Uri.parse(BuildConfig.SPOTIFY_REDIRECT_URI)

    private fun handlersOf(uri: Uri) = context.packageManager
        .queryIntentActivities(
            Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE),
            PackageManager.MATCH_ALL,
        )
        .filter { it.activityInfo.packageName == context.packageName }
        .map { it.activityInfo.name }

    @Test
    fun configuredRedirectIsHandledOnlyByAppAuthReceiver() {
        assertEquals("https", redirect.scheme)
        assertEquals(listOf(RedirectUriReceiverActivity::class.java.name), handlersOf(redirect))
    }

    @Test
    fun otherPathsOnTheRedirectHostAreNotClaimed() {
        val sibling = redirect.buildUpon().path("/somewhere-else").build()
        val plainHttp = redirect.buildUpon().scheme("http").build()

        assertTrue(handlersOf(sibling).isEmpty())
        assertTrue(handlersOf(plainHttp).isEmpty())
    }
}
