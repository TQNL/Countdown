// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.glance.appwidget.updateAll
import androidx.work.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/** Provider observation handles quick edits while alive; persisted work handles process death. */
class CountdownApplication : Application() {
    private var observing = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refresh: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refresh?.cancel()
            refresh = scope.launch { delay(500); refreshWidgets(this@CountdownApplication) }
        }
    }
    override fun onCreate() {
        super.onCreate()
        observeCalendars()
        // TIME_TICK is a system-only, dynamically registered broadcast. It stops
        // with this process; persisted WorkManager remains the background fallback.
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!(getSystemService(POWER_SERVICE) as PowerManager).isInteractive) return
                scope.launch {
                    // Days-only timed events also change from Today to ago at their start.
                    if (WidgetStore.ids(this@CountdownApplication).isNotEmpty())
                        refreshWidgets(this@CountdownApplication)
                }
            }
        }, IntentFilter().apply { addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_SCREEN_ON) })
    }
    @Synchronized fun observeCalendars() {
        if (observing || !CalendarRepository(this).hasAccess()) return
        try {
            contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            observing = true
        } catch (_: SecurityException) {
            // Permission may be revoked between the check and provider registration.
        }
    }
}

fun scheduleRefresh(context: Context) {
    (context.applicationContext as? CountdownApplication)?.observeCalendars()
    if (WidgetStore.ids(context).isEmpty()) return
    val manager = WorkManager.getInstance(context)
    manager.enqueueUniquePeriodicWork("countdown-periodic", ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build())
    if (CalendarRepository(context).hasAccess()) {
        manager.enqueueUniqueWork("countdown-calendar-watch", ExistingWorkPolicy.KEEP, calendarWatch())
    }
}

private fun calendarWatch() = OneTimeWorkRequestBuilder<CalendarWatchWorker>().setConstraints(
    Constraints.Builder().addContentUriTrigger(CalendarContract.CONTENT_URI, true)
        .setTriggerContentUpdateDelay(1, TimeUnit.SECONDS)
        .setTriggerContentMaxDelay(10, TimeUnit.SECONDS).build()
).build()

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (WidgetStore.ids(applicationContext).isEmpty()) return Result.success()
        refreshWidgets(applicationContext)
        return Result.success()
    }
}

class CalendarWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (WidgetStore.ids(applicationContext).isEmpty()) return Result.success()
        refreshWidgets(applicationContext)
        if (CalendarRepository(applicationContext).hasAccess()) {
            WorkManager.getInstance(applicationContext).enqueueUniqueWork("countdown-calendar-watch",
                ExistingWorkPolicy.APPEND_OR_REPLACE, calendarWatch())
        }
        return Result.success()
    }
}

class ClockChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduleRefresh(context)
                refreshWidgets(context)
            } finally { pending.finish() }
        }
    }
}
