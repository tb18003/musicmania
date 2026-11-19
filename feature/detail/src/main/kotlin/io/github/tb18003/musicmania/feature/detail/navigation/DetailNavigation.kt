/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.detail.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.tb18003.musicmania.core.ui.ProvideNavAnimatedScope
import io.github.tb18003.musicmania.feature.detail.DetailKind
import io.github.tb18003.musicmania.feature.detail.DetailRoute
import kotlinx.serialization.Serializable

/**
 * Type-safe route. Property names match [io.github.tb18003.musicmania.feature.detail.DetailViewModel]
 * argument keys. The image URL travels along so the shared element shows instantly.
 */
@Serializable
data class DetailDestination(
    val kind: String,
    val id: String? = null,
    val name: String,
    val artist: String = "",
    val imageUrl: String? = null,
)

fun NavController.navigateToTrack(id: String?, name: String, artist: String, imageUrl: String?) =
    navigate(DetailDestination(DetailKind.TRACK.key, id, name, artist, imageUrl))

fun NavController.navigateToArtist(id: String?, name: String, imageUrl: String?) =
    navigate(DetailDestination(DetailKind.ARTIST.key, id, name, "", imageUrl))

fun NavController.navigateToAlbum(id: String?, name: String, artist: String, imageUrl: String?) =
    navigate(DetailDestination(DetailKind.ALBUM.key, id, name, artist, imageUrl))

fun NavGraphBuilder.detailScreen(onBack: () -> Unit) {
    composable<DetailDestination> { ProvideNavAnimatedScope { DetailRoute(onBack) } }
}
