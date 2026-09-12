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

    @Test fun timedStartChangesTodayToAgoInEveryLabel() {
        val start = Instant.parse("2026-09-12T12:00:00Z")
        fun at(offset: Long) = countdownDayDetail(start.toEpochMilli(), false, start.plusMillis(offset), amsterdam)
        assertEquals("Today", at(-1).value)
        assertEquals("Now", at(0).value)
        assertEquals("<1", at(1).value)
        assertEquals("day ago", at(1).direction)
        assertEquals("<1d ago", at(1).compact)
        assertEquals("Less than 1 day ago", at(1).description)
        assertEquals("1 day ago", at(86400000).description)
        assertEquals("2 days ago", at(172800000).description)
    }

    @Test fun daysModeKeepsCalendarDayCountingAcrossMidnight() {
        val result = countdownDayDetail(Instant.parse("2026-09-12T21:59:59Z").toEpochMilli(), false,
            Instant.parse("2026-09-12T22:00:01Z"), amsterdam)
        assertEquals("1 day ago", result.description)
        assertEquals("1d ago", result.compact)
    }

    @Test fun allDayEventsStayTodayUntilLocalDateEnds() {
        val start = Instant.parse("2026-09-12T00:00:00Z").toEpochMilli()
        val zone = ZoneId.of("America/Los_Angeles")
        assertEquals("Today", countdownDayDetail(start, true, Instant.parse("2026-09-13T06:59:59Z"), zone).description)
        assertEquals("1 day ago", countdownDayDetail(start, true, Instant.parse("2026-09-13T07:00:00Z"), zone).description)
    }

    @Test fun futureDaysRetainLeftAndSameDayPastUsesDeviceZone() {
        val start = Instant.parse("2026-09-12T22:30:00Z").toEpochMilli()
        assertEquals("1 day left", countdownDayDetail(start, false, Instant.parse("2026-09-12T12:00:00Z"), amsterdam).description)
        assertEquals("Less than 1 day ago", countdownDayDetail(start, false, Instant.parse("2026-09-12T22:30:01Z"), amsterdam).description)
    }

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
