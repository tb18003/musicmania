/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Keys of the screens whose entrance animation already played in this session. Held at
 * the app root with [rememberEntranceRegistry]: it survives tab switches and configuration
 * changes, and starts empty when the app is launched again.
 */
@Stable
class EntranceRegistry(seen: Set<String> = emptySet()) {
    private val seen = seen.toMutableSet()

    fun hasSeen(key: String): Boolean = key in seen

    fun markSeen(key: String) {
        seen += key
    }

    internal fun snapshot(): List<String> = seen.toList()

    companion object {
        val Saver: Saver<EntranceRegistry, List<String>> = Saver(save = { it.snapshot() }, restore = { EntranceRegistry(it.toSet()) })
    }
}

@Composable
fun rememberEntranceRegistry(): EntranceRegistry = rememberSaveable(saver = EntranceRegistry.Saver) { EntranceRegistry() }

val LocalEntranceRegistry = staticCompositionLocalOf<EntranceRegistry?> { null }

/**
 * How entrance motion behaves on the current screen.
 *
 * - First visit in the session: everything animates as designed, but list entrances only
 *   during the initial phase (items scrolled into view later just appear).
 * - Later visits: whatever shows up during the initial phase (the screen reloading its data)
 *   appears in its final state; data that changes afterwards (a sync, another period) animates.
 * - [Unscoped] (no [ProvideEntranceOnce] above, e.g. previews and screenshot tests): always animates.
 */
@Immutable
class EntranceState internal constructor(
    private val firstVisit: Boolean,
    private val startedAtNanos: Long?,
    private val clock: () -> Long = System::nanoTime,
) {
    private fun inInitialPhase(): Boolean = startedAtNanos != null && clock() - startedAtNanos < INITIAL_PHASE_NANOS

    /** One-shot entrances such as staggered list items. */
    fun shouldPlayEntrance(): Boolean = startedAtNanos == null || (firstVisit && inInitialPhase())

    /** Data-driven motion such as charts drawing in and counters counting up. */
    fun shouldAnimateDataChange(): Boolean = startedAtNanos == null || firstVisit || !inInitialPhase()

    companion object {
        val Unscoped = EntranceState(firstVisit = true, startedAtNanos = null)

        /** Long enough for a screen to load its data from the local database. */
        internal const val INITIAL_PHASE_NANOS = 1_000_000_000L
    }
}

val LocalEntranceState = compositionLocalOf { EntranceState.Unscoped }

/**
 * Wrap a navigation destination so its entrance animations play only the first time it is
 * shown in the session. [key] identifies the screen (or the item, for detail screens).
 */
@Composable
fun ProvideEntranceOnce(key: String, content: @Composable () -> Unit) {
    val registry = LocalEntranceRegistry.current
    val state = remember(key) { EntranceState(firstVisit = registry?.hasSeen(key) != true, startedAtNanos = System.nanoTime()) }
    LaunchedEffect(key) { registry?.markSeen(key) }
    CompositionLocalProvider(LocalEntranceState provides state, content = content)
}
