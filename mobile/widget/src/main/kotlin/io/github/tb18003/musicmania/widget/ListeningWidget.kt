/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.tb18003.musicmania.core.domain.repository.HistoryRepository
import io.github.tb18003.musicmania.core.domain.repository.SyncObserver
import io.github.tb18003.musicmania.core.domain.stats.toDateRange
import io.github.tb18003.musicmania.core.model.DateRange
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

/** Glance cannot use constructor injection; dependencies come through an entry point. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun historyRepository(): HistoryRepository

    fun clock(): Clock
}

internal data class WidgetData(
    val todayMinutes: Long,
    val weekTopArtist: String?,
    val weekMinutes: Long,
)

/** Home-screen widget: today's minutes and this week's top artist. */
class ListeningWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val history = entryPoint.historyRepository()
        val clock = entryPoint.clock()
        val week = DateRange(clock.instant().minus(Duration.ofDays(DAYS_IN_WEEK)), clock.instant().plusSeconds(1))
        val data = WidgetData(
            todayMinutes = history.observeSummary(LocalDate.now(clock).toDateRange(clock)).first().minutes,
            weekTopArtist = history.observeTopArtists(week, 1).first().firstOrNull()?.artistName,
            weekMinutes = history.observeSummary(week).first().minutes,
        )
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        provideContent { WidgetContent(context, data, launch) }
    }

    private companion object {
        const val DAYS_IN_WEEK = 7L
    }
}

@Composable
private fun WidgetContent(context: Context, data: WidgetData, launch: android.content.Intent?) {
    val modifier = GlanceModifier
        .fillMaxSize()
        .cornerRadius(24.dp)
        .background(Color(0xFF111118))
        .padding(16.dp)
    Column(if (launch != null) modifier.clickable(actionStartActivity(launch)) else modifier) {
        Text(context.getString(R.string.widget_today), style = TextStyle(color = ColorProvider(Color(0xFFA3A3B8)), fontSize = 12.sp))
        Text(
            context.getString(R.string.widget_minutes, data.todayMinutes),
            style = TextStyle(color = ColorProvider(Color(0xFFC8FF4D)), fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(context.getString(R.string.widget_week_top), style = TextStyle(color = ColorProvider(Color(0xFFA3A3B8)), fontSize = 12.sp))
        Text(
            data.weekTopArtist ?: "—",
            maxLines = 1,
            style = TextStyle(color = ColorProvider(Color(0xFFF4F4F8)), fontSize = 16.sp, fontWeight = FontWeight.Medium),
        )
        Text(
            context.getString(R.string.widget_week_minutes, data.weekMinutes),
            style = TextStyle(color = ColorProvider(Color(0xFFA3A3B8)), fontSize = 12.sp),
        )
    }
}

class ListeningWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ListeningWidget()
}

/** Refreshes the widget after every background sync (Observer via Hilt multibinding). */
internal class WidgetSyncObserver @Inject constructor(@param:ApplicationContext private val context: Context) : SyncObserver {
    override suspend fun onSyncCompleted() = ListeningWidget().updateAll(context)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetModule {
    @Binds
    @IntoSet
    abstract fun bindsObserver(impl: WidgetSyncObserver): SyncObserver
}
