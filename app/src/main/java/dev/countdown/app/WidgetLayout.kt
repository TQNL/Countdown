// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import kotlin.math.abs

enum class WidgetLayout { COMPACT, TALL, WIDE, STANDARD }

fun widgetLayout(width: Float, height: Float, fontScale: Float = 1f): WidgetLayout {
    val w = width / fontScale.coerceAtLeast(1f)
    val h = height / fontScale.coerceAtLeast(1f)
    return when {
        w < 110 && h >= 140 -> WidgetLayout.TALL
        w < 110 || h < 70 -> WidgetLayout.COMPACT
        w >= 210 && h < 150 -> WidgetLayout.WIDE
        else -> WidgetLayout.STANDARD
    }
}

fun compactDays(days: Long): String = when {
    days == 0L -> "Today"
    days > 0 -> "${days}d"
    else -> "${abs(days)}d ago"
}

fun fullDays(days: Long): String = when {
    days == 0L -> "Today"
    days > 0 -> "$days ${if (days == 1L) "day" else "days"} left"
    else -> "${abs(days)} ${if (days == -1L) "day" else "days"} ago"
}

/** Reserve vertical space for Android TextView font padding as well as the visible glyphs. */
fun hoursCountFont(width: Float, height: Float, characters: Int, compact: Boolean,
    showDate: Boolean, hasDirection: Boolean): Int {
    val short = height < 150f
    val padding = if (compact || short) 12f else 24f
    val title = (if (compact) 11 else 13) * (if (short) 1 else 2)
    val otherText = title + (if (hasDirection) 11 else 0) + (if (showDate) {
        (if (short) 10 else 12) * (if (width < 110 && !short) 2 else 1)
    } else 0)
    val heightLimit = ((height - padding - if (showDate) 2 else 0) / 1.5f - otherText).toInt()
    val widthLimit = ((width - padding) / (characters.coerceAtLeast(3) * 0.65f)).toInt()
    return minOf(heightLimit, widthLimit, if (compact || short && showDate) 22 else 28).coerceAtLeast(10)
}
