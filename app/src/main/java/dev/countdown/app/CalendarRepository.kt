// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class DeviceCalendar(val id: Long, val name: String)

data class CalendarEvent(
    val id: Long,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val recurring: Boolean,
    val title: String,
    val calendarId: Long,
    val calendarName: String
) : java.io.Serializable {
    fun date() = Instant.ofEpochMilli(begin)
        .atZone(if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()).toLocalDate()
    fun dateLabel(): String = date().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    fun timeLabel(): String = if (allDay) "All day" else Instant.ofEpochMilli(begin)
        .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

class CalendarRepository(private val context: Context) {
    fun hasAccess() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(): List<DeviceCalendar> {
        val result = mutableListOf<DeviceCalendar>()
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
            arrayOf("_id", "calendar_displayName"), null, null, "calendar_displayName ASC")?.use { cursor ->
            while (cursor.moveToNext()) result += DeviceCalendar(cursor.getLong(0), cursor.getString(1).orEmpty())
        }
        return result
    }

    /** Bounded range and result count keep a large synced calendar usable on older phones. */
    fun events(from: Long, to: Long, search: String = "", calendarId: Long? = null): List<CalendarEvent> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val selection = mutableListOf("deleted = 0", "(eventStatus IS NULL OR eventStatus != 2)")
        val args = mutableListOf<String>()
        if (search.isNotBlank()) {
            selection += "title LIKE ? ESCAPE '\\'"
            args += "%${search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        }
        if (calendarId != null) { selection += "calendar_id = ?"; args += calendarId.toString() }
        val result = mutableListOf<CalendarEvent>()
        context.contentResolver.query(uri, arrayOf("event_id", "begin", "end", "allDay", "rrule", "title",
            "calendar_id", "calendar_displayName"), selection.joinToString(" AND "), args.toTypedArray(), "begin ASC, title ASC")?.use { c ->
            while (c.moveToNext() && result.size < 2001) result += CalendarEvent(
                c.getLong(0), c.getLong(1), c.getLong(2), c.getInt(3) == 1,
                !c.getString(4).isNullOrEmpty(), c.getString(5).orEmpty().ifBlank { "Untitled event" },
                c.getLong(6), c.getString(7).orEmpty()
            )
        }
        return result
    }

    /** A single event follows edits. A recurring selection stays on the chosen occurrence. */
    fun resolve(selection: WidgetSelection): CalendarEvent? {
        if (selection.recurring) {
            // Query only the selected event, rather than a capped list of every overlapping event.
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, selection.begin)
                ContentUris.appendId(it, selection.begin + 1)
            }.build()
            context.contentResolver.query(uri, arrayOf("event_id", "begin", "end", "allDay", "title", "calendar_id", "calendar_displayName"),
                "event_id = ? AND begin = ? AND deleted = 0 AND (eventStatus IS NULL OR eventStatus != 2)",
                arrayOf(selection.eventId.toString(), selection.begin.toString()), null)?.use { c ->
                if (c.moveToFirst()) return CalendarEvent(c.getLong(0), c.getLong(1), c.getLong(2), c.getInt(3) == 1,
                    true, c.getString(4).orEmpty().ifBlank { "Untitled event" }, c.getLong(5), c.getString(6).orEmpty())
            }
            return null
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, selection.eventId)
        context.contentResolver.query(uri, arrayOf("_id", "dtstart", "dtend", "allDay", "title", "calendar_id", "calendar_displayName"),
            "deleted = 0 AND (eventStatus IS NULL OR eventStatus != 2)", null, null)?.use { c ->
            if (c.moveToFirst()) return CalendarEvent(c.getLong(0), c.getLong(1), c.getLong(2), c.getInt(3) == 1,
                false, c.getString(4).orEmpty().ifBlank { "Untitled event" }, c.getLong(5), c.getString(6).orEmpty())
        }
        return null
    }
}
