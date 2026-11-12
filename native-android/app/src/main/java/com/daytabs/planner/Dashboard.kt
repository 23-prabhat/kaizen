package com.daytabs.planner

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate

internal val mascots = listOf(
    "Desk cat" to R.drawable.desk_cat,
    "Coffee cat" to R.drawable.desk_cat_coffee,
    "Focus cat" to R.drawable.desk_cat_focus,
    "Victory cat" to R.drawable.desk_cat_victory,
)

private val greetings = listOf(
    "Ready for today?", "One thing at a time", "Pick your first quest", "Let’s get moving",
    "A fresh page", "Time to level up", "Make today count", "Back in action",
)

@Composable
internal fun ColumnScope.Dashboard(
    state: PlannerState,
    today: LocalDate,
    onNavigate: (Tab) -> Unit,
    onNotifications: () -> Unit,
    onWidget: () -> Unit,
) {
    val palette = colors
    val todaysEntries = state.todos(today)
    val remaining = todaysEntries.filterNot { it.completed }
    val completed = todaysEntries.filter { it.completed }
    val active = state.goals.filter { it.schedule?.phase(today) == GoalPhase.ACTIVE }.sortedBy { it.schedule?.finish }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(6.dp)
            Image(painterResource(R.drawable.kaizen_mark), null,
                Modifier.size(32.dp).rotate(-7f).clip(shape).border(2.dp, actionInk, shape),
                contentScale = ContentScale.FillBounds)
            Copy("Kaizen", size = 25, lineHeight = 30, display = true, spacing = -0.9f)
        }
        val shape = RoundedCornerShape(9.dp)
        IconButton("bell", "Notifications",
            Modifier.clip(shape).background(palette.surface).border(2.dp, palette.ink, shape),
            onClick = onNotifications)
    }
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy((-16).dp)) {
            DeskCover(state, today) { onNavigate(Tab.SETTINGS) }
            MotivationCard(today, onWidget)
        }
        Column {
            SectionTitle("Today’s to-dos", "View to-dos") { onNavigate(Tab.TODOS) }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodoSummary("Remaining", remaining, "Nothing left on the list.", palette.remaining, "circle",
                    Modifier.weight(1f).fillMaxHeight())
                TodoSummary("Completed", completed, "No checkmarks yet.", palette.completed, "check-circle",
                    Modifier.weight(1f).fillMaxHeight())
            }
        }
        Column {
            SectionTitle("Activity", "View history") { onNavigate(Tab.HISTORY) }
            ActivityChart(remember(state.records, today) { state.activity(today) })
        }
        Column {
            SectionTitle("Active goals", "View goals") { onNavigate(Tab.GOALS) }
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    if (active.isEmpty()) {
                        Copy("No goals active today.", Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = palette.secondary)
                    } else {
                        Copy("${active.count { it.completed }} of ${active.size} complete",
                            Modifier.fillMaxWidth().background(palette.goal).padding(horizontal = 12.dp, vertical = 9.dp),
                            size = 14, color = actionInk, weight = FontWeight.Bold)
                        active.take(3).forEach { goal ->
                            Rule()
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                Glyph(if (goal.completed) "check-circle" else "flag", 18)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Copy(goal.title, size = 14, weight = FontWeight.Bold)
                                    Copy(requireNotNull(goal.schedule).label(), size = 13, lineHeight = 17, color = palette.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeskCover(state: PlannerState, today: LocalDate, onAvatar: () -> Unit) {
    val palette = colors
    val opening = state.opening.coerceAtLeast(0)
    val avatars = state.avatars
    val position = (opening % (mascots.size + avatars.photos.size)).toInt()
    val photo = avatars.fixedId?.let { id -> avatars.photos.firstOrNull { it.id == id } }
        ?: avatars.photos.getOrNull(position - mascots.size)
    Panel(
        Modifier.fillMaxWidth().blockShadow(palette.shadow, 4.dp, 12.dp),
        background = palette.cover, ink = actionInk, radius = 12.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 134.dp)
            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 24.dp)) {
            val avatarWidth = minOf(122.dp, maxWidth * 0.39f)
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tag = RoundedCornerShape(5.dp)
                    Row(Modifier.heightIn(min = 26.dp).clip(tag).background(actionYellow)
                        .border(1.5.dp, actionInk, tag).padding(horizontal = 7.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph("calendar", 14, actionInk)
                        Copy(dayLabel(today), size = 12, lineHeight = 17, color = actionInk, weight = FontWeight.ExtraBold)
                    }
                    Copy(greetings[(opening % greetings.size).toInt()], Modifier.semantics { heading() },
                        size = 34, lineHeight = 36, color = actionInk, display = true, spacing = -1.3f)
                }
                Box(Modifier.width(avatarWidth).height(122.dp)
                    .clickable(role = Role.Button, onClick = onAvatar)
                    .semantics { contentDescription = "Change dashboard avatar" }) {
                    AvatarImage(photo?.uri, Modifier.fillMaxSize(), position % mascots.size)
                    val edit = RoundedCornerShape(6.dp)
                    Box(Modifier.align(Alignment.BottomEnd).size(27.dp).clip(edit).background(actionYellow)
                        .border(1.5.dp, actionInk, edit), contentAlignment = Alignment.Center) {
                        Glyph("edit-2", 12, actionInk)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AvatarImage(uri: String?, modifier: Modifier = Modifier, index: Int = 0, label: String? = null) {
    val custom = remember(uri) {
        if (uri == null) null else runCatching {
            val data = Base64.decode(uri.removePrefix(AVATAR_PREFIX), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (custom == null) {
        Image(painterResource(mascots[index].second), label, modifier, contentScale = ContentScale.Fit)
    } else {
        val shape = RoundedCornerShape(12.dp)
        Image(custom, label, modifier.clip(shape).border(2.dp, actionInk, shape), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun MotivationCard(today: LocalDate, onWidget: () -> Unit) {
    val quote = dailyMotivation(today)
    Panel(Modifier.fillMaxWidth().padding(horizontal = 10.dp)
        .blockShadow(colors.shadow, 5.dp, 10.dp), background = actionYellow, ink = actionInk, radius = 10.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 5.dp, end = 12.dp, bottom = 13.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                itemVerticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph("zap", 16, actionInk)
                    Copy("Daily motivation", size = 13, lineHeight = 18, color = actionInk, weight = FontWeight.Bold)
                }
                Row(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onWidget).padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph("plus-square", 15, actionInk)
                    Copy("Add widget", size = 12, lineHeight = 16, color = actionInk, weight = FontWeight.Bold)
                }
            }
            Copy(quote.displayLine, size = 25, lineHeight = 29, color = actionInk, display = true, spacing = -0.6f)
            quote.credit?.let { Copy(it, Modifier.padding(top = 5.dp), size = 13, lineHeight = 18, color = actionInk) }
        }
    }
}

@Composable
private fun SectionTitle(title: String, link: String, onClick: () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
            Copy(title, Modifier.semantics { heading() }, size = 22, display = true, spacing = -0.5f)
        }
        Row(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Copy(link, size = 14, weight = FontWeight.Bold)
            Glyph("arrow-right", 16)
        }
    }
}

@Composable
private fun TodoSummary(
    label: String, entries: List<Entry>, empty: String, tone: Color, icon: String, modifier: Modifier,
) {
    Panel(modifier) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().background(tone).padding(start = 9.dp, top = 7.dp, end = 9.dp, bottom = 5.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(icon, 15, actionInk)
                    Copy(label, size = 13, lineHeight = 18, color = actionInk, weight = FontWeight.ExtraBold)
                }
                Copy(entries.size.toString(), size = 38, lineHeight = 42, color = actionInk, display = true, spacing = -1.4f)
            }
            Rule(color = actionInk, thickness = 2.dp)
            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (entries.isEmpty()) Copy(empty, size = 13, lineHeight = 17, color = colors.secondary)
                entries.take(2).forEach { Copy(it.title, size = 13, lineHeight = 17, maxLines = 2) }
            }
        }
    }
}

@Composable
private fun ActivityChart(data: List<ActivityDay>) {
    val palette = colors
    val maximum = data.maxOf { it.done + it.remaining }.coerceAtLeast(2)
    val metrics = listOf(
        Triple("Completed", data.sumOf { it.done }, palette.completed),
        Triple("Remaining", data.sumOf { it.remaining }, palette.remaining),
        Triple("Days logged", data.count { it.done + it.remaining > 0 }, null),
    )
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Copy("To-dos by date", size = 12, lineHeight = 16, color = palette.secondary, weight = FontWeight.Bold)
                Copy("Last 14 days", size = 12, lineHeight = 16, color = palette.secondary, weight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                metrics.forEachIndexed { index, (label, value, swatch) ->
                    if (index != 0) Box(Modifier.width(1.dp).fillMaxHeight().background(palette.divider))
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Copy(value.toString(), size = 28, lineHeight = 32, display = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (swatch != null) {
                                val chip = RoundedCornerShape(2.dp)
                                Box(Modifier.size(9.dp).background(swatch, chip).border(1.dp, actionInk, chip))
                            }
                            Copy(label, size = 11, lineHeight = 14, color = palette.secondary)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.height(64.dp), verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End) {
                    Copy(maximum.toString(), size = 11, lineHeight = 14, color = palette.secondary)
                    Copy("0", size = 11, lineHeight = 14, color = palette.secondary)
                }
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().height(64.dp)) {
                        Rule(Modifier.align(Alignment.TopCenter))
                        Rule(Modifier.align(Alignment.Center), thickness = 0.5.dp)
                        Rule(Modifier.align(Alignment.BottomCenter))
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                            data.forEach { item ->
                                val description = "${dayLabel(item.day)}, ${item.done} completed, ${item.remaining} remaining"
                                Box(Modifier.weight(1f).semantics { contentDescription = description },
                                    contentAlignment = Alignment.BottomCenter) {
                                    if (item.done + item.remaining == 0) {
                                        Box(Modifier.fillMaxWidth(0.46f).height(2.dp).background(palette.divider))
                                    } else {
                                        Column(Modifier.fillMaxWidth(0.72f)
                                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))) {
                                            Box(Modifier.fillMaxWidth().height(62.dp * item.remaining / maximum).background(palette.remaining))
                                            Box(Modifier.fillMaxWidth().height(62.dp * item.done / maximum).background(palette.completed))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf(data.first(), data[6], data.last()).forEach {
                            Copy(dateLabel(it.day), size = 11, lineHeight = 14, color = palette.secondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Rule(modifier: Modifier = Modifier, color: Color = colors.divider, thickness: Dp = 1.dp) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}
