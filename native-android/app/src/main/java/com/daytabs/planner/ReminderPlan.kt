package com.daytabs.planner

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class ReminderKind(val title: String, val setting: String, val description: String) {
    TODOS("Unfinished to-dos", "Pending to-dos", "7 PM if today’s list still has unfinished items."),
    GOALS("Goal deadlines", "Goal deadlines", "9 AM the day before and on the finish date."),
    TODO_DEADLINE("To-do deadline", "To-do deadlines", "At each deadline while the to-do is still unfinished."),
    PROGRESS("Goal progress notes", "Daily goal progress notes", "8 PM for active goals without a progress note today."),
    ;

    fun enabled(prefs: ReminderPreferences): Boolean = when (this) {
        TODOS -> prefs.todos
        GOALS -> prefs.goals
        TODO_DEADLINE -> prefs.todoDeadlines
        PROGRESS -> prefs.progress
    }

    fun set(prefs: ReminderPreferences, enabled: Boolean): ReminderPreferences = when (this) {
        TODOS -> prefs.copy(todos = enabled)
        GOALS -> prefs.copy(goals = enabled)
        TODO_DEADLINE -> prefs.copy(todoDeadlines = enabled)
        PROGRESS -> prefs.copy(progress = enabled)
    }
}

internal data class ReminderAlarm(val kind: ReminderKind, val token: String, val time: Long) {
    val key: String get() = "${kind.name}:$token:$time"

    companion object {
        fun fromKey(key: String): ReminderAlarm? {
            val parts = key.split(':', limit = 3)
            val kind = ReminderKind.entries.firstOrNull { it.name == parts.firstOrNull() } ?: return null
            val token = parts.getOrNull(1) ?: return null
            val time = parts.getOrNull(2)?.toLongOrNull() ?: return null
            return ReminderAlarm(kind, token, time)
        }
    }
}

internal fun todoMessage(count: Int) = "You have $count ${if (count == 1) "to-do" else "to-dos"} left today."

internal fun goalMessage(goals: List<Entry>, day: LocalDate, includeSingleDayTomorrow: Boolean = true): String {
    val pending = goals.filterNot { it.completed }
    val dueToday = pending.count { it.schedule?.finish == day }
    val dueTomorrow = pending.count {
        val schedule = it.schedule ?: return@count false
        schedule.finish == day.plusDays(1) && (includeSingleDayTomorrow || schedule.start < schedule.finish)
    }
    return listOf("today" to dueToday, "tomorrow" to dueTomorrow).mapNotNull { (whenDue, count) ->
        if (count == 0) null else "$count ${if (count == 1) "goal is" else "goals are"} due $whenDue."
    }.joinToString(" ")
}

internal fun List<Entry>.awaitingProgress(day: LocalDate) = count {
    !it.completed && it.schedule?.phase(day) == GoalPhase.ACTIVE && it.progress.none { note -> note.day == day }
}

internal fun progressMessage(count: Int) = if (count == 1) "Add a progress note for 1 active goal today."
    else "Add progress notes for $count active goals today."

internal fun PlannerState.pendingDeadlines() = records.asSequence()
    .filter { it.key.startsWith("todo:") }.flatMap { it.value.asSequence() }
    .filter { !it.completed && it.deadline != null }

internal fun planReminders(
    state: PlannerState,
    now: Instant,
    zone: ZoneId,
    existing: Set<String>,
): List<ReminderAlarm> {
    val today = now.atZone(zone).toLocalDate()
    val nowMs = now.toEpochMilli()
    fun localAlarm(kind: ReminderKind, day: LocalDate, hour: Int) =
        ReminderAlarm(kind, day.toString(), day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli())

    fun isDue(alarm: ReminderAlarm) = alarm.time > nowMs || alarm.key in existing

    val openGoals = state.goals.filter { !it.completed && it.schedule != null }
    return buildList {
        if (state.reminders.todos) {
            state.records.asSequence().filter { (key, entries) -> key.startsWith("todo:") && entries.any { !it.completed } }
                .mapNotNull { (key, _) -> parseDay(key.substringAfter(':')) }
                .filter { it >= today }.sorted()
                .map { localAlarm(ReminderKind.TODOS, it, 19) }.firstOrNull(::isDue)?.let(::add)
        }
        if (state.reminders.goals) {
            val deadlines = openGoals.asSequence().mapNotNull { it.schedule }
                .flatMap { if (it.start < it.finish) sequenceOf(it.finish.minusDays(1), it.finish) else sequenceOf(it.finish) }
                .filter { it >= today }.distinct().sorted()
                .map { localAlarm(ReminderKind.GOALS, it, 9) }.filter(::isDue).take(61)
            addAll(deadlines)
        }
        if (state.reminders.todoDeadlines) {
            val deadlines = state.pendingDeadlines().mapNotNull { it.deadline }.distinct().sorted()
                .map { ReminderAlarm(ReminderKind.TODO_DEADLINE, it.toString(), it) }
                .filter { isDue(it) && it.time > nowMs - 24 * 3_600_000L }.take(61)
            addAll(deadlines)
        }
        if (state.reminders.progress) {
            // Only the next applicable day needs an alarm. Delivery plans the following day.
            val next = openGoals.mapNotNull { goal ->
                val schedule = requireNotNull(goal.schedule)
                val noted = goal.progress.mapTo(mutableSetOf()) { it.day }
                generateSequence(maxOf(today, schedule.start)) { it.plusDays(1) }
                    .takeWhile { it <= schedule.finish }
                    .filterNot { it in noted }.map { localAlarm(ReminderKind.PROGRESS, it, 20) }
                    .firstOrNull(::isDue)
            }.minByOrNull { it.time }
            next?.let(::add)
        }
    }
}

internal fun reminderBody(state: PlannerState, alarm: ReminderAlarm, today: LocalDate): String? {
    if (!alarm.kind.enabled(state.reminders)) return null
    if (alarm.kind == ReminderKind.TODO_DEADLINE) {
        val items = state.pendingDeadlines().filter { it.deadline == alarm.time }.toList()
        return when (items.size) {
            0 -> null
            1 -> "Still unfinished: ${items.single().title}"
            else -> "${items.size} to-dos have reached their deadline."
        }
    }
    if (parseDay(alarm.token) != today) return null
    return when (alarm.kind) {
        ReminderKind.TODOS -> state.todos(today).count { !it.completed }.takeIf { it > 0 }?.let(::todoMessage)
        ReminderKind.GOALS -> goalMessage(state.goals, today, false).takeIf { it.isNotEmpty() }
        ReminderKind.PROGRESS -> state.goals.awaitingProgress(today).takeIf { it > 0 }?.let(::progressMessage)
        ReminderKind.TODO_DEADLINE -> null
    }
}
