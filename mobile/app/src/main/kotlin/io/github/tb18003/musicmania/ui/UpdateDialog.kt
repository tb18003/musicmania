/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.tb18003.musicmania.R
import io.github.tb18003.musicmania.core.common.config.AppInfo
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import io.github.tb18003.musicmania.core.model.AppUpdate
import io.github.tb18003.musicmania.core.model.AppVersion

/** Tells the user a newer release exists and links to where it is published. */
@Composable
fun UpdateDialog(update: AppUpdate, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
        title = { Text(stringResource(R.string.update_title)) },
        text = { Text(stringResource(R.string.update_message, update.latest.toString(), update.current.toString())) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                uriHandler.openUri(AppInfo.RELEASES_URL)
            }) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
    )
}

@Preview
@Composable
private fun UpdateDialogPreview() {
    MusicManiaTheme(darkTheme = true) {
        UpdateDialog(AppUpdate(current = AppVersion(0, 3, 0), latest = AppVersion(0, 4, 0)), onDismiss = {})
    }
}
