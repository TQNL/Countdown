// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

/** Include occurrence times so a recurring event opens at the selected occurrence. */
fun Context.openCalendarEvent(event: CalendarEvent): Boolean {
    return try {
        // Google Calendar treats ACTION_EDIT as insertion. VIEW opens the existing
        // event's page, with its own edit action and calendar access controls.
        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id),
                "vnd.android.cursor.item/event")
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
