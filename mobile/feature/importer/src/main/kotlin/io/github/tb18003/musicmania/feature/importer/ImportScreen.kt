/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.feature.importer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tb18003.musicmania.core.designsystem.chart.RingProgress
import io.github.tb18003.musicmania.core.designsystem.component.AnimatedCounter
import io.github.tb18003.musicmania.core.designsystem.component.GradientButton
import io.github.tb18003.musicmania.core.designsystem.theme.LocalReduceMotion
import io.github.tb18003.musicmania.core.designsystem.theme.StatNumberStyle
import io.github.tb18003.musicmania.core.model.ImportResult
import io.github.tb18003.musicmania.core.ui.Formatters
import io.github.tb18003.musicmania.core.ui.message

private const val PRIVACY_URL = "https://www.spotify.com/account/privacy/"

@Composable
fun ImportRoute(onBack: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris.map { UriImportSource(context.contentResolver, it) })
    }
    ImportScreen(
        state = state,
        onBack = onBack,
        onPickFiles = {
            picker.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "application/x-zip-compressed"))
        },
        onOpenPrivacyPage = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
            } catch (_: ActivityNotFoundException) {
                // No browser available.
            }
        },
        onCancel = viewModel::cancel,
        onReset = viewModel::reset,
    )
}

@Composable
fun ImportScreen(
    state: ImportUiState,
    onBack: () -> Unit,
    onPickFiles: () -> Unit,
    onOpenPrivacyPage: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.feature_importer_back)) }
            Text(stringResource(R.string.feature_importer_title), style = MaterialTheme.typography.titleLarge)
        }
        AnimatedContent(targetState = state, contentKey = {
            it::class
        }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "import") { current ->
            Column(Modifier.padding(20.dp)) {
                when (current) {
                    ImportUiState.Idle -> Guide(onPickFiles, onOpenPrivacyPage)
                    is ImportUiState.Running -> Running(current, onCancel)
                    is ImportUiState.Done -> Done(current.result, onBack, onReset)
                    is ImportUiState.Failed -> Failed(current, onPickFiles)
                }
            }
        }
    }
}

@Composable
private fun Guide(onPickFiles: () -> Unit, onOpenPrivacyPage: () -> Unit) {
    Text(stringResource(R.string.feature_importer_headline), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.feature_importer_intro),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    Step(1, stringResource(R.string.feature_importer_step1_title), stringResource(R.string.feature_importer_step1_body)) {
        OutlinedButton(onClick = onOpenPrivacyPage) { Text(stringResource(R.string.feature_importer_open_privacy)) }
    }
    Step(2, stringResource(R.string.feature_importer_step2_title), stringResource(R.string.feature_importer_step2_body))
    Step(3, stringResource(R.string.feature_importer_step3_title), stringResource(R.string.feature_importer_step3_body))
    Spacer(Modifier.height(20.dp))
    GradientButton(
        text = stringResource(R.string.feature_importer_pick),
        onClick = onPickFiles,
        leading = { Icon(Icons.Rounded.FolderOpen, null) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.feature_importer_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Step(number: Int, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.padding(vertical = 10.dp)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) { Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary) }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}

@Composable
private fun Running(state: ImportUiState.Running, onCancel: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val sweep by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = if (reduce) 0f else FULL_TURN,
        animationSpec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)),
        label = "sweep",
    )
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            RingProgress(INDETERMINATE_ARC, Modifier.size(180.dp).graphicsLayer { rotationZ = sweep })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedCounter(
                    state.playsRead.toLong(),
                    style = StatNumberStyle.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize),
                    durationMillis = COUNTER_MS,
                )
                Text(stringResource(R.string.feature_importer_plays_read), style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            state.currentFile,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onCancel) { Text(stringResource(R.string.feature_importer_cancel)) }
    }
}

@Composable
private fun Done(result: ImportResult, onFinish: () -> Unit, onImportMore: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Rounded.CheckCircle,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp).graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.feature_importer_done), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultRow(stringResource(R.string.feature_importer_result_new), Formatters.number(result.eventsInserted.toLong()))
                ResultRow(stringResource(R.string.feature_importer_result_duplicates), Formatters.number(result.duplicates.toLong()))
                ResultRow(stringResource(R.string.feature_importer_result_skipped), Formatters.number(result.skippedEntries.toLong()))
                ResultRow(stringResource(R.string.feature_importer_result_files), result.filesProcessed.toString())
                if (result.earliest != null && result.latest != null) {
                    ResultRow(
                        stringResource(R.string.feature_importer_result_range),
                        "${Formatters.date(result.earliest!!)} – ${Formatters.date(result.latest!!)}",
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        GradientButton(stringResource(R.string.feature_importer_finish), onClick = onFinish, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onImportMore) { Text(stringResource(R.string.feature_importer_more)) }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Failed(state: ImportUiState.Failed, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text(state.error.message(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.feature_importer_try_another)) }
    }
}

private const val FULL_TURN = 360f
private const val SPIN_MS = 1_400
private const val INDETERMINATE_ARC = 0.3f
private const val COUNTER_MS = 400
