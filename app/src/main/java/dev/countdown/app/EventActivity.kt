// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resolve again at tap time so moved/deleted events never open a stale occurrence. */
class EventActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        lifecycleScope.launch {
            val event = withContext(Dispatchers.IO) {
                val repository = CalendarRepository(this@EventActivity)
                if (!WidgetStore.owns(this@EventActivity, id) || !repository.hasAccess()) null
                else runCatching { WidgetStore(this@EventActivity).read(id)?.let { repository.resolve(it) } }.getOrNull()
            }
            val setup = Intent(this@EventActivity, MainActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            if (event == null) startActivity(setup)
            else if (!openCalendarEvent(event)) startActivity(setup)
            finish()
        }
    }
}
