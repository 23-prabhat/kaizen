package com.daytabs.planner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NotificationDialog(
    state: PlannerState,
    today: LocalDate,
    store: PlannerStore,
    syncError: Boolean,
    foreground: Int,
    onClose: () -> Unit,
    onOpen: (Tab) -> Unit,
) {
    val palette = colors
    val pending = state.todos(today).count { !it.completed }
    val deadlines = goalMessage(state.goals, today)
    var preferencesOpen by rememberSaveable { mutableStateOf(false) }
    KaizenDialog("Notifications", onClose) {
        DialogTitle("Notifications", "Close notifications", onClose = onClose)
        if (pending > 0) Notice("Unfinished to-dos", todoMessage(pending), "check-square", palette.remaining) { onOpen(Tab.TODOS) }
        if (deadlines.isNotEmpty()) Notice("Goal deadlines", deadlines, "flag", palette.goal) { onOpen(Tab.GOALS) }
        if (pending == 0 && deadlines.isEmpty()) Copy("You’re all caught up.", Modifier.padding(bottom = 8.dp), color = palette.secondary)
        Action(if (preferencesOpen) "Hide reminder preferences" else "Manage reminders") { preferencesOpen = !preferencesOpen }
        if (preferencesOpen) ReminderSettings(state.reminders, store, syncError, foreground)
    }
}

@Composable
private fun Notice(title: String, body: String, icon: String, tone: Color, onClick: () -> Unit) {
    val palette = colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(palette.surface)
            .border(2.dp, palette.ink, shape).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$title. $body" }.padding(9.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        val badge = RoundedCornerShape(6.dp)
        Box(Modifier.size(34.dp).background(tone, badge).border(1.5.dp, actionInk, badge), contentAlignment = Alignment.Center) {
            Glyph(icon, 18, actionInk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Copy(title, weight = FontWeight.Bold)
            Copy(body, size = 14, lineHeight = 19, color = palette.secondary)
        }
        Glyph("chevron-right", 18)
    }
}

@Composable
private fun ReminderSettings(
    preferences: ReminderPreferences,
    store: PlannerStore,
    syncError: Boolean,
    foreground: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissionPrefs = remember { context.getSharedPreferences("kaizen_permissions", Activity.MODE_PRIVATE) }
    var requested by remember { mutableStateOf(permissionPrefs.getBoolean("notifications", false)) }
    var permissionGranted by remember(foreground) { mutableStateOf(DeviceReminders.canNotify(context)) }
    var pendingKind by rememberSaveable { mutableStateOf<ReminderKind?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }

    fun save(kind: ReminderKind, value: Boolean) {
        scope.launch {
            busy = true
            error = ""
            try {
                withContext(Dispatchers.IO) {
                    store.update {
                        val prefs = when (kind) {
                            ReminderKind.TODOS -> it.reminders.copy(todos = value)
                            ReminderKind.GOALS -> it.reminders.copy(goals = value)
                        }
                        it.copy(reminders = prefs)
                    }
                }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = "Could not update reminders. Try again."
            } finally {
                busy = false
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kind = pendingKind
        pendingKind = null
        permissionGranted = granted && DeviceReminders.canNotify(context)
        if (permissionGranted && kind != null) save(kind, true)
        else if (!permissionGranted) error = "Allow notifications in your device settings to turn on reminders."
    }

    val activity = LocalActivity.current
    val requestable = Build.VERSION.SDK_INT >= 33 && (!requested ||
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true)
    fun change(kind: ReminderKind, enabled: Boolean) {
        error = ""
        when {
            !enabled -> save(kind, false)
            DeviceReminders.canNotify(context) -> save(kind, true)
            Build.VERSION.SDK_INT >= 33 && requestable -> {
                DeviceReminders.ensureChannel(context)
                pendingKind = kind
                requested = true
                permissionPrefs.edit().putBoolean("notifications", true).apply()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> error = "Allow notifications in your device settings to turn on reminders."
        }
    }

    Panel(Modifier.fillMaxWidth()) {
        Column {
            ReminderToggle("Pending to-dos", "7 PM when today’s list has unfinished items.", preferences.todos, !busy && pendingKind == null) {
                change(ReminderKind.TODOS, it)
            }
            Rule()
            ReminderToggle("Goal deadlines", "9 AM the day before and on the finish date.", preferences.goals, !busy && pendingKind == null) {
                change(ReminderKind.GOALS, it)
            }
        }
    }
    if (!permissionGranted && !requestable) {
        Copy("Allow notifications in your device settings to receive reminders.", size = 14, lineHeight = 19, color = colors.secondary)
        Action("Open device settings", icon = "external-link") {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        }
    }
    if (syncError) Message("Could not schedule reminders. Reopen the app to try again.", error = true)
    Message(error, error = true)
}

@Composable
private fun ReminderToggle(title: String, description: String, enabled: Boolean, clickable: Boolean, onChange: (Boolean) -> Unit) {
    val palette = colors
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Copy(title, weight = FontWeight.Bold)
            Copy(description, size = 14, lineHeight = 19, color = palette.secondary)
        }
        Box(Modifier.size(48.dp).toggleable(enabled, enabled = clickable, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = title }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp, 16.dp).background(if (enabled) palette.goal else palette.divider, CircleShape))
            Box(Modifier.align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 4.dp).size(21.dp)
                .background(if (enabled) Color(0xFF6E8757) else Color(0xFFF4F1F5), CircleShape)
                .border(1.dp, palette.divider, CircleShape))
        }
    }
}
