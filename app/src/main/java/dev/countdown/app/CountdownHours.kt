// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Whole elapsed hours, split into 24-hour days. All-day events use local midnight. */
data class HourCountdown(val days: Long = 0, val hours: Long = 0, val past: Boolean = false,
    val underHour: Boolean = false, val status: String? = null) {
    val value: String get() = status ?: if (underHour) "<1h" else
        if (days == 0L) "${hours}h" else "${days}d ${hours}h"
    val direction: String get() = if (status != null) "" else if (past) "ago" else "left"
    val description: String get() = status ?: run {
        val amount = if (underHour) "Less than 1 hour" else buildList {
            if (days > 0) add("$days ${if (days == 1L) "day" else "days"}")
            add("$hours ${if (hours == 1L) "hour" else "hours"}")
        }.joinToString(" ")
        "$amount $direction"
    }
}

fun countdownHours(beginMillis: Long, allDay: Boolean, now: Instant, zone: ZoneId): HourCountdown {
    val begin = Instant.ofEpochMilli(beginMillis)
    val eventDate = begin.atZone(ZoneOffset.UTC).toLocalDate()
    if (allDay && eventDate == now.atZone(zone).toLocalDate()) return HourCountdown(status = "Today")
    val target = if (allDay) eventDate.atStartOfDay(zone).toInstant() else begin
    val duration = Duration.between(now, target)
    if (duration.isZero) return HourCountdown(status = "Now")
    val hours = duration.abs().toHours()
    return HourCountdown(hours / 24, hours % 24, duration.isNegative, hours == 0L)
}
