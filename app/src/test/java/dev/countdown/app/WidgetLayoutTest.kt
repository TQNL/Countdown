// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutTest {
    @Test fun smallAndAwkwardShapesHaveExplicitLayouts() {
        assertEquals(WidgetLayout.COMPACT, widgetLayout(70f, 90f))
        assertEquals(WidgetLayout.TALL, widgetLayout(70f, 240f))
        assertEquals(WidgetLayout.WIDE, widgetLayout(300f, 90f))
        assertEquals(WidgetLayout.STANDARD, widgetLayout(170f, 220f))
        assertEquals(WidgetLayout.COMPACT, widgetLayout(300f, 50f))
    }
    @Test fun smartLauncherShortWidgetReservesSpaceForEveryLine() {
        // Pixel Smart Launcher 2x1: 174x94 dp. TextView lines include font padding.
        val count = hoursCountFont(174f, 94f, 6, false, true, true)
        org.junit.Assert.assertTrue(12 + 2 + 1.5f * (13 + count + 11 + 10) <= 94)
        org.junit.Assert.assertTrue(hoursCountFont(74f, 94f, 6, true, false, true) < count)
        org.junit.Assert.assertTrue(hoursCountFont(174f, 94f, 6, false, false, true) > count)
    }
    @Test fun largeFontsUseSimplerLayout() {
        assertEquals(WidgetLayout.COMPACT, widgetLayout(160f, 100f, 1.6f))
    }
    @Test fun compactLabelsKeepPastAndFutureDistinct() {
        assertEquals("14d", compactDays(14))
        assertEquals("Today", compactDays(0))
        assertEquals("2d ago", compactDays(-2))
        assertEquals("1 day left", fullDays(1))
        assertEquals("1 day ago", fullDays(-1))
        assertEquals("14 days left", fullDays(14))
    }
}
