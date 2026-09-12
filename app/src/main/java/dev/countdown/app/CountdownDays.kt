// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

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
