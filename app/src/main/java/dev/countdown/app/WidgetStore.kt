// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import org.json.JSONObject

data class WidgetSelection(val eventId: Long, val begin: Long, val recurring: Boolean, val showDate: Boolean, val showHours: Boolean = false) {
    fun matches(event: CalendarEvent): Boolean = eventId == event.id && (!recurring || begin == event.begin)
}

class WidgetStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("widget-selections", Context.MODE_PRIVATE)

    fun read(id: Int): WidgetSelection? = preferences.getString(id.toString(), null)?.let {
        runCatching {
            val json = JSONObject(it)
            WidgetSelection(json.getLong("eventId"), json.getLong("begin"), json.getBoolean("recurring"), json.optBoolean("showDate", true), json.optBoolean("showHours", false))
        }.getOrNull()
    }

    fun save(id: Int, event: CalendarEvent, showDate: Boolean, showHours: Boolean = false) {
        val json = JSONObject().put("eventId", event.id).put("begin", event.begin)
            .put("recurring", event.recurring).put("showDate", showDate).put("showHours", showHours)
        check(preferences.edit().putString(id.toString(), json.toString()).commit()) { "Could not save widget" }
    }

    fun saveOptions(id: Int, showDate: Boolean, showHours: Boolean) {
        val selected = checkNotNull(read(id)) { "Select an event first" }
        val json = JSONObject().put("eventId", selected.eventId).put("begin", selected.begin)
            .put("recurring", selected.recurring).put("showDate", showDate).put("showHours", showHours)
        check(preferences.edit().putString(id.toString(), json.toString()).commit()) { "Could not save widget" }
    }

    fun name(id: Int): String = synchronized(nameLock) {
        preferences.getString("name-$id", null) ?: run {
            var number = preferences.getInt("next-name", 1)
            val used = preferences.all.filterKeys { it.startsWith("name-") }.values.toSet()
            while ("Widget $number" in used) number++
            val name = "Widget $number"
            check(preferences.edit().putString("name-$id", name).putInt("next-name", number + 1).commit())
            name
        }
    }

    fun rename(id: Int, value: String) = synchronized(nameLock) {
        val name = value.trim()
        require(name.isNotEmpty() && name.length <= 40) { "Use 1–40 characters." }
        require(ids(context).filter { it != id }.none { name(it).equals(name, ignoreCase = true) }) {
            "Another widget already uses this name."
        }
        check(preferences.edit().putString("name-$id", name).commit()) { "Could not rename widget" }
    }

    fun remove(id: Int) { preferences.edit().remove(id.toString()).remove("name-$id").apply() }

    companion object {
        private val nameLock = Any()
        fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, CountdownWidgetReceiver::class.java))
        fun owns(context: Context, id: Int) = id in ids(context)
    }
}
