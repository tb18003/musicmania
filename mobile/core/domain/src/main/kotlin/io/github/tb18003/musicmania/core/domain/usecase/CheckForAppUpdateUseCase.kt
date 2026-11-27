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
        if (!cloud.isEnabled) return null
        val current = AppVersion.parse(appInfo.versionName) ?: return null
        val latest = (cloud.latestAppVersion() as? DataResult.Success)?.data?.let(AppVersion::parse) ?: return null
        return AppUpdate(current, latest).takeIf { latest > current }
    }
}
