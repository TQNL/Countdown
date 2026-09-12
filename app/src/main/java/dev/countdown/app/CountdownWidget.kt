// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.runtime.*
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

private val WidgetRevision = longPreferencesKey("revision")

/** Invalidate Glance state as well as requesting rendering; a live Glance session
 * otherwise retains values captured before provideContent. */
suspend fun refreshWidgets(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    manager.getGlanceIds(CountdownWidget::class.java).forEach { id ->
        updateAppWidgetState(context, id) { it[WidgetRevision] = System.nanoTime() }
        CountdownWidget().update(context, id)
    }
}

private data class WidgetData(val selection: WidgetSelection?, val access: Boolean, val event: CalendarEvent?, val failed: Boolean)

private suspend fun widgetData(context: Context, id: Int): WidgetData = withContext(Dispatchers.IO) {
    val selection = WidgetStore(context).read(id)
    val repository = CalendarRepository(context)
    val access = repository.hasAccess()
    val result = runCatching { if (access && selection != null) repository.resolve(selection) else null }
    WidgetData(selection, access, result.getOrNull(), result.isFailure)
}

class CountdownWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initial = widgetData(context, appWidgetId)
        val setup = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("countdown://widget/$appWidgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        provideContent {
        val revision = currentState<Preferences>()[WidgetRevision]
        val data by produceState(initial, revision) { value = widgetData(context, appWidgetId) }
        val (selection, access, event, failed) = data
        val target = if (event == null) setup else Intent(context, EventActivity::class.java)
            .setData(Uri.parse("countdown://event/$appWidgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        val status = when {
            !access -> "Allow calendar access"
            selection == null -> "Select event"
            failed -> "Calendar unavailable"
            event == null -> "Event removed"
            else -> null
        }
            val size = LocalSize.current
            val scale = context.resources.configuration.fontScale
            val layout = widgetLayout(size.width.value, size.height.value, scale)
            val compact = layout == WidgetLayout.COMPACT
            val shortWidget = size.height.value / scale.coerceAtLeast(1f) < 150f
            val shortDate = selection?.showDate == true && !compact &&
                size.height.value / scale.coerceAtLeast(1f) in 90f..<150f
            // Read one instant per composition, including state invalidation on clock ticks.
            val now = Instant.now()
            val detail = if (event != null && selection?.showHours == true)
                countdownHours(event.begin, event.allDay, now, ZoneId.systemDefault()) else null
            val description = if (detail != null) "${event!!.title}, ${detail.description}, ${event.dateLabel()}"
                else if (event != null) "${event.title}, ${fullDays(countdownDays(event.begin, event.allDay, LocalDate.now(), ZoneId.systemDefault()))}, ${event.dateLabel()}"
                else "$status. Tap to select an event."
            val modifier = GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFFFAFAFA), Color(0xFF222222)))
                .cornerRadius(16.dp).padding(if (compact || shortWidget) 6.dp else 12.dp)
                .semantics { contentDescription = description }.clickable(actionStartActivity(target))
            if (event == null) {
                Box(modifier, contentAlignment = Alignment.Center) {
                    Label(if (compact && !access) "Allow\ncalendar" else status.orEmpty(), 13, 3)
                }
            } else {
                val days = countdownDays(event.begin, event.allDay, LocalDate.now(), ZoneId.systemDefault())
                if (detail != null) {
                    val height = size.height.value / scale.coerceAtLeast(1f)
                    val width = size.width.value / scale.coerceAtLeast(1f)
                    val dateFits = !compact && (shortDate || height >= 150 || (layout == WidgetLayout.WIDE && height >= 95))
                    Column(modifier, verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Label(event.title, if (compact) 11 else 13, if (height >= 150) 2 else 1)
                        val showDate = selection?.showDate == true && dateFits
                        val countFont = hoursCountFont(width, height, detail.value.length,
                            compact, showDate, detail.direction.isNotEmpty())
                        Label(detail.value, countFont, 1, true)
                        if (detail.direction.isNotEmpty()) Label(detail.direction, 11, 1)
                        if (selection?.showDate == true && dateFits) {
                            Spacer(GlanceModifier.height(2.dp))
                            Label(event.dateLabel(), if (shortDate) 10 else 12, if (layout == WidgetLayout.TALL) 2 else 1)
                        }
                    }
                } else when (layout) {
                    WidgetLayout.COMPACT -> Column(modifier, verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        val availableHeight = (size.height.value - 12) / scale.coerceAtLeast(1f)
                        val titleLines = if (availableHeight >= 90) 2 else 1
                        Label(event.title, 11, titleLines)
                        val text = compactDays(days)
                        val available = (size.width.value - 12) / scale.coerceAtLeast(1f)
                        val heightLimit = ((availableHeight - 14 * titleLines) / 1.3f).toInt().coerceIn(10, 28)
                        val font = (available / (text.length.coerceAtLeast(2) * 0.65f)).toInt().coerceIn(10, heightLimit)
                        Label(text, font, 1, true)
                    }
                    WidgetLayout.WIDE -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                        Column(GlanceModifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Label(if (days == 0L) "Today" else abs(days).toString(), if (abs(days) > 9999) 22 else 30, 1, true)
                            if (days != 0L) Label(if (days > 0) "${if (days == 1L) "day" else "days"} left"
                                else "${if (days == -1L) "day" else "days"} ago", 12, 1)
                        }
                        Spacer(GlanceModifier.width(14.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Label(event.title, 16, 2, left = true)
                            if (selection?.showDate == true && size.height.value / scale >= 90) Label(event.dateLabel(), 12, 1, left = true)
                        }
                    }
                    else -> Column(modifier, verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        val tall = layout == WidgetLayout.TALL
                        Label(event.title, if (tall || shortDate) 13 else 16,
                            if (tall) 3 else if (size.height.value / scale < 140) 1 else 2)
                        Spacer(GlanceModifier.height(if (shortDate) 2.dp else 6.dp))
                        Label(if (days == 0L) "Today" else abs(days).toString(),
                            if (shortDate) 18 else if (tall || abs(days) > 9999) 26 else if (size.height.value / scale < 125) 30 else 36, 1, true)
                        if (days != 0L) Label(if (days > 0) "${if (days == 1L) "day" else "days"} left"
                            else "${if (days == -1L) "day" else "days"} ago", if (shortDate) 11 else 13, 1)
                        if (selection?.showDate == true && (shortDate || size.height.value / scale >= 150)) {
                            Spacer(GlanceModifier.height(if (shortDate) 2.dp else 8.dp))
                            Label(event.dateLabel(), if (shortDate) 10 else 12, if (tall) 2 else 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String, size: Int, lines: Int, bold: Boolean = false, left: Boolean = false) {
    Text(text, maxLines = lines, style = TextStyle(color = ColorProvider(Color(0xFF171717), Color(0xFFF5F5F5)), fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = if (left) TextAlign.Start else TextAlign.Center))
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); scheduleRefresh(context) }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetStore(context).remove(it) }
        super.onDeleted(context, appWidgetIds)
    }
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork("countdown-periodic")
        WorkManager.getInstance(context).cancelUniqueWork("countdown-calendar-watch")
    }
}
