package com.daytabs.planner

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

internal const val TODO_DURATION_LIMIT = "Use 0–23 hours and 0–59 minutes. For a longer plan, add a goal."
internal const val TODO_DAY_LIMIT = "The deadline must fit within the planned day. For a longer plan, add a goal."

internal class DeadlineWindow(day: LocalDate, now: Instant, zone: ZoneId) {
    private val midnight = day.atStartOfDay(zone).toInstant()
    private val endOfDay = day.plusDays(1).atStartOfDay(zone).toInstant()
    private val start = if (day == now.atZone(zone).toLocalDate()) now else midnight

    fun afterMinutes(minutes: Int): Instant {
        require(minutes in 1..1_439) { TODO_DURATION_LIMIT }
        val deadline = start.plusSeconds(minutes * 60L)
        require(contains(deadline)) { TODO_DAY_LIMIT }
        return deadline
    }

    fun contains(deadline: Instant): Boolean = deadline >= midnight && deadline < endOfDay

    fun startFor(deadline: Instant): Long = minOf(start, deadline).toEpochMilli()
}

internal fun deadlineDuration(hours: Int?, minutes: Int?): Int {
    require(hours != null && hours in 0..23 && minutes != null && minutes in 0..59) { TODO_DURATION_LIMIT }
    val total = hours * 60 + minutes
    require(total > 0) { "Enter at least 1 minute, or remove the deadline for an all-day to-do." }
    return total
}

private val hoursAndMinutes = Regex("""(\d+)\s*(?:h|hr|hrs|hour|hours)(?:\s*(\d+)\s*(?:m|min|mins|minute|minutes))?""", RegexOption.IGNORE_CASE)
private val minutesOnly = Regex("""(\d+)\s*(?:m|min|mins|minute|minutes)""", RegexOption.IGNORE_CASE)
private val clockPattern = Regex("""(\d{1,2})(?::(\d{2})(?::(\d{2}))?)?\s*([AaPp][Mm])?""")

internal data class ImportedDeadline(val deadline: Long? = null, val start: Long? = null, val identity: String = "")

internal fun parseImportedDeadline(
    text: String,
    day: LocalDate,
    now: Instant,
    zone: ZoneId,
    excel: Boolean = false,
): ImportedDeadline {
    val value = text.trim()
    if (value.isEmpty()) return ImportedDeadline()
    val window = DeadlineWindow(day, now, zone)
    val hoursMatch = hoursAndMinutes.matchEntire(value)
    val minuteMatch = minutesOnly.matchEntire(value)
    val duration = when {
        hoursMatch != null -> {
            val (hours, minutes) = hoursMatch.destructured
            deadlineDuration(hours.toIntOrNull(), if (minutes.isEmpty()) 0 else minutes.toIntOrNull())
        }
        minuteMatch != null -> {
            val minutes = minuteMatch.groupValues[1].toIntOrNull()
            require(minutes != null && minutes <= 1_439) { TODO_DURATION_LIMIT }
            deadlineDuration(minutes / 60, minutes % 60)
        }
        else -> null
    }
    if (duration != null) {
        val deadline = window.afterMinutes(duration)
        return ImportedDeadline(deadline.toEpochMilli(), window.startFor(deadline), "duration:$duration")
    }

    val clock = parseClock(value, excel)
        ?: throw IllegalArgumentException("Use a deadline like 17:30, 4hr, 1hr 30 min, or leave it blank.")
    val deadline = day.atTime(clock).atZone(zone).toInstant()
    require(window.contains(deadline)) { TODO_DAY_LIMIT }
    return ImportedDeadline(deadline.toEpochMilli(), window.startFor(deadline), clock.toString())
}

private fun parseClock(text: String, excel: Boolean): LocalTime? {
    if (excel) {
        text.toDoubleOrNull()?.takeIf { it >= 0 && it < 1 }?.let {
            return LocalTime.ofSecondOfDay((it * 86_400).roundToInt().coerceAtMost(86_399).toLong())
        }
    }
    val match = clockPattern.matchEntire(text) ?: return null
    val (hours, minutes, seconds, meridiem) = match.destructured
    var hour = hours.toInt()
    val minute = minutes.toIntOrNull() ?: 0
    val second = seconds.toIntOrNull() ?: 0
    if (meridiem.isNotEmpty()) {
        if (hour !in 1..12) return null
        hour = hour % 12 + if (meridiem.equals("PM", true)) 12 else 0
    } else if (minutes.isEmpty()) return null
    if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
    return LocalTime.of(hour, minute, second)
}
