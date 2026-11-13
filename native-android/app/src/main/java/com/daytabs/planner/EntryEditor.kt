package com.daytabs.planner

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Timing(val label: String) { RANGE("Date range"), DAYS("Number of days") }
private enum class PickDate(val label: String) { START("Start date"), FINISH("Finish date") }

@Composable
internal fun EntryEditor(request: EditRequest, store: PlannerStore, onClose: () -> Unit, onStartGoal: (String) -> Unit) {
    val entry = request.entry
    val isGoal = request.key == GOALS_KEY
    val noun = if (isGoal) "goal" else "to-do"
    var title by rememberSaveable { mutableStateOf(entry?.title ?: request.draftTitle) }
    var note by rememberSaveable { mutableStateOf(entry?.note.orEmpty()) }
    val progressDay = remember { LocalDate.now() }
    val progressNote = entry?.progress?.firstOrNull { it.day == progressDay }
    var progressOpen by rememberSaveable { mutableStateOf(false) }
    var progressText by rememberSaveable { mutableStateOf(progressNote?.text.orEmpty()) }
    var deadline by rememberSaveable { mutableStateOf(entry?.deadline) }
    var hours by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    var goalTag by rememberSaveable { mutableStateOf(entry?.goalTag) }
    var timing by rememberSaveable { mutableStateOf(Timing.RANGE) }
    var duration by rememberSaveable { mutableStateOf("1") }
    var start by remember { mutableStateOf(entry?.schedule?.start ?: request.day) }
    var end by remember { mutableStateOf(entry?.schedule?.finish ?: request.day) }
    val finish = if (timing == Timing.DAYS) finishForDuration(start, duration) else end
    var picking by rememberSaveable { mutableStateOf<PickDate?>(null) }
    var error by rememberSaveable { mutableStateOf("") }
    var deadlineError by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    fun close() {
        if (!busy) onClose()
    }

    fun openCalendar(date: PickDate) {
        keyboard?.hide()
        focus.clearFocus()
        picking = date
    }

    fun submit(deleting: Boolean = false) {
        if (busy) return
        if (!deleting && title.isBlank()) {
            error = "Enter a title."
            return
        }
        if (!deleting && isGoal && finish == null) {
            error = "Enter a positive whole number of days that fits on the calendar."
            return
        }
        val now = Instant.now()
        val window = DeadlineWindow(request.day, now, ZoneId.systemDefault())
        val hasDuration = hours.isNotEmpty() || minutes.isNotEmpty()
        val dueAt = try {
            when {
                deleting || isGoal -> null
                hasDuration -> window.afterMinutes(deadlineDuration(
                    if (hours.isEmpty()) 0 else hours.toIntOrNull(),
                    if (minutes.isEmpty()) 0 else minutes.toIntOrNull(),
                )).also {
                    require(it > now) { "Choose a deadline on a current or future date." }
                }.toEpochMilli()
                else -> deadline?.also { selected ->
                    if (selected != entry?.deadline) {
                        val instant = Instant.ofEpochMilli(selected)
                        require(window.contains(instant)) { TODO_DAY_LIMIT }
                        require(instant > now) { "Choose a time later today or plan this to-do on another date." }
                    }
                }
            }
        } catch (failure: IllegalArgumentException) {
            keyboard?.hide()
            focus.clearFocus()
            error = ""
            deadlineError = failure.message.orEmpty()
            return
        }
        val cleanTitle = title.trim()
        val cleanNote = if (isGoal) entry?.note else note.trim().ifEmpty { null }
        val schedule = if (isGoal && !deleting) Schedule(start, requireNotNull(finish)) else null
        val progressEdit = if (isGoal && progressOpen) progressText.trim() else null
        val deadlineStart = when {
            dueAt == null -> null
            !hasDuration && dueAt == entry?.deadline -> entry.deadlineStart
            else -> window.startFor(Instant.ofEpochMilli(dueAt))
        }

        keyboard?.hide()
        focus.clearFocus()
        error = ""
        deadlineError = ""
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    store.updateEntries(request.key) { entries ->
                        if (deleting) {
                            entries.filterNot { it.id == entry?.id }
                        } else {
                            fun edit(current: Entry): Entry {
                                val progress = if (progressEdit == null) current.progress else {
                                    current.progress.filterNot { it.day == progressDay } +
                                        listOfNotNull(progressEdit.takeIf { it.isNotEmpty() }?.let {
                                            ProgressNote(progressDay, it)
                                        })
                                }
                                return current.copy(
                                    title = cleanTitle, note = cleanNote, schedule = schedule,
                                    deadline = dueAt, deadlineStart = deadlineStart, progress = progress, goalTag = goalTag,
                                )
                            }
                            if (entry == null) {
                                entries + edit(Entry(title = cleanTitle))
                            } else {
                                check(entries.any { it.id == entry.id }) { "This item was deleted." }
                                entries.map { if (it.id == entry.id) edit(it) else it }
                            }
                        }
                    }
                }
                onClose()
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = when {
                    failure.message == "This item was deleted." -> "This item was deleted."
                    deleting -> "Could not delete this item. Try again."
                    else -> "Could not save your changes. Try again."
                }
            } finally {
                busy = false
            }
        }
    }

    BackHandler { close() }
    Box(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
        contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 16.dp)) {
            val heading = when {
                request.readOnly -> "To-do"
                confirm -> "Delete $noun?"
                entry == null -> "Add $noun"
                else -> "Edit $noun"
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Heading(heading, Modifier.weight(1f))
                IconButton("x", "Close", enabled = !busy, onClick = ::close)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (!isGoal) EditorDate(dayLabel(request.day))
                when {
                    request.readOnly -> {
                        Copy(title, size = 23, lineHeight = 29, weight = FontWeight.Bold)
                        Copy(if (entry?.completed == true) "Completed" else "Unfinished", color = colors.secondary)
                        if (note.isNotEmpty()) Copy(note)
                        entry?.deadline?.let { EditorDate("Due ${deadlineLabel(LocalContext.current, it)}") }
                    }
                    confirm -> Copy("“${entry?.title ?: title}” will be deleted.")
                    else -> {
                        Field("Title", title, { title = it }, 160, enabled = !busy)
                        if (isGoal) {
                            GoalTagPicker(goalTag, !busy) { goalTag = it }
                            GoalProgress(entry?.progress.orEmpty(), progressDay, progressOpen, progressText,
                                { progressOpen = true }, { progressText = it }, !busy)
                            Panel(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().selectableGroup()) {
                                    Timing.entries.forEach { option ->
                                        val active = timing == option
                                        Box(Modifier.weight(1f).heightIn(min = 44.dp)
                                            .background(if (active) colors.date else colors.surface)
                                            .selectable(active, enabled = !busy, role = Role.RadioButton) {
                                                if (option != timing) {
                                                    if (option == Timing.DAYS) duration = calendarDays(start, end).toString()
                                                    else end = finish ?: end
                                                    timing = option
                                                    error = ""
                                                }
                                            }.padding(7.dp), contentAlignment = Alignment.Center) {
                                            Copy(option.label, weight = FontWeight.Bold, align = TextAlign.Center)
                                        }
                                    }
                                }
                            }
                            DateField(PickDate.START, start, !busy) { openCalendar(PickDate.START) }
                            if (timing == Timing.RANGE) {
                                DateField(PickDate.FINISH, end, !busy) { openCalendar(PickDate.FINISH) }
                            } else {
                                Field("Number of days", duration, { duration = it }, 7, numeric = true, enabled = !busy)
                            }
                            if (finish != null) {
                                val days = calendarDays(start, finish)
                                EditorDate(if (timing == Timing.DAYS) "Finishes ${fullDayLabel(finish)}"
                                    else "$days ${if (days == 1L) "day" else "days"}")
                            }
                        } else {
                            Field("Note (optional)", note, { note = it }, 4000, multiline = true, enabled = !busy)
                            TodoDeadline(
                                request.day, deadline, hours, minutes, deadlineError, !busy,
                                onHours = {
                                    hours = it
                                    deadlineError = ""
                                },
                                onMinutes = {
                                    minutes = it
                                    deadlineError = ""
                                },
                                onClock = {
                                    deadline = it
                                    hours = ""
                                    minutes = ""
                                    deadlineError = ""
                                },
                                onStartGoal = { onStartGoal(title.trim()) },
                            )
                        }
                        if (entry != null) Action("Delete $noun", icon = "trash-2", danger = true, enabled = !busy) {
                            keyboard?.hide()
                            focus.clearFocus()
                            error = ""
                            confirm = true
                        }
                    }
                }
                Message(error, error = true)
            }
            if (!request.readOnly) {
                Column(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (confirm) {
                        Action("Cancel", enabled = !busy) { error = ""; confirm = false }
                        Action(if (busy) "Deleting…" else "Delete $noun", danger = true, enabled = !busy) { submit(true) }
                    } else {
                        Action(if (busy) "Saving…" else "Save $noun", primary = true, enabled = !busy) { submit() }
                    }
                }
            }
        }
    }
    picking?.let { pick ->
        KaizenDialog(pick.label, { picking = null }) {
            DialogTitle(pick.label, "Close calendar", 26) { picking = null }
            CalendarGrid(
                selected = if (pick == PickDate.START) start else end,
                firstDay = if (pick == PickDate.START) firstCalendarDay else start,
            ) { day ->
                if (pick == PickDate.START) {
                    start = day
                    if (day > end) end = day
                } else {
                    end = day
                }
                error = ""
                picking = null
            }
        }
    }
}

@Composable
private fun DateField(pick: PickDate, day: LocalDate, enabled: Boolean, onClick: () -> Unit) {
    val label = fullDayLabel(day)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Copy(pick.label, weight = FontWeight.Bold)
        Action(label, icon = "calendar", enabled = enabled,
            accessibilityLabel = "Choose ${pick.label.lowercase()}, $label", onClick = onClick)
    }
}

@Composable
private fun EditorDate(text: String) {
    Panel(Modifier.fillMaxWidth(), background = colors.date) {
        Copy(text, Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

@Composable
internal fun CalendarGrid(
    selected: LocalDate,
    firstDay: LocalDate = firstCalendarDay,
    lastDay: LocalDate = lastCalendarDay,
    onSelect: (LocalDate) -> Unit,
) {
    var monthKey by rememberSaveable(selected.toString()) { mutableStateOf(selected.withDayOfMonth(1).toString()) }
    val month = LocalDate.parse(monthKey)
    val palette = colors
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton("chevron-left", "Previous month", enabled = month > firstDay.withDayOfMonth(1)) {
                monthKey = month.minusMonths(1).toString()
            }
            Copy(monthLabel(month), Modifier.weight(1f), weight = FontWeight.Bold, align = TextAlign.Center)
            IconButton("chevron-right", "Next month", enabled = month < lastDay.withDayOfMonth(1)) {
                monthKey = month.plusMonths(1).toString()
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (day in listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")) {
                Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                    Copy(day, size = 12, color = palette.secondary)
                }
            }
        }
        val offset = month.dayOfWeek.value - 1
        val weeks = (offset + month.lengthOfMonth() + 6) / 7
        for (week in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (weekday in 0..6) {
                    val index = week * 7 + weekday - offset + 1
                    val cell = Modifier.weight(1f).heightIn(min = 48.dp)
                    if (index !in 1..month.lengthOfMonth()) {
                        Box(cell)
                    } else {
                        val day = month.withDayOfMonth(index)
                        val active = day == selected
                        val enabled = day in firstDay..lastDay
                        val shape = RoundedCornerShape(6.dp)
                        Box(cell.alpha(if (enabled) 1f else 0.38f)
                            .background(if (active) palette.date else androidx.compose.ui.graphics.Color.Transparent, shape)
                            .border(1.dp, if (active) palette.ink else androidx.compose.ui.graphics.Color.Transparent, shape)
                            .selectable(active, enabled = enabled, role = Role.Button) { onSelect(day) }
                            .semantics { contentDescription = accessibleDayLabel(day) },
                            contentAlignment = Alignment.Center) {
                            Copy(index.toString())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalProgress(
    notes: List<ProgressNote>,
    today: LocalDate,
    editing: Boolean,
    text: String,
    onEdit: () -> Unit,
    onChange: (String) -> Unit,
    enabled: Boolean,
) {
    val current = notes.firstOrNull { it.day == today }
    val previous = remember(notes, today) { notes.filterNot { it.day == today }.sortedByDescending { it.day } }
    var showPrevious by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Copy("Daily progress notes", size = 18, weight = FontWeight.Bold)
        if (editing) {
            Copy(dayLabel(today), size = 14, color = colors.secondary)
            Field("Today’s progress note", text, onChange, 4000, multiline = true, enabled = enabled)
        } else {
            current?.let {
                Copy(it.text, color = colors.secondary, maxLines = 3)
            }
            Action(
                if (current == null) "Add today’s progress note" else "Edit today’s progress note",
                icon = if (current == null) "plus" else "edit-2",
                enabled = enabled,
                onClick = onEdit,
            )
        }
        if (previous.isNotEmpty()) {
            Action(if (showPrevious) "Hide past notes" else "Show past notes (${previous.size})") {
                showPrevious = !showPrevious
            }
            if (showPrevious) {
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        previous.forEachIndexed { index, note ->
                            if (index > 0) Rule()
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Copy(fullDayLabel(note.day), size = 14, weight = FontWeight.Bold)
                                Copy(note.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalTagPicker(selected: GoalTag?, enabled: Boolean, onSelect: (GoalTag?) -> Unit) {
    val palette = colors
    val shape = RoundedCornerShape(6.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Copy("Goal tag", weight = FontWeight.Bold)
        FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (tag in listOf(null) + GoalTag.entries) {
                val active = selected == tag
                Box(Modifier.heightIn(min = 48.dp).clip(shape)
                    .background(if (active) palette.date else palette.surface)
                    .border(if (active) 2.dp else 1.5.dp, palette.ink, shape)
                    .selectable(active, enabled = enabled, role = Role.RadioButton) { onSelect(tag) }
                    .padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Copy(tag?.label ?: "No tag", size = 14, weight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun TodoDeadline(
    plannedDay: LocalDate,
    deadline: Long?,
    hours: String,
    minutes: String,
    error: String,
    enabled: Boolean,
    onHours: (String) -> Unit,
    onMinutes: (String) -> Unit,
    onClock: (Long?) -> Unit,
    onStartGoal: () -> Unit,
) {
    val context = LocalContext.current
    val dark = colors == darkPalette
    val isToday = plannedDay == LocalDate.now()
    val hasDuration = hours.isNotEmpty() || minutes.isNotEmpty()
    val recovery = remember { BringIntoViewRequester() }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Copy("Deadline (optional)", weight = FontWeight.Bold)
        if (deadline != null && !hasDuration) EditorDate("Due ${deadlineLabel(context, deadline)}")
        if (expanded) {
            Copy(if (isToday) "Count from when you save." else "Count from midnight on ${dayLabel(plannedDay)}.",
                size = 14, lineHeight = 19, color = colors.secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Hours (0–23)", hours, onHours, 10, numeric = true, enabled = enabled, modifier = Modifier.weight(1f))
                Field("Minutes (0–59)", minutes, onMinutes, 10, numeric = true, enabled = enabled, modifier = Modifier.weight(1f))
            }
            if (error.isNotEmpty()) {
                Column(Modifier.bringIntoViewRequester(recovery), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Message(error, error = true)
                    if (error == TODO_DURATION_LIMIT || error == TODO_DAY_LIMIT) {
                        Action("Add a goal", icon = "flag", enabled = enabled, onClick = onStartGoal)
                    }
                }
                LaunchedEffect(error) { recovery.bringIntoView() }
            }
            Action("Choose a clock time", icon = "clock", enabled = enabled) {
                val zone = ZoneId.systemDefault()
                val initial = deadline?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
                    ?: if (isToday) LocalTime.now(zone).plusHours(1) else LocalTime.of(17, 0)
                val theme = if (dark) android.R.style.Theme_Material_Dialog_Alert
                    else android.R.style.Theme_Material_Light_Dialog_Alert
                TimePickerDialog(context, theme, { _, hour, minute ->
                    val time = LocalTime.of(hour, minute)
                    onClock(plannedDay.atTime(time).atZone(zone).toInstant().toEpochMilli())
                }, initial.hour, initial.minute, DateFormat.is24HourFormat(context)).show()
            }
            if (deadline != null || hasDuration) {
                Action("Remove deadline", enabled = enabled) { onClock(null) }
            }
        } else {
            Action(if (deadline == null) "Add deadline" else "Change deadline",
                icon = "clock", enabled = enabled) { expanded = true }
        }
    }
}

internal fun deadlineLabel(context: Context, deadline: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(deadline).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val label = when (day) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> fullDayLabel(day)
    }
    return "$label at ${DateFormat.getTimeFormat(context).format(Date(deadline))}"
}
