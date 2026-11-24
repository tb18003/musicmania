/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import io.github.tb18003.musicmania.core.designsystem.theme.LocalReduceMotion

/**
 * Provided by the app's NavHost so feature screens (which never know about each other)
 * can still share elements, e.g. artwork that flies from a list into a detail header.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Call inside a `composable<Route> { }` destination to enable shared elements in it. */
@Composable
fun AnimatedVisibilityScope.ProvideNavAnimatedScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this, content = content)
}

/** Stable keys so list and detail agree on what is "the same" element. */
object SharedKeys {
    fun artwork(kind: String, name: String, artist: String = "") = "artwork|$kind|$name|$artist"

    fun title(kind: String, name: String, artist: String = "") = "title|$kind|$name|$artist"
}

/** Applies a shared-element transition when inside a navigation transition, no-op otherwise. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementOrSelf(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    if (LocalReduceMotion.current) return this
    return with(shared) {
        this@sharedElementOrSelf.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
        )
    }
}
