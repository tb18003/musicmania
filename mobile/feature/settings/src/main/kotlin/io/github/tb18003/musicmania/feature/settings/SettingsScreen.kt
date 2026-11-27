/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tb18003.musicmania.core.common.config.AppInfo
import io.github.tb18003.musicmania.core.designsystem.component.Artwork
import io.github.tb18003.musicmania.core.designsystem.component.SegmentedSelector
import io.github.tb18003.musicmania.core.model.SessionState
import io.github.tb18003.musicmania.core.model.ThemeMode
import io.github.tb18003.musicmania.core.ui.Formatters
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val SOURCE_URL = "https://github.com/tb18003/musicmania"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onImport: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export { context.contentResolver.openOutputStream(uri) }
    }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            val message = when (event) {
                is SettingsEvent.Exported -> resources.getString(R.string.feature_settings_exported, event.count)
                SettingsEvent.ExportFailed -> resources.getString(R.string.feature_settings_export_failed)
                SettingsEvent.DataDeleted -> resources.getString(R.string.feature_settings_deleted)
                SettingsEvent.CloudDataDeleted -> resources.getString(R.string.feature_settings_cloud_deleted)
                SettingsEvent.CloudFailed -> resources.getString(R.string.feature_settings_cloud_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onBack = onBack,
            onImport = onImport,
            onThemeMode = viewModel::setThemeMode,
            onDynamicColor = viewModel::setDynamicColor,
            onReduceMotion = viewModel::setReduceMotion,
            onSyncNow = viewModel::syncNow,
            onExport = { exportLauncher.launch("musicmania-history-${LocalDate.now()}.json") },
            onDeleteData = viewModel::deleteLocalData,
            onSignOut = viewModel::signOut,
            onCloudSharing = viewModel::setCloudSharing,
            onDeleteCloudData = viewModel::deleteCloudData,
            onCopySpotifyId = { id ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Spotify ID", id)))
                    snackbarHostState.showSnackbar(resources.getString(R.string.feature_settings_spotify_id_copied))
                }
            },
            onOpenUrl = { url ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: ActivityNotFoundException) {
                    // No browser installed.
                }
            },
        ),
    )
}

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onThemeMode: (ThemeMode) -> Unit = {},
    val onDynamicColor: (Boolean) -> Unit = {},
    val onReduceMotion: (Boolean) -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onDeleteData: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onCloudSharing: (Boolean) -> Unit = {},
    val onDeleteCloudData: () -> Unit = {},
    val onCopySpotifyId: (String) -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
)

private enum class PendingConfirmation { DELETE, SIGN_OUT, DELETE_CLOUD }

@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    var confirmation by rememberSaveable { mutableStateOf<PendingConfirmation?>(null) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.feature_settings_back))
                }
                Text(stringResource(R.string.feature_settings_title), style = MaterialTheme.typography.titleLarge)
            }
        }
        item { AccountCard(state) }

        item { SectionTitle(R.string.feature_settings_appearance) }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.feature_settings_theme), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(8.dp))
                SegmentedSelector(
                    options = listOf(
                        stringResource(R.string.feature_settings_theme_system),
                        stringResource(R.string.feature_settings_theme_light),
                        stringResource(R.string.feature_settings_theme_dark),
                    ),
                    selectedIndex = state.preferences.themeMode.ordinal,
                    onSelect = { actions.onThemeMode(ThemeMode.entries[it]) },
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
                SwitchRow(
                    R.string.feature_settings_dynamic_color,
                    R.string.feature_settings_dynamic_color_body,
                    state.preferences.useDynamicColor,
                    actions.onDynamicColor,
                )
            }
        }
        item {
            SwitchRow(
                R.string.feature_settings_reduce_motion,
                R.string.feature_settings_reduce_motion_body,
                state.preferences.reduceMotion,
                actions.onReduceMotion,
            )
        }

        item { SectionTitle(R.string.feature_settings_data) }
        item {
            val subtitle =
                state.sync.lastSuccessfulSync?.let { stringResource(R.string.feature_settings_last_sync, Formatters.relative(it)) }
                    ?: stringResource(R.string.feature_settings_never_synced)
            ActionRow(
                Icons.Rounded.Sync,
                stringResource(R.string.feature_settings_sync_now),
                subtitle,
                enabled =
                state.session == SessionState.SignedIn,
                onClick = actions.onSyncNow,
            )
        }
        item {
            ActionRow(
                Icons.Rounded.FileUpload,
                stringResource(R.string.feature_settings_import),
                stringResource(R.string.feature_settings_import_body),
                onClick = actions.onImport,
            )
        }
        item {
            ActionRow(
                Icons.Rounded.FileDownload,
                stringResource(R.string.feature_settings_export),
                stringResource(R.string.feature_settings_export_body, Formatters.number(state.playCount.toLong())),
                onClick = actions.onExport,
            )
        }
        item {
            ActionRow(
                Icons.Rounded.DeleteForever,
                stringResource(R.string.feature_settings_delete),
                stringResource(R.string.feature_settings_delete_body),
            ) {
                confirmation = PendingConfirmation.DELETE
            }
        }

        state.cloud?.let { cloud ->
            rankingSection(cloud, state.profile?.id, actions) { confirmation = PendingConfirmation.DELETE_CLOUD }
        }

        item { SectionTitle(R.string.feature_settings_about) }
        item {
            ActionRow(Icons.Rounded.Code, stringResource(R.string.feature_settings_source), SOURCE_URL) { actions.onOpenUrl(SOURCE_URL) }
        }
        item {
            ActionRow(
                Icons.Rounded.Gavel,
                stringResource(R.string.feature_settings_license),
                stringResource(R.string.feature_settings_license_body),
            ) {
                actions.onOpenUrl(LICENSE_URL)
            }
        }
        item {
            ActionRow(
                Icons.Rounded.Description,
                stringResource(R.string.feature_settings_oss),
                stringResource(R.string.feature_settings_oss_body),
            ) {
                showLicenses =
                    true
            }
        }
        item {
            ActionRow(
                Icons.Rounded.Info,
                stringResource(R.string.feature_settings_version),
                stringResource(R.string.feature_settings_version_body, state.appVersion),
            ) {
                actions.onOpenUrl(AppInfo.RELEASES_URL)
            }
        }
        item {
            ActionRow(
                Icons.AutoMirrored.Rounded.Logout,
                stringResource(R.string.feature_settings_sign_out),
                stringResource(R.string.feature_settings_sign_out_body),
            ) {
                confirmation = PendingConfirmation.SIGN_OUT
            }
        }
        item {
            Text(
                stringResource(R.string.feature_settings_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }

    confirmation?.let { pending ->
        val (title, body) = when (pending) {
            PendingConfirmation.DELETE -> R.string.feature_settings_delete to R.string.feature_settings_delete_confirm
            PendingConfirmation.SIGN_OUT -> R.string.feature_settings_sign_out to R.string.feature_settings_sign_out_confirm
            PendingConfirmation.DELETE_CLOUD -> R.string.feature_settings_cloud_delete to R.string.feature_settings_cloud_delete_confirm
        }
        ConfirmDialog(
            title = stringResource(title),
            body = stringResource(body),
            onDismiss = { confirmation = null },
            onConfirm = {
                confirmation = null
                when (pending) {
                    PendingConfirmation.DELETE -> actions.onDeleteData()
                    PendingConfirmation.SIGN_OUT -> actions.onSignOut()
                    PendingConfirmation.DELETE_CLOUD -> actions.onDeleteCloudData()
                }
            },
        )
    }
    if (showLicenses) LicensesDialog(onDismiss = { showLicenses = false })
}

@Composable
private fun AccountCard(state: SettingsUiState) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(20.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(state.profile?.imageUrl, state.profile?.displayName ?: "♪", shape = CircleShape, modifier = Modifier.size(56.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    state.profile?.displayName ?: stringResource(R.string.feature_settings_listener),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(
                        if (state.session ==
                            SessionState.Demo
                        ) {
                            R.string.feature_settings_demo_mode
                        } else {
                            R.string.feature_settings_connected
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: Int, body: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(body)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun ConfirmDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.feature_settings_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** Main third-party components and their licences (all compatible with GPL-3.0). */
private val openSourceLibraries = listOf(
    "AndroidX, Jetpack Compose, Room, WorkManager, DataStore, Paging, Glance" to "Apache 2.0",
    "Kotlin, Coroutines, kotlinx.serialization" to "Apache 2.0",
    "Dagger / Hilt" to "Apache 2.0",
    "Retrofit, OkHttp" to "Apache 2.0",
    "Coil" to "Apache 2.0",
    "Google Tink" to "Apache 2.0",
    "AppAuth for Android" to "Apache 2.0",
    "Space Grotesk font" to "SIL Open Font License 1.1",
)

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_settings_oss)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                openSourceLibraries.forEach { (name, license) ->
                    Column {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Text(license, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}

/** Friends leaderboard: opt-in sharing, my Spotify id (to get allow-listed) and server-side deletion. */
private fun LazyListScope.rankingSection(cloud: CloudSettings, spotifyUserId: String?, actions: SettingsActions, onDelete: () -> Unit) {
    item { SectionTitle(R.string.feature_settings_ranking) }
    if (cloud.notAllowlisted) {
        item {
            Text(
                stringResource(R.string.feature_settings_cloud_not_allowlisted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    } else {
        item {
            SwitchRow(
                R.string.feature_settings_cloud_sharing,
                R.string.feature_settings_cloud_sharing_body,
                checked = cloud.sharingEnabled == true,
                onChange = { if (!cloud.busy && cloud.sharingEnabled != null) actions.onCloudSharing(it) },
            )
        }
    }
    spotifyUserId?.let { id ->
        item {
            ActionRow(Icons.Rounded.ContentCopy, stringResource(R.string.feature_settings_spotify_id), id) { actions.onCopySpotifyId(id) }
        }
    }
    if (!cloud.notAllowlisted) {
        item {
            ActionRow(
                Icons.Rounded.CloudOff,
                stringResource(R.string.feature_settings_cloud_delete),
                stringResource(R.string.feature_settings_cloud_delete_body),
                enabled = !cloud.busy,
                onClick = onDelete,
            )
        }
    }
}
