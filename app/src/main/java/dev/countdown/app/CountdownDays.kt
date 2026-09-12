// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class DayCountdown(val days: Long, val startedToday: Boolean = false, val now: Boolean = false) {
    val value: String get() = when {
        now -> "Now"
        startedToday -> "<1"
        days == 0L -> "Today"
        else -> abs(days).toString()
    }
    val direction: String get() = when {
        startedToday -> "day ago"
        days == 0L -> ""
        else -> "${if (abs(days) == 1L) "day" else "days"} ${if (days < 0) "ago" else "left"}"
    }
    val compact: String get() = if (startedToday) "<1d ago" else if (now) "Now" else compactDays(days)
    val description: String get() = if (startedToday) "Less than 1 day ago" else if (now) "Now" else fullDays(days)
}

fun countdownDayDetail(beginMillis: Long, allDay: Boolean, now: Instant, zone: ZoneId): DayCountdown {
    val days = countdownDays(beginMillis, allDay, now.atZone(zone).toLocalDate(), zone)
    val begin = Instant.ofEpochMilli(beginMillis)
    return DayCountdown(days, !allDay && days == 0L && begin.isBefore(now), !allDay && begin == now)
}

/** Positive means upcoming, zero means today, negative means past.
 * Calendar Provider encodes all-day dates at UTC midnight. They must not shift
 * to yesterday when displayed west of UTC. Timed events use the device's zone.
 */
fun countdownDays(
    beginMillis: Long,
    allDay: Boolean,
    today: LocalDate,
    deviceZone: ZoneId
): Long {
    val eventZone = if (allDay) ZoneOffset.UTC else deviceZone
    val eventDate = Instant.ofEpochMilli(beginMillis).atZone(eventZone).toLocalDate()
    return ChronoUnit.DAYS.between(today, eventDate)
}
