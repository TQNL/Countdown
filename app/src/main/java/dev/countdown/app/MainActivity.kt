// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 TQNL and contributors

package dev.countdown.app

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.provider.CalendarContract
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private data class WidgetSummary(val id: Int, val name: String, val label: String)

class MainActivity : ComponentActivity() {
    private var access by mutableStateOf(false)
    private var revision by mutableIntStateOf(0)
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        access = it
        revision++
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        access = CalendarRepository(this).hasAccess()
        enableEdgeToEdge()
        val suppliedId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val externalId = suppliedId.takeIf { WidgetStore.owns(this, it) }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { CountdownScreen(externalId) }
            }
        }
        val requested = getPreferences(MODE_PRIVATE).getBoolean("requested-calendar", false)
        if (savedInstanceState == null && !access && (!requested || shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR))) requestCalendar()
    }
    override fun onResume() {
        super.onResume()
        access = CalendarRepository(this).hasAccess()
        revision++
    }
    private fun requestCalendar() {
        getPreferences(MODE_PRIVATE).edit().putBoolean("requested-calendar", true).apply()
        permission.launch(Manifest.permission.READ_CALENDAR)
    }

    private fun openCalendar(date: LocalDate): Boolean {
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val uri = ContentUris.withAppendedId(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build(), millis)
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: ActivityNotFoundException) {
            // Some calendar apps expose only their main screen, not Calendar Provider time links.
            try {
                startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
                true
            } catch (_: ActivityNotFoundException) { false }
        }
    }

    @Composable
    private fun CountdownScreen(externalId: Int?) {
        var editingId by rememberSaveable { mutableStateOf(externalId) }
        val scope = rememberCoroutineScope()
        var saveError by remember { mutableStateOf<String?>(null) }
        var saving by remember { mutableStateOf(false) }
        LaunchedEffect(access, revision) {
            withContext(Dispatchers.IO) {
                scheduleRefresh(this@MainActivity)
                refreshWidgets(this@MainActivity)
            }
        }
        BackHandler(editingId != null) { if (!saving) { if (externalId != null) finish() else editingId = null } }
        Column(Modifier.safeDrawingPadding().imePadding().fillMaxSize().padding(horizontal = 16.dp)) {
            if (editingId == null || !access) Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (editingId == null) "Countdown" else "Select event", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                if (editingId != null) TextButton(enabled = !saving, onClick = { if (externalId != null) finish() else editingId = null }) { Text("Cancel") }
            }
            if (!access) {
                Text("Calendar access is required to select events and update widgets.")
                Spacer(Modifier.height(16.dp))
                val requested = getPreferences(MODE_PRIVATE).getBoolean("requested-calendar", false)
                val settingsNeeded = requested && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
                Button(onClick = {
                    if (settingsNeeded) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    else requestCalendar()
                }) { Text(if (settingsNeeded) "Open permission settings" else "Allow calendar access") }
                return@Column
            }
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (editingId != null) {
                EventPicker(editingId!!, revision, saving, onCancel = { if (externalId != null) finish() else editingId = null }) { event, showDate, showHours ->
                    val id = editingId!!
                    scope.launch {
                        saveError = null
                        saving = true
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                check(WidgetStore.owns(this@MainActivity, id)) { "Widget removed" }
                                val store = WidgetStore(this@MainActivity)
                                if (event == null) store.saveOptions(id, showDate, showHours) else store.save(id, event, showDate, showHours)
                                scheduleRefresh(this@MainActivity)
                                refreshWidgets(this@MainActivity)
                            }
                        }
                        saving = false
                        if (result.isSuccess) {
                            if (externalId != null) {
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                finish()
                            } else { editingId = null; revision++ }
                        } else saveError = "Could not save the widget. Try again."
                    }
                }
            } else {
                var renaming by rememberSaveable { mutableStateOf<Int?>(null) }
                var draftName by rememberSaveable { mutableStateOf("") }
                var nameError by remember { mutableStateOf<String?>(null) }
                var renamingBusy by remember { mutableStateOf(false) }
                if (renaming != null) AlertDialog(
                    onDismissRequest = { if (!renamingBusy) renaming = null },
                    title = { Text("Rename widget") },
                    text = {
                        OutlinedTextField(draftName, onValueChange = { draftName = it.take(40); nameError = null },
                            label = { Text("Widget name") }, singleLine = true, isError = nameError != null,
                            supportingText = { nameError?.let { Text(it) } })
                    },
                    dismissButton = { TextButton(enabled = !renamingBusy, onClick = { renaming = null }) { Text("Cancel") } },
                    confirmButton = { TextButton(enabled = !renamingBusy && draftName.isNotBlank(), onClick = {
                        val id = renaming!!
                        renamingBusy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching {
                                check(WidgetStore.owns(this@MainActivity, id)) { "Widget removed" }
                                WidgetStore(this@MainActivity).rename(id, draftName)
                            } }
                            renamingBusy = false
                            if (result.isSuccess) { renaming = null; revision++ }
                            else nameError = result.exceptionOrNull()?.message ?: "Could not rename widget."
                        }
                    }) { Text("Save") } }
                )
                var widgets by remember { mutableStateOf<List<WidgetSummary>>(emptyList()) }
                LaunchedEffect(revision, access) {
                    widgets = withContext(Dispatchers.IO) {
                        val store = WidgetStore(this@MainActivity)
                        val repository = CalendarRepository(this@MainActivity)
                        WidgetStore.ids(this@MainActivity).map { id ->
                            val selected = store.read(id)
                            val label = if (selected == null) "Select event" else
                                runCatching { repository.resolve(selected)?.let { "${it.title}\n${it.dateLabel()} · ${it.calendarName}" } }
                                    .getOrNull().orEmpty().ifBlank { "Event unavailable · select another event" }
                            WidgetSummary(id, store.name(id), label)
                        }
                    }
                }
                val manager = AppWidgetManager.getInstance(this@MainActivity)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (manager.isRequestPinAppWidgetSupported) Button(onClick = {
                        manager.requestPinAppWidget(ComponentName(this@MainActivity, CountdownWidgetReceiver::class.java), null, null)
                    }) { Text("Add widget") }
                    TextButton(onClick = { revision++ }) { Text("Refresh") }
                }
                Text("Select a widget to edit its settings.",
                    Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
                if (!manager.isRequestPinAppWidgetSupported) Text("Add Countdown from your launcher's Widgets menu.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(widgets, key = { it.id }) { (id, name, label) ->
                        OutlinedCard(onClick = { editingId = id }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary)
                                    TextButton(onClick = { renaming = id; draftName = name; nameError = null }) { Text("Rename") }
                                }
                                Text(label.substringBefore('\n'), style = MaterialTheme.typography.titleMedium)
                                if ('\n' in label) Text(label.substringAfter('\n'),
                                    modifier = Modifier.padding(top = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(if (label == "Select event") "Choose a calendar event" else "Edit widget",
                                    modifier = Modifier.padding(top = 8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ColumnScope.EventPicker(id: Int, refresh: Int, saving: Boolean, onCancel: () -> Unit, onPick: (CalendarEvent?, Boolean, Boolean) -> Unit) {
        val keyboard = LocalSoftwareKeyboardController.current
        var choosingEvent by rememberSaveable(id) { mutableStateOf(false) }
        var draftEvent by rememberSaveable(id) { mutableStateOf<CalendarEvent?>(null) }
        BackHandler(choosingEvent) { keyboard?.hide(); choosingEvent = false }
        var search by rememberSaveable(id) { mutableStateOf("") }
        var calendarId by rememberSaveable(id) { mutableStateOf<Long?>(null) }
        var years by rememberSaveable(id) { mutableIntStateOf(1) }
        val selection = remember(id, refresh) { WidgetStore(this@MainActivity).read(id) }
        val widgetName = remember(id, refresh) { WidgetStore(this@MainActivity).name(id) }
        var showDate by rememberSaveable(id) { mutableStateOf(selection?.showDate ?: true) }
        var showHours by rememberSaveable(id) { mutableStateOf(selection?.showHours ?: false) }
        var currentEvent by remember(id) { mutableStateOf<CalendarEvent?>(null) }
        var currentLoading by remember(id) { mutableStateOf(selection != null) }
        LaunchedEffect(id, refresh) {
            currentLoading = selection != null
            currentEvent = withContext(Dispatchers.IO) {
                runCatching { selection?.let { CalendarRepository(this@MainActivity).resolve(it) } }.getOrNull()
            }
            // A newly picked event may be edited before the widget settings are saved.
            val draft = draftEvent
            if (draft != null) {
                val updated = withContext(Dispatchers.IO) {
                    runCatching { CalendarRepository(this@MainActivity).resolve(
                        WidgetSelection(draft.id, draft.begin, draft.recurring, showDate, showHours)) }.getOrNull()
                }
                if (draftEvent == draft && updated != null) draftEvent = updated
            }
            currentLoading = false
        }
        var calendars by remember { mutableStateOf<List<DeviceCalendar>>(emptyList()) }
        var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        var menu by remember { mutableStateOf(false) }
        var retry by remember { mutableIntStateOf(0) }
        var calendarLaunchFailed by remember { mutableStateOf(false) }
        var eventLaunchFailed by remember { mutableStateOf(false) }
        LaunchedEffect(search, calendarId, years, refresh, retry) {
            loading = true
            error = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val repository = CalendarRepository(this@MainActivity)
                    val today = LocalDate.now()
                    val zone = ZoneId.systemDefault()
                    repository.calendars() to repository.events(
                        today.minusYears((years - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli(),
                        today.plusYears(years.toLong()).atStartOfDay(zone).toInstant().toEpochMilli(), search, calendarId)
                }
            }
            result.onSuccess { (c, e) -> calendars = c; events = e }
                .onFailure { error = "Could not read calendars. Check calendar access and try again."; events = emptyList() }
            loading = false
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = !saving, onClick = {
                keyboard?.hide()
                if (choosingEvent) choosingEvent = false else onCancel()
            }) { Text(if (choosingEvent) "Back" else "Cancel") }
            Text(if (choosingEvent) "Select event" else "Widget settings", Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge)
            if (!choosingEvent) TextButton(
                enabled = !saving && (draftEvent != null || (selection != null &&
                    (showDate != selection.showDate || showHours != selection.showHours))),
                onClick = { keyboard?.hide(); onPick(draftEvent, showDate, showHours) }
            ) { Text(if (saving) "Saving…" else "Save") }
        }
        if (!choosingEvent) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(widgetName, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val shownEvent = draftEvent ?: currentEvent
                val openEvent = {
                    eventLaunchFailed = shownEvent?.let { !openCalendarEvent(it) } ?: false
                }
                OutlinedCard(onClick = {
                    if (shownEvent == null) choosingEvent = true else openEvent()
                }, enabled = !saving && !currentLoading,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Event", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(shownEvent?.title ?: if (currentLoading) "Loading…" else if (selection == null)
                            "Select event" else "Event unavailable", style = MaterialTheme.typography.titleMedium)
                        shownEvent?.let { Text("${it.dateLabel()} · ${it.timeLabel()} · ${it.calendarName}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !saving, onClick = {
                                eventLaunchFailed = false
                                choosingEvent = true
                            }) { Text(if (shownEvent == null) "Select event" else "Change event") }
                            if (shownEvent != null) TextButton(enabled = !saving && !currentLoading,
                                onClick = { openEvent() }) { Text("Open in calendar") }
                        }
                    }
                }
                if (eventLaunchFailed) Text("Could not open this event in a calendar app.",
                    color = MaterialTheme.colorScheme.error)
                Text("Display", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedCard(Modifier.fillMaxWidth()) {
                    OptionRow("Show date", "Shown when the widget has enough space.", showDate, !saving) { showDate = it }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    OptionRow("Show hours", "Include hours in the countdown.", showHours, !saving) { showHours = it }
                }
            }
            return
        }
        OutlinedTextField(search, onValueChange = { search = it }, label = { Text("Search events") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            enabled = !saving,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            trailingIcon = {
                if (search.isNotEmpty()) TextButton(enabled = !saving, onClick = { search = "" }) { Text("Clear") }
            })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(enabled = !saving, onClick = { keyboard?.hide(); menu = true }) {
                    Text(calendars.find { it.id == calendarId }?.name ?: "All calendars",
                        modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(" \u25be")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("All calendars") }, onClick = { calendarId = null; menu = false })
                    calendars.forEach { calendar ->
                        DropdownMenuItem(text = { Text(calendar.name) }, onClick = { calendarId = calendar.id; menu = false })
                    }
                }
            }
            TextButton(enabled = !saving, onClick = {
                keyboard?.hide()
                calendarLaunchFailed = !openCalendar((draftEvent ?: currentEvent)?.date() ?: LocalDate.now())
            }) { Text("Open calendar") }
        }
        if (calendarLaunchFailed) Text("No calendar app available.", color = MaterialTheme.colorScheme.error)
        if (loading || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { retry++ }) { Text("Retry") }
        }
        val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
        LazyColumn(Modifier.weight(1f)) {
            if (!loading && events.isEmpty() && error == null) item {
                Text(if (calendars.isEmpty()) "No device calendars. Enable calendar sync in Android settings."
                    else if (search.isNotBlank()) "No matching events. Try another search or calendar."
                    else "No events in this date range.", Modifier.padding(vertical = 20.dp))
            }
            events.take(2000).groupBy { it.date() }.forEach { (date, datedEvents) ->
                item(key = "date-$date") {
                    Text(date.format(dateFormat), Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge)
                }
                items(datedEvents, key = { "${it.id}-${it.begin}" }) { event ->
                    val isCurrent = draftEvent?.let { it.id == event.id && (!it.recurring || it.begin == event.begin) }
                        ?: (selection?.matches(event) == true)
                    Surface(color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small) {
                    Column(Modifier.fillMaxWidth().semantics { selected = isCurrent }.clickable(enabled = !loading && !saving) { keyboard?.hide(); draftEvent = event; choosingEvent = false }
                        .padding(vertical = 12.dp, horizontal = 4.dp)) {
                        if (isCurrent) Text("Selected", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text(event.title, style = MaterialTheme.typography.titleMedium)
                        Text("${event.timeLabel()} · ${event.calendarName}${if (event.recurring) " · Repeats" else ""}",
                            modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (events.size > 2000) item { Text("Showing the first 2,000 events. Search or choose a calendar to narrow the list.") }
            item {
                TextButton(onClick = { years++ }, enabled = !loading && !saving) {
                    Text("Search earlier and later events")
                }
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, description: String, checked: Boolean, enabled: Boolean,
    onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
