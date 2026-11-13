package com.daytabs.planner

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var foreground by mutableIntStateOf(0)
    private var reminderTab by mutableStateOf<Tab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openReminder(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, darkPalette.surface.toArgb()),
        )
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        val store = PlannerStore(applicationContext)
        setContent {
            val state by store.state.collectAsState()
            var loadError by remember { mutableStateOf(false) }
            var reload by remember { mutableIntStateOf(0) }
            var today by remember { mutableStateOf(LocalDate.now()) }

            LaunchedEffect(foreground, reload) {
                if (foreground == 0) return@LaunchedEffect
                today = LocalDate.now()
                loadError = false
                try {
                    withContext(Dispatchers.IO) {
                        store.load()
                        store.update { it.copy(opening = if (it.opening == Long.MAX_VALUE) 0 else it.opening + 1) }
                    }
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    loadError = true
                }
                while (true) {
                    delay(15_000)
                    today = LocalDate.now()
                }
            }
            LaunchedEffect(today, foreground, state?.records) {
                withContext(Dispatchers.IO) { DaytabsMotivationProvider.refresh(applicationContext) }
            }
            val appearance = state?.appearance ?: Appearance.LIGHT
            val dark = appearance == Appearance.DARK || appearance == Appearance.SYSTEM && isSystemInDarkTheme()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            CompositionLocalProvider(LocalPalette provides if (dark) darkPalette else lightPalette) {
                val current = state
                if (current != null) {
                    Kaizen(store, current, today, foreground, reminderTab) { reminderTab = null }
                } else {
                    Box(Modifier.fillMaxSize().background(colors.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(Modifier.widthIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Heading("Kaizen")
                            if (loadError) {
                                Message("Could not open your planner. Try again.", error = true)
                                Action("Try again", primary = true) { reload++ }
                            } else {
                                Copy("Loading", color = colors.secondary)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        foreground++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openReminder(intent)
    }

    private fun openReminder(intent: Intent) {
        val destination = intent.getStringExtra(REMINDER_TAB)
        reminderTab = Tab.entries.firstOrNull { it.name == destination }
        intent.removeExtra(REMINDER_TAB)
    }
}

internal const val REMINDER_TAB = "kaizen.reminder.tab"

internal enum class Tab(val label: String, val icon: String) {
    DASHBOARD("Dashboard", "grid"), TODOS("To-dos", "check-square"), GOALS("Goals", "flag"),
    HISTORY("History", "clock"), SETTINGS("Settings", "settings")
}

internal data class EditRequest(
    val key: String,
    val day: LocalDate,
    val entry: Entry? = null,
    val readOnly: Boolean = false,
    val draftTitle: String = "",
)

@Composable
private fun Kaizen(
    store: PlannerStore,
    state: PlannerState,
    today: LocalDate,
    foreground: Int,
    reminderTab: Tab?,
    onReminderOpened: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
    var editor by remember { mutableStateOf<EditRequest?>(null) }
    var notifications by rememberSaveable { mutableStateOf(false) }
    var widget by rememberSaveable { mutableStateOf(false) }
    var settingsReset by rememberSaveable { mutableIntStateOf(0) }
    var todoDayReset by rememberSaveable { mutableIntStateOf(0) }
    val tabState = rememberSaveableStateHolder()
    val context = androidx.compose.ui.platform.LocalContext.current
    var reminderError by remember { mutableStateOf(false) }

    LaunchedEffect(reminderTab, editor) {
        if (reminderTab != null && editor == null) {
            notifications = false
            widget = false
            tab = reminderTab
            if (tab == Tab.TODOS) todoDayReset++
            onReminderOpened()
        }
    }

    LaunchedEffect(state.records, state.reminders, foreground, today) {
        reminderError = try {
            withContext(Dispatchers.IO) { DeviceReminders.reconcile(context.applicationContext, state) }
            false
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            true
        }
    }

    val edit = editor
    BackHandler(edit == null && tab != Tab.DASHBOARD && !notifications && !widget) { tab = Tab.DASHBOARD }
    if (edit != null) {
        androidx.compose.runtime.key(edit) {
            EntryEditor(edit, store, onClose = { editor = null }, onStartGoal = { title ->
                tab = Tab.GOALS
                editor = EditRequest(GOALS_KEY, edit.day.coerceAtLeast(today), draftTitle = title)
            })
        }
    } else {
        Column(Modifier.fillMaxSize().background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Column(Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth()
                .align(Alignment.CenterHorizontally).padding(horizontal = 16.dp)) {
                tabState.SaveableStateProvider(tab.name) {
                    when (tab) {
                        Tab.DASHBOARD -> Dashboard(state, today, { destination ->
                            if (destination == Tab.SETTINGS) settingsReset++
                            if (destination == Tab.TODOS) todoDayReset++
                            tab = destination
                        }, { notifications = true }, { widget = true })
                        Tab.TODOS, Tab.GOALS, Tab.HISTORY -> PlannerTab(tab, state, today, store, todoDayReset) { editor = it }
                        Tab.SETTINGS -> Settings(state, store, settingsReset, foreground, reminderError)
                    }
                }
            }
            BottomTabs(tab) { tab = it }
        }
    }

    if (notifications) {
        NotificationDialog(state, today, store, reminderError, foreground,
            onClose = { notifications = false }, onOpen = {
                notifications = false
                tab = it
            })
    }
    if (widget) {
        KaizenDialog("Add home screen widget", { widget = false }) {
            DialogTitle("Add home screen widget", "Close widget instructions", 26) { widget = false }
            Copy("Android", weight = FontWeight.Bold)
            Copy("Touch and hold an empty spot on the home screen. Open Widgets, find Kaizen, and drag Today’s to-dos onto your screen. Scroll inside the widget to see your full list.",
                Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun BottomTabs(tab: Tab, onSelect: (Tab) -> Unit) {
    val palette = colors
    Column(Modifier.fillMaxWidth().background(palette.surface).navigationBarsPadding()) {
        Rule()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 63.dp).padding(horizontal = 6.dp, vertical = 4.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Top,
        ) {
            Tab.entries.forEach { item ->
                val selected = tab == item
                Column(
                    Modifier.weight(1f).heightIn(min = 48.dp)
                        .selectable(selected, role = Role.Tab, onClick = { onSelect(item) })
                        .semantics { contentDescription = item.label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    val shape = RoundedCornerShape(6.dp)
                    Box(
                        Modifier.size(41.dp, 28.dp)
                            .background(if (selected) actionYellow else Color.Transparent, shape)
                            .border(2.dp, if (selected) actionInk else Color.Transparent, shape),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(item.icon, 20, if (selected) actionInk else palette.secondary) }
                    Copy(item.label, size = 11, lineHeight = 14, weight = FontWeight.SemiBold,
                        color = if (selected) palette.ink else palette.secondary, align = TextAlign.Center)
                }
            }
        }
    }
}
