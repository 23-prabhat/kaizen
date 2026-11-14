package com.daytabs.planner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GoalProgressScreen(goal: Entry, today: LocalDate, store: PlannerStore, onBack: () -> Unit, onEditGoal: () -> Unit) {
    val notes = remember(goal.progress, today) {
        goal.progress.sortedWith(compareByDescending<ProgressNote> { it.day == today }.thenByDescending { it.day })
    }
    val listState = rememberLazyListState()
    var editingDay by rememberSaveable { mutableStateOf<String?>(null) }
    val day = parseDay(editingDay)

    Column(Modifier.fillMaxSize().background(colors.background)
        .windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 16.dp)) {
            if (day != null) {
                val saved = goal.progress.firstOrNull { it.day == day }
                ProgressNoteEditor(day, saved, onBack = { editingDay = null }) { text ->
                    withContext(Dispatchers.IO) { store.saveGoalProgressNote(goal.id, ProgressNote(day, text)) }
                    editingDay = null
                    if (saved == null) listState.requestScrollToItem(0)
                }
            } else {
                ProgressToolbar("Progress notes", "Back", onBack, onEditGoal)
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item("goal") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Copy(goal.title, Modifier.semantics { heading() }, size = 28, lineHeight = 34, display = true)
                            val details = listOfNotNull(goal.goalTag?.label,
                                if (goal.completed) "Completed" else goal.schedule?.phase(today)?.label,
                                goal.schedule?.label())
                            if (details.isNotEmpty()) Copy(details.joinToString(" · "), size = 14, color = colors.secondary)
                        }
                    }
                    if (notes.none { it.day == today }) {
                        item("add") {
                            Action("Add today’s note", icon = "plus", primary = true) { editingDay = today.toString() }
                        }
                    }
                    if (notes.isEmpty()) {
                        item("empty") {
                            Copy("No progress notes yet.", color = colors.secondary)
                        }
                    }
                    items(notes, key = { it.day.toString() }) { note ->
                        ProgressNoteRow(note, note.day == today, onEdit = { editingDay = note.day.toString() })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressToolbar(title: String, backLabel: String, onBack: () -> Unit, onEditGoal: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton("chevron-left", backLabel, onClick = onBack)
        Copy(title, Modifier.weight(1f).semantics { heading() }, size = 24, lineHeight = 30, display = true)
        if (onEditGoal != null) IconButton("settings", "Edit goal", onClick = onEditGoal)
    }
    Rule()
}

@Composable
private fun ProgressNoteRow(note: ProgressNote, isToday: Boolean, onEdit: () -> Unit) {
    val date = accessibleDayLabel(note.day)
    Column {
        Row(Modifier.fillMaxWidth().background(if (isToday) colors.date else colors.background)
            .padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Copy(if (isToday) "Today · $date" else date, Modifier.weight(1f).semantics { heading() },
                size = 15, lineHeight = 20, weight = FontWeight.Bold)
            IconButton("edit-2", "Edit note for $date", onClick = onEdit)
        }
        Rule()
        Copy(note.text, Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
    }
}

@Composable
private fun ProgressNoteEditor(day: LocalDate, note: ProgressNote?, onBack: () -> Unit, onSave: suspend (String) -> Unit) {
    var text by rememberSaveable(day.toString()) { mutableStateOf(note?.text.orEmpty()) }
    var error by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    fun close() {
        if (saving) return
        keyboard?.hide()
        focus.clearFocus()
        onBack()
    }

    fun save() {
        if (saving) return
        val clean = text.trim()
        if (clean.isEmpty()) {
            error = "Enter a progress note."
            return
        }
        saving = true
        error = ""
        keyboard?.hide()
        focus.clearFocus()
        scope.launch {
            try {
                onSave(clean)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = "Could not save your note. Try again."
            } finally {
                saving = false
            }
        }
    }

    BackHandler { close() }
    ProgressToolbar(if (note == null) "Add progress note" else "Edit progress note", "Back to progress notes", ::close)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Copy(accessibleDayLabel(day), size = 15, color = colors.secondary)
        Field("Progress note", text, {
            text = it
            error = ""
        }, maxOf(4000, note?.text?.length ?: 0),
            multiline = true, enabled = !saving)
        Message(error, error = true)
        Action(if (saving) "Saving…" else "Save note", primary = true, enabled = !saving, onClick = ::save)
    }
}
