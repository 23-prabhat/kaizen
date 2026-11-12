package com.daytabs.planner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ColumnScope.Settings(
    state: PlannerState,
    store: PlannerStore,
    reset: Int,
    foreground: Int,
    reminderError: Boolean,
) {
    val palette = colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var lastReset by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(reset) {
        if (lastReset != reset) {
            scroll.scrollTo(0)
            lastReset = reset
        }
    }

    fun change(fallback: String, action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            error = ""
            try {
                action()
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = if (failure is IllegalArgumentException) failure.message ?: fallback else fallback
            } finally {
                busy = false
            }
        }
    }

    val reportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            change("Could not import this report. Try again.") {
                val imported = withContext(Dispatchers.IO) {
                    val content = readDocument(context, uri, MAX_REPORT_BYTES, "Choose a report file under 1 MB.")
                    store.importReport(content.toString(Charsets.UTF_8))
                }
                status = if (imported == 0) "These to-dos are already in your planner."
                else "Imported $imported ${if (imported == 1) "to-do" else "to-dos"}."
            }
        }
    }
    val todoFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            change("Could not read this file. Choose a valid .xlsx or CSV file.") {
                val imported = withContext(Dispatchers.IO) {
                    val content = readDocument(context, uri, MAX_TODO_FILE_BYTES, "Choose an Excel or CSV file under 2 MB.")
                    store.addImportedTodos(parseTodoFile(content, Instant.now(), ZoneId.systemDefault()))
                }
                status = if (imported == 0) "These to-dos are already in your planner."
                    else "Imported $imported ${if (imported == 1) "to-do" else "to-dos"}."
            }
        }
    }

    Heading("Settings")
    Column(Modifier.weight(1f).verticalScroll(scroll).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Copy("Dashboard avatar", size = 18, lineHeight = 22, weight = FontWeight.ExtraBold)
        AvatarSettings(state.avatars, store)
        Copy("Appearance", Modifier.padding(top = 10.dp), size = 18, lineHeight = 22, weight = FontWeight.ExtraBold)
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.selectableGroup()) {
                val icons = listOf("sun", "moon", "smartphone")
                Appearance.entries.forEachIndexed { index, mode ->
                    if (index > 0) Rule()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 51.dp)
                            .selectable(state.appearance == mode, enabled = !busy, role = Role.RadioButton) {
                                change("Could not save your appearance setting. Try again.") {
                                    withContext(Dispatchers.IO) { store.update { it.copy(appearance = mode) } }
                                }
                            }.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(icons[index], 20)
                        Copy(mode.label, Modifier.weight(1f), weight = FontWeight.SemiBold)
                        if (state.appearance == mode) Glyph("check", 20)
                    }
                }
            }
        }
        Copy("Notifications", Modifier.padding(top = 10.dp), size = 18, lineHeight = 22, weight = FontWeight.ExtraBold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderSettings(state.reminders, store, reminderError, foreground)
        }
        Copy("Planner data", Modifier.padding(top = 10.dp), size = 18, lineHeight = 22, weight = FontWeight.ExtraBold)
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Copy("Saved on this device", weight = FontWeight.SemiBold)
                Copy("Plan multiple days from an Excel (.xlsx) or CSV file. Use the columns date, todo work, and deadline. Leave the deadline blank for an all-day to-do.",
                    size = 14, lineHeight = 19, color = palette.secondary)
                Action("Import Excel or CSV", icon = "download", enabled = !busy) {
                    status = ""
                    error = ""
                    todoFilePicker.launch(arrayOf("*/*"))
                }
                Action("Import Kaizen JSON report", enabled = !busy) {
                    status = ""
                    error = ""
                    reportPicker.launch(arrayOf("*/*"))
                }
                Message(status)
            }
        }
        Message(error, error = true)
    }
}

@Composable
private fun AvatarSettings(avatars: Avatars, store: PlannerStore) {
    val palette = colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var usePicked by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }

    fun update(message: String, change: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            feedback = ""
            error = ""
            try {
                change()
                feedback = message
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = if (failure.message == "Choose an image under 20 MB.") failure.message.orEmpty()
                    else "Could not update the avatars. Try again or choose another image."
            } finally {
                busy = false
            }
        }
    }

    fun select(id: String?, message: String) {
        update(message) {
            withContext(Dispatchers.IO) { store.update { it.copy(avatars = it.avatars.copy(fixedId = id)) } }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val fixed = usePicked
            update(if (fixed) "Using your image." else "Image added.") {
                withContext(Dispatchers.IO) {
                    val photo = readAvatar(context, uri)
                    store.update {
                        val existing = it.avatars
                        if (existing.photos.size >= MAX_AVATARS) it else it.copy(avatars = Avatars(
                            existing.photos + photo, if (fixed) photo.id else existing.fixedId
                        ))
                    }
                }
            }
        }
    }

    fun addImage(useImage: Boolean) {
        if (avatars.photos.size >= MAX_AVATARS) return
        usePicked = useImage
        photoPicker.launch("image/*")
    }

    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            val fixed = avatars.fixedId != null
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AvatarMode("Rotate avatars", !fixed, !busy, Modifier.weight(1f)) {
                    if (fixed) select(null, "Avatar rotation on.")
                }
                AvatarMode("Use one image", fixed, !busy, Modifier.weight(1f)) {
                    if (!fixed) {
                        val first = avatars.photos.firstOrNull()
                        if (first == null) addImage(true) else select(first.id, "Using one image.")
                    }
                }
            }
            Copy(if (fixed) "Your selected image stays on the dashboard."
                else "The four mascots and your images take turns when you open the app.",
                size = 14, lineHeight = 19, color = palette.secondary)
            if (!fixed) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    mascots.forEachIndexed { index, (name, _) ->
                        Box(Modifier.size(48.dp).background(palette.cover, RoundedCornerShape(7.dp)).padding(3.dp)) {
                            AvatarImage(null, Modifier.fillMaxSize(), index, name)
                        }
                    }
                }
            }
            if (avatars.photos.isNotEmpty()) {
                Column {
                    Copy("Your images", Modifier.padding(bottom = 4.dp), size = 14, weight = FontWeight.Bold)
                    avatars.photos.forEachIndexed { index, photo ->
                        val number = index + 1
                        Rule()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).heightIn(min = 48.dp)
                                .clickable(enabled = !busy, role = Role.Button) { select(photo.id, "Using image $number.") }
                                .semantics { contentDescription = "Use image $number"; selected = avatars.fixedId == photo.id }
                                .padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                AvatarImage(photo.uri, modifier = Modifier.size(38.dp))
                                Copy("Image $number", Modifier.weight(1f), size = 14)
                                if (avatars.fixedId == photo.id) Glyph("check", 18)
                            }
                            IconButton("x", "Remove image $number", enabled = !busy) {
                                update("Image removed.") {
                                    withContext(Dispatchers.IO) {
                                        store.update {
                                            val current = it.avatars
                                            it.copy(avatars = current.copy(
                                                photos = current.photos.filterNot { image -> image.id == photo.id },
                                                fixedId = current.fixedId.takeUnless { id -> id == photo.id },
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Action("Add image", icon = "image", enabled = !busy && avatars.photos.size < MAX_AVATARS) { addImage(false) }
            if (avatars.photos.size == MAX_AVATARS) {
                Copy("Remove an image to add another.", size = 14, lineHeight = 19, color = palette.secondary)
            }
            Message(feedback)
            Message(error, error = true)
        }
    }
}

@Composable
private fun AvatarMode(label: String, active: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = colors
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier.heightIn(min = 44.dp).clip(shape).background(if (active) actionYellow else palette.surface)
            .border(2.dp, if (active) actionInk else palette.ink, shape)
            .selectable(active, enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Copy(label, size = 14, lineHeight = 19, color = if (active) actionInk else palette.ink,
            weight = FontWeight.Bold, align = TextAlign.Center)
    }
}
