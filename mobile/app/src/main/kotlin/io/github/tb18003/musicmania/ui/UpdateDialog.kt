/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.tb18003.musicmania.R
import io.github.tb18003.musicmania.core.common.config.AppInfo
import io.github.tb18003.musicmania.core.designsystem.theme.MusicManiaTheme
import io.github.tb18003.musicmania.core.model.AppUpdate
import io.github.tb18003.musicmania.core.model.AppVersion
import io.github.tb18003.musicmania.core.model.ReleaseNotes

/** Tells the user a newer release exists, lists what changed and links to where it is published. */
@Composable
fun UpdateDialog(update: AppUpdate, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val changes = changesFor(update, Locale.current.language)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.update_message, update.latest.toString(), update.current.toString()))
                if (changes.isNotEmpty()) {
                    Text(
                        stringResource(R.string.update_whats_new),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    changes.forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•")
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                uriHandler.openUri(AppInfo.RELEASES_URL)
            }) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
    )
}

/** The changes of every missing release (newest first) in the user's language, kept brief. */
internal fun changesFor(update: AppUpdate, language: String): List<String> =
    update.releases.flatMap { it.linesFor(language) }.take(MAX_CHANGES)

private const val MAX_CHANGES = 6

@Preview
@Composable
private fun UpdateDialogPreview() {
    MusicManiaTheme(darkTheme = true) {
        UpdateDialog(
            AppUpdate(
                current = AppVersion(0, 4, 0),
                latest = AppVersion(0, 4, 1),
                releases = listOf(
                    ReleaseNotes(
                        AppVersion(0, 4, 1),
                        mapOf("en" to listOf("Importing a MusicMania export works again.", "The update message lists what changed.")),
                    ),
                ),
            ),
            onDismiss = {},
        )
    }
}
