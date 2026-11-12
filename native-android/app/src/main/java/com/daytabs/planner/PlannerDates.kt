package com.daytabs.planner

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val shortDay = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.UK)
private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
private val fullDate = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.UK)
private val month = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.UK)
private val accessibleDate = DateTimeFormatter.ofPattern("EEEE d MMMM uuuu", Locale.UK)

internal fun dayLabel(day: LocalDate): String = day.format(shortDay)
internal fun dateLabel(day: LocalDate): String = day.format(shortDate)
internal fun fullDayLabel(day: LocalDate): String = day.format(fullDate)
internal fun monthLabel(day: LocalDate): String = day.format(month)
internal fun accessibleDayLabel(day: LocalDate): String = day.format(accessibleDate)
internal fun calendarDays(start: LocalDate, end: LocalDate): Long = ChronoUnit.DAYS.between(start, end) + 1

internal enum class GoalPhase(val label: String) { ACTIVE("Active"), UPCOMING("Upcoming"), PAST("Past") }

internal fun Schedule.phase(today: LocalDate): GoalPhase = when {
    today < start -> GoalPhase.UPCOMING
    today > finish -> GoalPhase.PAST
    else -> GoalPhase.ACTIVE
}

internal fun Schedule.label(): String = when {
    start == finish -> fullDayLabel(start)
    start.year == finish.year -> "${dateLabel(start)} – ${fullDayLabel(finish)}"
    else -> "${fullDayLabel(start)} – ${fullDayLabel(finish)}"
}

internal fun finishForDuration(start: LocalDate, text: String): LocalDate? {
    val days = text.toLongOrNull() ?: return null
    return if (days in 1..calendarDays(start, lastCalendarDay)) start.plusDays(days - 1) else null
}

internal data class ActivityDay(val day: LocalDate, val done: Int, val remaining: Int)

internal fun PlannerState.activity(today: LocalDate): List<ActivityDay> = (13L downTo 0L).map { offset ->
    val day = today.minusDays(offset)
    val entries = todos(day)
    val done = entries.count { it.completed }
    ActivityDay(day, done, entries.size - done)
}
