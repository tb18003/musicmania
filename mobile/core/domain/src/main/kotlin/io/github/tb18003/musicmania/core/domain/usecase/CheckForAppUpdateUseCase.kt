/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.domain.usecase

import io.github.tb18003.musicmania.core.common.config.AppInfo
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.domain.repository.CloudRepository
import io.github.tb18003.musicmania.core.model.AppUpdate
import io.github.tb18003.musicmania.core.model.AppVersion
import javax.inject.Inject

/**
 * Asks the MusicMania API for the latest release and returns it when it is newer than the installed one.
 * Null when up to date, when the API is not configured or unreachable, or when either version is malformed:
 * an update check must never get in the user's way.
 */
class CheckForAppUpdateUseCase @Inject constructor(
    private val cloud: CloudRepository,
    private val appInfo: AppInfo,
) {
    suspend operator fun invoke(): AppUpdate? {
        val current = AppVersion.parse(appInfo.versionName)?.takeIf { cloud.isEnabled } ?: return null
        val release = (cloud.latestRelease() as? DataResult.Success)?.data ?: return null
        return AppVersion.parse(release.version)?.takeIf { it > current }?.let { latest ->
            // Only what the user is missing: the releases after theirs, up to the latest, newest first.
            val missing = release.releases.filter { it.version > current && it.version <= latest }.sortedByDescending { it.version }
            AppUpdate(current, latest, missing)
        }
    }
}
