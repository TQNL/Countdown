// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CountdownHoursTest {
    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-09-12T12:00:00Z")
    private fun at(seconds: Long) = countdownHours(now.plusSeconds(seconds).toEpochMilli(), false, now, utc)

    @Test fun splitsElapsedHoursIntoDays() {
        assertEquals("2d 5h", at(53 * 3600).value)
        assertEquals("left", at(53 * 3600).direction)
        assertEquals("1d 0h", at(24 * 3600).value)
        assertEquals("23h", at(24 * 3600 - 1).value)
    }
    @Test fun handlesStartAndUnderHourWithoutNegativeZero() {
        assertEquals("Now", at(0).value)
        assertEquals("<1h", at(1).value)
        assertEquals("<1h", at(-1).value)
        assertEquals("ago", at(-1).direction)
        assertEquals("1h", at(-3600).value)
        assertEquals("2d 5h", at(-53 * 3600).value)
        assertEquals("Less than 1 hour left", at(3599).description)
    }
    @Test fun allDayDateUsesLocalMidnightAndToday() {
        val date = Instant.parse("2026-09-13T00:00:00Z").toEpochMilli()
        val zone = ZoneId.of("America/Los_Angeles")
        assertEquals("19h", countdownHours(date, true, now, zone).value)
        assertEquals("Today", countdownHours(date, true, Instant.parse("2026-09-14T06:59:59Z"), zone).value)
        assertEquals("1d 0h", countdownHours(date, true, Instant.parse("2026-09-14T07:00:00Z"), zone).value)
    }
    @Test fun daylightSavingUsesElapsedHours() {
        val zone = ZoneId.of("Europe/Amsterdam")
        val spring = countdownHours(Instant.parse("2026-03-30T00:00:00Z").toEpochMilli(), true,
            Instant.parse("2026-03-28T23:00:00Z"), zone)
        assertEquals("23h", spring.value)
        val fall = countdownHours(Instant.parse("2026-10-26T00:00:00Z").toEpochMilli(), true,
            Instant.parse("2026-10-24T22:00:00Z"), zone)
        assertEquals("1d 1h", fall.value)
    }
    @Test fun timedEventsDoNotChangeDurationWhenZoneChanges() {
        val end = now.plusSeconds(90000).toEpochMilli()
        assertEquals(countdownHours(end, false, now, utc),
            countdownHours(end, false, now, ZoneId.of("Pacific/Auckland")))
    }
    @Test fun midnightDoesNotTurnATimedSubHourEventIntoToday() {
        val result = countdownHours(Instant.parse("2026-09-13T00:15:00Z").toEpochMilli(), false,
            Instant.parse("2026-09-12T23:45:00Z"), utc)
        assertEquals("<1h", result.value)
        assertEquals("left", result.direction)
    }
    @Test fun hourModeDefaultsOffForExistingSelections() {
        assertFalse(WidgetSelection(1, 0, false, true).showHours)
    }
    @Test fun leapDayAndFarFutureAreSupported() {
        assertEquals("1d 1h", countdownHours(Instant.parse("2028-03-01T01:00:00Z").toEpochMilli(), false,
            Instant.parse("2028-02-29T00:00:00Z"), utc).value)
        assertTrue(countdownHours(Instant.parse("2200-01-01T00:00:00Z").toEpochMilli(), false, now, utc).days > 60000)
    }
}
