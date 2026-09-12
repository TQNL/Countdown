// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import org.junit.Assert.*
import org.junit.Test

class WidgetSelectionTest {
    private fun event(id: Long = 7, begin: Long = 1000) =
        CalendarEvent(id, begin, begin + 100, false, true, "Repeated event", 1, "Calendar")

    @Test fun repeatingSelectionMarksOnlyChosenOccurrence() {
        val selection = WidgetSelection(7, 1000, true, true)
        assertTrue(selection.matches(event()))
        assertFalse(selection.matches(event(begin = 2000)))
        assertFalse(selection.matches(event(id = 8)))
    }

    @Test fun singleEventRemainsSelectedAfterItsTimeChanges() {
        val selection = WidgetSelection(7, 1000, false, false)
        assertTrue(selection.matches(event(begin = 2000)))
        assertFalse(selection.matches(event(id = 8)))
    }
}
