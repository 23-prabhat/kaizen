package com.daytabs.planner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Timing(val label: String) { RANGE("Date range"), DAYS("Number of days") }
private enum class PickDate(val label: String) { START("Start date"), FINISH("Finish date") }

@Composable
internal fun EntryEditor(request: EditRequest, store: PlannerStore, onClose: () -> Unit) {
    val entry = request.entry
    val isGoal = request.key == GOALS_KEY
    val noun = if (isGoal) "goal" else "to-do"
    var title by rememberSaveable { mutableStateOf(entry?.title.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(entry?.note.orEmpty()) }
    var timing by rememberSaveable { mutableStateOf(Timing.RANGE) }
    var duration by rememberSaveable { mutableStateOf("1") }
    var start by remember { mutableStateOf(entry?.schedule?.start ?: request.day) }
    var end by remember { mutableStateOf(entry?.schedule?.finish ?: request.day) }
    val finish = if (timing == Timing.DAYS) finishForDuration(start, duration) else end
    var picking by rememberSaveable { mutableStateOf<PickDate?>(null) }
    var error by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    fun close() {
        if (picking != null) picking = null
        else if (!busy) onClose()
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
        keyboard?.hide()
        focus.clearFocus()
        error = ""
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    store.updateEntries(request.key) { entries ->
                        if (deleting) {
                            entries.filterNot { it.id == entry?.id }
                        } else {
                            val schedule = if (isGoal) Schedule(start, requireNotNull(finish)) else null
                            val cleanNote = if (isGoal) null else note.trim().ifEmpty { null }
                            val cleanTitle = title.trim()
                            if (entry == null) {
                                entries + Entry(title = cleanTitle, note = cleanNote, schedule = schedule)
                            } else {
                                check(entries.any { it.id == entry.id }) { "This item was deleted." }
                                entries.map {
                                    if (it.id == entry.id) it.copy(title = cleanTitle, note = cleanNote, schedule = schedule)
                                    else it
                                }
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
            val pick = picking
            val heading = pick?.label ?: when {
                request.readOnly -> "To-do"
                confirm -> "Delete $noun?"
                entry == null -> "Add $noun"
                else -> "Edit $noun"
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Heading(heading, Modifier.weight(1f))
                IconButton("x", if (pick == null) "Close" else "Close calendar", enabled = !busy, onClick = ::close)
            }
            if (pick != null) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                    Panel(Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp)) {
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
            } else {
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
                        }
                        confirm -> Copy("“${entry?.title ?: title}” will be deleted.")
                        else -> {
                            Field("Title", title, { title = it }, 160, enabled = !busy)
                            if (isGoal) {
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
                                DateField(PickDate.START, start, !busy) { picking = PickDate.START }
                                if (timing == Timing.RANGE) {
                                    DateField(PickDate.FINISH, end, !busy) { picking = PickDate.FINISH }
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
