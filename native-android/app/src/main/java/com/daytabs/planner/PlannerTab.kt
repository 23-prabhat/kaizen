package com.daytabs.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ColumnScope.PlannerTab(
    tab: Tab,
    state: PlannerState,
    today: LocalDate,
    store: PlannerStore,
    onEdit: (EditRequest) -> Unit,
) {
    val history = tab == Tab.HISTORY
    val goals = tab == Tab.GOALS
    val yesterday = today.minusDays(1)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    val day = if (history) parseDay(selected)?.coerceAtMost(yesterday) ?: yesterday else today
    val recent = remember(state.records, today) {
        if (history) state.activity(today).filter { it.day < today && it.done + it.remaining > 0 }.asReversed()
        else emptyList()
    }
    val entries = if (goals) state.goals else state.todos(day)
    val ordered = remember(entries, today, goals) {
        if (goals) entries.sortedWith(compareBy<Entry> { it.schedule?.phase(today) }.thenBy { it.completed })
        else entries.sortedBy { it.completed }
    }
    val scope = rememberCoroutineScope()
    val key = if (goals) GOALS_KEY else todoKey(day)
    var error by remember(key) { mutableStateOf("") }

    fun select(next: LocalDate) {
        selected = if (next < yesterday) next.toString() else null
        calendarOpen = false
    }

    Heading(tab.label)
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)) {
        if (!goals || error.isNotEmpty()) {
            item("date-strip") {
                Column(Modifier.padding(bottom = 11.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    if (history) {
                        DateStrip(day, onPick = { calendarOpen = true },
                            onPrevious = if (day > firstCalendarDay) ({ select(day.minusDays(1)) }) else null,
                            onNext = { select(day.plusDays(1)) }, nextEnabled = day < yesterday)
                        if (recent.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Copy("Recent days with to-dos", size = 13, lineHeight = 18,
                                    color = colors.secondary, weight = FontWeight.Bold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    recent.forEach { item -> RecentDay(item.day, item.day == day) { select(item.day) } }
                                }
                            }
                        }
                    } else if (!goals) {
                        DateStrip(day, "${entries.count { it.completed }} of ${entries.size} done")
                    }
                    Message(error, error = true)
                }
            }
        }
        if (ordered.isEmpty()) {
            item("empty") {
                val text = when {
                    goals -> "No goals yet"
                    history -> "No to-dos for this day"
                    else -> "No to-dos for today"
                }
                Panel(Modifier.fillMaxWidth().heightIn(min = 58.dp), radius = 9.dp) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart) {
                        Copy(text, color = colors.secondary)
                    }
                }
            }
        }
        itemsIndexed(ordered, key = { _, item -> item.id }) { index, entry ->
            EntryRow(entry, today, index == 0, index == ordered.lastIndex, history,
                onOpen = { onEdit(EditRequest(key, day, entry, history)) },
                onToggle = {
                    scope.launch {
                        error = ""
                        try {
                            withContext(Dispatchers.IO) {
                                store.updateEntries(key) { all ->
                                    all.map {
                                        if (it.id == entry.id) it.copy(completedAt = if (it.completed) null else Instant.now().toString())
                                        else it
                                    }
                                }
                            }
                        } catch (failure: Exception) {
                            if (failure is kotlinx.coroutines.CancellationException) throw failure
                            error = "Could not update this item. Tap its checkbox to try again."
                        }
                    }
                })
        }
    }
    if (!history) {
        val noun = if (goals) "goal" else "to-do"
        Action("Add $noun", Modifier.padding(top = 8.dp, bottom = 17.dp), icon = "plus", primary = true) {
            onEdit(EditRequest(key, day))
        }
    }
    if (calendarOpen && history) {
        KaizenDialog("Choose a day", { calendarOpen = false }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Copy("Choose a day", Modifier.weight(1f), size = 21, weight = FontWeight.ExtraBold)
                IconButton("x", "Close calendar") { calendarOpen = false }
            }
            CalendarGrid(day, lastDay = yesterday, onSelect = ::select)
        }
    }
}

@Composable
private fun DateStrip(
    day: LocalDate,
    detail: String? = null,
    onPick: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    nextEnabled: Boolean = true,
) {
    Panel(Modifier.fillMaxWidth(), background = colors.date) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onPrevious != null) IconButton("chevron-left", "Previous day", onClick = onPrevious)
            val label = dayLabel(day)
            if (onPick != null) {
                Row(
                    Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onPick)
                        .semantics { contentDescription = "Choose date, $label" },
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Copy(label, size = 17, weight = FontWeight.ExtraBold, align = TextAlign.Center)
                    Glyph("calendar", 17)
                }
            } else {
                Copy(label, Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp),
                    size = 17, weight = FontWeight.ExtraBold)
            }
            if (detail != null) Copy(detail, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), weight = FontWeight.Bold)
            if (onNext != null) IconButton("chevron-right", "Next day", enabled = nextEnabled, onClick = onNext)
        }
    }
}

@Composable
private fun RecentDay(day: LocalDate, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.widthIn(min = 48.dp).heightIn(min = 44.dp).clip(shape)
            .background(if (active) actionYellow else colors.surface)
            .border(1.5.dp, if (active) actionInk else colors.ink, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Show ${dayLabel(day)}"; selected = active }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) { Copy(dateLabel(day), size = 13, lineHeight = 18, weight = FontWeight.Bold, color = if (active) actionInk else colors.ink) }
}

@Composable
private fun EntryRow(
    entry: Entry,
    today: LocalDate,
    first: Boolean,
    last: Boolean,
    readOnly: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val palette = colors
    val top = if (first) 7.dp else 0.dp
    val bottom = if (last) 7.dp else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    val subtitle = entry.schedule?.let { "${it.phase(today).label} · ${it.label()}" }
    val innerTop = if (first) 5.5.dp else 0.dp
    val innerBottom = if (last) 5.5.dp else 0.dp
    val innerShape = RoundedCornerShape(
        topStart = innerTop, topEnd = innerTop, bottomStart = innerBottom, bottomEnd = innerBottom,
    )
    Column(Modifier.fillMaxWidth().clip(shape).background(palette.ink).padding(bottom = if (last) 1.5.dp else 0.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 1.5.dp, end = 1.5.dp, top = if (first) 1.5.dp else 0.dp)
            .background(palette.surface, innerShape), verticalAlignment = Alignment.CenterVertically) {
            val check = if (readOnly) Modifier else Modifier.toggleable(entry.completed, role = Role.Checkbox) { onToggle() }
                .semantics { contentDescription = entry.title }
            Box(Modifier.width(51.dp).heightIn(min = 51.dp).then(check), contentAlignment = Alignment.Center) {
                val checkbox = RoundedCornerShape(4.dp)
                Box(Modifier.size(22.dp).background(if (entry.completed) palette.ink else palette.surface, checkbox)
                    .border(1.6.dp, palette.ink, checkbox), contentAlignment = Alignment.Center) {
                    if (entry.completed) Glyph("check", 15, palette.surface)
                }
            }
            val description = if (readOnly) "${entry.title}, ${if (entry.completed) "completed" else "unfinished"}" else "Edit ${entry.title}"
            Column(Modifier.weight(1f).heightIn(min = 51.dp)
                .clickable(role = Role.Button, onClick = onOpen).semantics { contentDescription = description }
                .padding(top = 14.dp, bottom = 14.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                Copy(entry.title, lineHeight = 20, weight = FontWeight.Bold,
                    color = if (entry.completed) palette.secondary else palette.ink)
                (subtitle ?: entry.note)?.let {
                    Copy(it, size = 14, lineHeight = 18, color = palette.secondary,
                        maxLines = if (subtitle == null) 1 else Int.MAX_VALUE)
                }
            }
        }
        if (!last) Rule(Modifier.padding(horizontal = 1.5.dp))
    }
}
