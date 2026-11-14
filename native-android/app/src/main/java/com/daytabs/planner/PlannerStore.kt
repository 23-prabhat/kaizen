package com.daytabs.planner

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

internal const val MAX_REPORT_BYTES = 1_000_000
internal const val MAX_AVATARS = 4
internal const val AVATAR_PREFIX = "data:image/png;base64,"
internal val firstCalendarDay: LocalDate = LocalDate.of(1, 1, 1)
internal val lastCalendarDay: LocalDate = LocalDate.of(9999, 12, 31)

internal data class Schedule(val start: LocalDate, val finish: LocalDate)
internal data class ProgressNote(val day: LocalDate, val text: String)
internal enum class GoalTag(val label: String) { SHORT_TERM("Short term"), LONG_TERM("Long term") }
internal data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String? = null,
    val completedAt: String? = null,
    val schedule: Schedule? = null,
    val deadline: Long? = null,
    val deadlineStart: Long? = null,
    val progress: List<ProgressNote> = emptyList(),
    val goalTag: GoalTag? = null,
) {
    val completed: Boolean get() = completedAt != null
}

internal data class AvatarPhoto(val id: String, val uri: String)
internal data class Avatars(val photos: List<AvatarPhoto> = emptyList(), val fixedId: String? = null)
internal data class ReminderPreferences(
    val todos: Boolean = false,
    val goals: Boolean = false,
    val todoDeadlines: Boolean = false,
    val progress: Boolean = false,
)
internal enum class Appearance(val label: String) { LIGHT("Light"), DARK("Dark"), SYSTEM("System") }

internal data class PlannerState(
    val records: Map<String, List<Entry>> = emptyMap(),
    val appearance: Appearance = Appearance.LIGHT,
    val avatars: Avatars = Avatars(),
    val reminders: ReminderPreferences = ReminderPreferences(),
    val opening: Long = -1,
) {
    fun todos(day: LocalDate) = records[todoKey(day)].orEmpty()
    val goals: List<Entry> get() = records[GOALS_KEY].orEmpty()
}

internal const val GOALS_KEY = "goals"
internal fun todoKey(day: LocalDate) = "todo:$day"

private const val RECORDS = "daytabs.demo.v1"
private const val APPEARANCE = "daytabs.appearance"
private const val AVATAR = "daytabs.avatar.v1:local-demo"
private const val REMINDERS = "daytabs.reminders:local-demo"
private const val OPENING = "daytabs.opening:local-demo"
private const val MIGRATED = "reactNativeStorageImported"

internal class PlannerStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("kaizen", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow<PlannerState?>(null)
    val state = mutableState.asStateFlow()

    @Synchronized
    fun load(): PlannerState {
        mutableState.value?.let { return it }
        if (!preferences.getBoolean(MIGRATED, false)) {
            val legacy = readLegacyValues()
            decode(legacy)
            val edit = preferences.edit().putBoolean(MIGRATED, true)
            legacy.forEach { (key, value) -> edit.putString(key, value) }
            check(edit.commit()) { "Could not migrate planner storage." }
        }
        return decode(legacyKeys.mapNotNull { key ->
            preferences.getString(key, null)?.let { key to it }
        }.toMap()).also { mutableState.value = it }
    }

    @Synchronized
    fun update(change: (PlannerState) -> PlannerState) {
        val current = load()
        val next = change(current)
        if (current == next) return
        val edit = preferences.edit()
        if (current.records != next.records) edit.putString(RECORDS, encodeRecords(next.records))
        if (current.appearance != next.appearance) edit.putString(APPEARANCE, next.appearance.name.lowercase())
        if (current.avatars != next.avatars) edit.putString(AVATAR, encodeAvatars(next.avatars))
        if (current.reminders != next.reminders) edit.putString(REMINDERS, JSONObject()
            .put("todos", next.reminders.todos).put("goals", next.reminders.goals)
            .put("todoDeadlines", next.reminders.todoDeadlines).put("progress", next.reminders.progress).toString())
        if (current.opening != next.opening) edit.putString(OPENING, next.opening.toString())
        check(edit.commit()) { "Could not save planner storage." }
        mutableState.value = next
    }

    fun updateEntries(key: String, change: (List<Entry>) -> List<Entry>) = update { state ->
        state.copy(records = state.records + (key to change(state.records[key].orEmpty())))
    }

    fun saveGoalProgressNote(goalId: String, note: ProgressNote) = updateEntries(GOALS_KEY) { goals ->
        check(goals.any { it.id == goalId }) { "This goal was deleted." }
        goals.map { goal ->
            if (goal.id == goalId) goal.copy(progress = goal.progress.filterNot { it.day == note.day } + note)
            else goal
        }
    }

    fun importReport(input: String): Int = addImportedTodos(parseTodoReport(input, Instant.now(), ZoneId.systemDefault()))

    fun addImportedTodos(todos: List<Pair<String, Entry>>): Int {
        var added = 0
        update { state ->
            val records = state.records.toMutableMap()
            todos.groupBy({ it.first }, { it.second }).forEach { (key, incoming) ->
                val entries = records[key].orEmpty()
                val existingIds = entries.mapTo(mutableSetOf()) { it.id }
                val new = incoming.filter { existingIds.add(it.id) }
                records[key] = entries + new
                added += new.size
            }
            state.copy(records = records)
        }
        return added
    }

    private fun readLegacyValues(): Map<String, String> = buildMap {
        // AsyncStorage Next takes precedence when both database formats are present.
        for ((database, table) in listOf("RKStorage" to "catalystLocalStorage", "AsyncStorage" to "Storage")) {
            val file = context.getDatabasePath(database)
            if (!file.isFile) continue
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val placeholders = legacyKeys.joinToString(",") { "?" }
                db.rawQuery("SELECT key, value FROM $table WHERE key IN ($placeholders)", legacyKeys).use { rows ->
                    while (rows.moveToNext()) {
                        if (!rows.isNull(1)) put(rows.getString(0), rows.getString(1))
                    }
                }
            }
        }
    }

    private fun decode(values: Map<String, String>): PlannerState {
        val reminders = values[REMINDERS]?.let { runCatching { JSONObject(it) }.getOrNull() }
        return PlannerState(
            records = values[RECORDS]?.let(::decodeRecords).orEmpty(),
            appearance = Appearance.entries.firstOrNull { it.name.equals(values[APPEARANCE], true) }
                ?: Appearance.LIGHT,
            avatars = values[AVATAR]?.let { runCatching { decodeAvatars(it) }.getOrNull() } ?: Avatars(),
            reminders = ReminderPreferences(
                todos = reminders?.optBoolean("todos") == true,
                goals = reminders?.optBoolean("goals") == true,
                todoDeadlines = reminders?.optBoolean("todoDeadlines") == true,
                progress = reminders?.optBoolean("progress") == true,
            ),
            opening = values[OPENING]?.toLongOrNull()?.coerceAtLeast(-1) ?: -1,
        )
    }

    private companion object {
        val legacyKeys = arrayOf(RECORDS, APPEARANCE, AVATAR, REMINDERS, OPENING)
    }
}

internal fun parseDay(value: String?): LocalDate? {
    if (value == null || value.length != 10) return null
    return runCatching { LocalDate.parse(value) }.getOrNull()?.takeIf { it in firstCalendarDay..lastCalendarDay }
}

private fun decodeRecords(value: String): Map<String, List<Entry>> {
    val source = JSONObject(value)
    val records = source.keys().asSequence().associateWith { key ->
        val rows = source.getJSONArray(key)
        List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val schedule = row.optJSONObject("schedule")?.let {
                Schedule(LocalDate.parse(it.getString("start")), LocalDate.parse(it.getString("finish")))
            }
            Entry(
                id = row.getString("id"),
                title = row.getString("title"),
                note = row.stringOrNull("note"),
                completedAt = row.stringOrNull("completedAt"),
                schedule = schedule,
                deadline = row.optLong("deadline", 0).takeIf { it > 0 },
                deadlineStart = row.optLong("deadlineStart", 0).takeIf { it > 0 },
                progress = decodeProgress(row.optJSONArray("progress")),
                goalTag = GoalTag.entries.firstOrNull { it.name == row.stringOrNull("goalTag") },
            )
        }
    }.toMutableMap()
    val goals = records[GOALS_KEY].orEmpty().toMutableList()
    val legacyGoal = Regex("target:(week|month):(.+)")
    for (key in records.keys.toList()) {
        val match = legacyGoal.matchEntire(key) ?: continue
        val start = LocalDate.parse(match.groupValues[2])
        val finish = if (match.groupValues[1] == "week") start.plusDays(6) else start.plusMonths(1).minusDays(1)
        goals += records.remove(key).orEmpty().map { it.copy(schedule = Schedule(start, finish)) }
    }
    if (goals.isNotEmpty()) records[GOALS_KEY] = goals
    return records
}

private fun encodeRecords(records: Map<String, List<Entry>>): String = JSONObject().apply {
    records.forEach { (key, entries) ->
        put(key, JSONArray().apply {
            entries.forEach { entry ->
                put(JSONObject().put("id", entry.id).put("title", entry.title)
                    .put("note", entry.note ?: JSONObject.NULL)
                    .put("completedAt", entry.completedAt ?: JSONObject.NULL).apply {
                        entry.deadline?.let { put("deadline", it) }
                        entry.deadlineStart?.let { put("deadlineStart", it) }
                        entry.goalTag?.let { put("goalTag", it.name) }
                        if (entry.progress.isNotEmpty()) {
                            put("progress", JSONArray().apply {
                                entry.progress.forEach { note ->
                                    put(JSONObject().put("day", note.day.toString()).put("text", note.text))
                                }
                            })
                        }
                        entry.schedule?.let {
                            put("schedule", JSONObject().put("start", it.start.toString()).put("finish", it.finish.toString()))
                        }
                    })
            }
        })
    }
}.toString()

private fun decodeProgress(notes: JSONArray?): List<ProgressNote> {
    if (notes == null) return emptyList()
    return (0 until notes.length()).mapNotNull { index ->
        val note = notes.optJSONObject(index) ?: return@mapNotNull null
        val day = parseDay(note.stringOrNull("day")) ?: return@mapNotNull null
        val text = note.stringOrNull("text")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        ProgressNote(day, text)
    }.distinctBy { it.day }
}

private fun decodeAvatars(value: String): Avatars {
    if (value.startsWith(AVATAR_PREFIX)) return Avatars(listOf(AvatarPhoto("original-photo", value)), "original-photo")
    val saved = JSONObject(value)
    val photos = saved.optJSONArray("photos")
    val valid = List(minOf(photos?.length() ?: 0, MAX_AVATARS)) { index ->
        val photo = photos?.optJSONObject(index)
        val id = photo?.stringOrNull("id")
        val uri = photo?.stringOrNull("uri")
        if (id != null && uri?.startsWith(AVATAR_PREFIX) == true) AvatarPhoto(id, uri) else null
    }.filterNotNull()
    return Avatars(valid, saved.stringOrNull("fixedId")?.takeIf { id -> valid.any { it.id == id } })
}

private fun encodeAvatars(avatars: Avatars) = JSONObject()
    .put("fixedId", avatars.fixedId ?: JSONObject.NULL)
    .put("photos", JSONArray().apply {
        avatars.photos.forEach { put(JSONObject().put("id", it.id).put("uri", it.uri)) }
    }).toString()

private fun JSONObject.stringOrNull(key: String) = opt(key) as? String
