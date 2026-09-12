// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownDaysTest {
    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    @Test fun todayPastAndFutureUseCalendarDates() {
        val today = LocalDate.parse("2026-09-12")
        for ((date, expected) in listOf("2026-09-11" to -1L, "2026-09-12" to 0L, "2026-09-13" to 1L)) {
            assertEquals(expected, countdownDays(Instant.parse("${date}T00:00:00Z").toEpochMilli(), true, today, amsterdam))
        }
    }

    @Test fun allDayDateDoesNotShiftInWesternTimezone() {
        assertEquals(1L, countdownDays(Instant.parse("2026-09-13T00:00:00Z").toEpochMilli(), true,
            LocalDate.parse("2026-09-12"), ZoneId.of("America/Los_Angeles")))
    }

    @Test fun timedEventUsesLocalDateAcrossUtcBoundary() {
        assertEquals(1L, countdownDays(Instant.parse("2026-09-12T22:05:00Z").toEpochMilli(), false,
            LocalDate.parse("2026-09-12"), amsterdam))
    }

    @Test fun daylightSavingTransitionsStillCountAsOneDay() {
        for (date in listOf(LocalDate.parse("2026-03-28"), LocalDate.parse("2026-10-24"))) {
            val nextDay = date.plusDays(1).atTime(12, 0).atZone(amsterdam).toInstant()
            assertEquals(1L, countdownDays(nextDay.toEpochMilli(), false, date, amsterdam))
        }
    }
}
