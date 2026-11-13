package com.daytabs.planner

import android.util.Xml
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

internal const val MAX_TODO_FILE_BYTES = 2_000_000
private const val MAX_WORKBOOK_BYTES = 16_000_000
private const val MAX_ROWS = 5_000
private val columns = listOf("date", "todo work", "deadline")
private val reportId = Regex("[A-Za-z0-9_-]{1,120}")

private data class ImportRow(val number: Int, val cells: List<String>)
private data class TodoSheet(val rows: List<ImportRow>, val date1904: Boolean? = null)

internal fun parseTodoFile(bytes: ByteArray, now: Instant, zone: ZoneId): List<Pair<String, Entry>> {
    require(bytes.size <= MAX_TODO_FILE_BYTES) { "Choose an Excel or CSV file under 2 MB." }
    val sheet = when {
        bytes.take(2) == listOf('P'.code.toByte(), 'K'.code.toByte()) -> readWorkbook(bytes)
        bytes.take(2) == listOf(0xD0.toByte(), 0xCF.toByte()) ->
            throw IllegalArgumentException("Save your Excel file as .xlsx or CSV, then try again.")
        else -> TodoSheet(readCsv(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")))
    }
    val rows = sheet.rows.filter { row -> row.cells.any { it.isNotBlank() } }
    require(rows.firstOrNull()?.cells?.let { header ->
        header.take(3).map { it.trim().lowercase(Locale.ROOT) } == columns &&
            header.drop(3).none { it.isNotBlank() }
    } == true) { "Use the columns date, todo work, and deadline in that order." }
    require(rows.size > 1) { "The file has no to-dos to import." }
    require(rows.size <= MAX_ROWS + 1) { "Import up to 5,000 to-dos per file." }

    val digest = MessageDigest.getInstance("SHA-256")
    return rows.drop(1).map { row ->
        val dateText = row.cells.getOrElse(0) { "" }.trim()
        val title = row.cells.getOrElse(1) { "" }.trim()
        val timeText = row.cells.getOrElse(2) { "" }.trim()
        val day = parseDay(dateText) ?: sheet.date1904?.let {
            parseDay(dateText.removeSuffix("T00:00:00")) ?: excelDate(dateText, it)
        }
        require(day != null) { "Row ${row.number} needs a valid date in YYYY-MM-DD format." }
        require(title.isNotBlank() && title.length <= 160) { "Row ${row.number} needs a task title of 1 to 160 characters." }
        require(row.cells.drop(3).none { it.isNotBlank() }) { "Row ${row.number} has extra columns." }
        val deadline = importDeadline("Row ${row.number}", timeText, day, now, zone, sheet.date1904 != null)
        todoKey(day) to Entry(id = bulkId(digest, day, title, deadline), title = title,
            deadline = deadline.deadline, deadlineStart = deadline.start)
    }
}

internal fun parseTodoReport(input: String, now: Instant, zone: ZoneId): List<Pair<String, Entry>> {
    require(input.length <= MAX_REPORT_BYTES) { "Choose a JSON file under 1 MB." }
    val source = try {
        JSONArray(input.removePrefix("\uFEFF"))
    } catch (_: JSONException) {
        throw IllegalArgumentException("Use a JSON array of to-dos.")
    }
    require(source.length() > 0) { "No to-dos found in this file." }
    require(source.length() <= MAX_ROWS) { "Import up to 5,000 to-dos per file." }
    val digest = MessageDigest.getInstance("SHA-256")
    return List(source.length()) { index ->
        val label = "To-do ${index + 1}"
        val row = source.optJSONObject(index) ?: throw IllegalArgumentException("$label must be a JSON object.")
        val day = parseDay((row.opt("day") ?: row.opt("date")) as? String)
        val title = ((row.opt("title") ?: row.opt("todo work")) as? String)?.trim()
        require(day != null) { "$label needs a valid date in YYYY-MM-DD format." }
        require(!title.isNullOrEmpty() && title.length <= 160) { "$label needs a task title of 1 to 160 characters." }
        val id = row.optionalString("id", label)
        val note = row.optionalString("note", label)
        val done = row.opt("done")
        require(id == null || reportId.matches(id)) { "$label has an invalid ID." }
        require(note == null || note.length <= 4000) { "$label needs a note of up to 4,000 characters." }
        require(done == null || done is Boolean) { "$label needs true or false for done." }
        val deadline = importDeadline(label, row.optionalString("deadline", label).orEmpty(), day, now, zone)
        todoKey(day) to Entry(
            id = if (id == null) bulkId(digest, day, title, deadline) else "work:$id",
            title = title, note = note, completedAt = if (done == true) "${day}T12:00:00.000Z" else null,
            deadline = deadline.deadline, deadlineStart = deadline.start,
        )
    }
}

private fun JSONObject.optionalString(key: String, label: String): String? {
    if (isNull(key)) return null
    return opt(key) as? String ?: throw IllegalArgumentException("$label needs text for $key.")
}

private fun importDeadline(label: String, text: String, day: LocalDate, now: Instant, zone: ZoneId, excel: Boolean = false) =
    try {
        parseImportedDeadline(text, day, now, zone, excel)
    } catch (failure: IllegalArgumentException) {
        throw IllegalArgumentException("$label: ${failure.message}")
    }

private fun bulkId(digest: MessageDigest, day: LocalDate, title: String, deadline: ImportedDeadline): String {
    val signature = "$day\u0000$title\u0000${deadline.identity}"
    val id = digest.digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
    return "bulk:$id"
}

private fun excelDate(text: String, date1904: Boolean): LocalDate? {
    val value = text.toDoubleOrNull() ?: return null
    if (!value.isFinite() || value < 0 || value > 3_000_000 || value % 1 != 0.0) return null
    val days = value.toLong()
    if (!date1904 && days == 60L) return null
    val epoch = if (date1904) LocalDate.of(1904, 1, 1)
        else LocalDate.of(1899, 12, if (days < 60) 31 else 30)
    return epoch.plusDays(days).takeIf { it in firstCalendarDay..lastCalendarDay }
}

private fun readCsv(text: String): List<ImportRow> {
    val rows = mutableListOf<ImportRow>()
    val cells = mutableListOf<String>()
    val field = StringBuilder()
    var index = 0
    var indexRow = 1
    var quoted = false
    var closedQuote = false

    fun endField() {
        cells += field.toString()
        require(cells.size <= 256) { "The CSV contains too many columns." }
        field.clear()
        closedQuote = false
    }

    fun endRow() {
        endField()
        if (cells.any { it.isNotBlank() }) rows += ImportRow(indexRow, cells.toList())
        require(rows.size <= MAX_ROWS + 1) { "Import up to 5,000 to-dos per file." }
        cells.clear()
    }

    while (index < text.length) {
        val char = text[index++]
        if (quoted) {
            if (char != '"') field.append(char)
            else if (text.getOrNull(index) == '"') {
                field.append('"')
                index++
            } else {
                quoted = false
                closedQuote = true
            }
            continue
        }
        when (char) {
            ',' -> endField()
            '\r', '\n' -> {
                if (char == '\r' && text.getOrNull(index) == '\n') index++
                endRow()
                indexRow++
            }
            '"' -> {
                require(field.isEmpty() && !closedQuote) { "Check the quotes in CSV row $indexRow." }
                quoted = true
            }
            else -> {
                require(!closedQuote || char.isWhitespace()) { "Check the quotes in CSV row $indexRow." }
                field.append(char)
            }
        }
    }
    require(!quoted) { "Close the quoted field in CSV row $indexRow." }
    if (field.isNotEmpty() || cells.isNotEmpty() || closedQuote) endRow()
    return rows
}

private fun readWorkbook(bytes: ByteArray): TodoSheet {
    val parts = workbookParts(bytes)
    val workbook = parts["xl/workbook.xml"] ?: throw IllegalArgumentException("Choose a valid .xlsx workbook.")
    val relations = parts["xl/_rels/workbook.xml.rels"] ?: throw IllegalArgumentException("The workbook has no worksheets.")
    var firstSheet: String? = null
    var date1904 = false
    readTags(workbook) { parser ->
        when (parser.name) {
            "workbookPr" -> date1904 = parser.attr("date1904") in setOf("1", "true")
            "sheet" -> if (firstSheet == null) firstSheet = parser.attr("id")
        }
    }
    var sheetPath: String? = null
    readTags(relations) { parser ->
        if (parser.name == "Relationship" && parser.attr("Id") == firstSheet &&
            parser.attr("Type")?.endsWith("/worksheet") == true && parser.attr("TargetMode") != "External") {
            sheetPath = parser.attr("Target")?.let(::workbookPath)
        }
    }
    val sheet = sheetPath?.let(parts::get) ?: throw IllegalArgumentException("The first tab must be a worksheet.")
    val strings = mutableListOf<String>()
    parts["xl/sharedStrings.xml"]?.let { shared ->
        readTags(shared) { if (it.name == "si") strings += readTextRuns(it) }
    }
    val rows = mutableListOf<ImportRow>()
    readTags(sheet) { parser ->
        if (parser.name == "row") {
            val row = readWorksheetRow(parser, rows.size + 1, strings)
            if (row.cells.any { it.isNotBlank() }) rows += row
            require(rows.size <= MAX_ROWS + 1) { "Import up to 5,000 to-dos per file." }
        }
    }
    return TodoSheet(rows, date1904)
}

private fun workbookPath(target: String): String {
    val path = if (target.startsWith('/')) target.drop(1) else "xl/$target"
    val folders = mutableListOf<String>()
    path.split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (folders.isNotEmpty()) folders.removeAt(folders.lastIndex)
            else -> folders += part
        }
    }
    return folders.joinToString("/")
}

private fun workbookParts(bytes: ByteArray): Map<String, ByteArray> = buildMap {
    ZipInputStream(bytes.inputStream()).use { archive ->
        var expanded = 0
        var entries = 0
        val buffer = ByteArray(8192)
        while (true) {
            val entry = archive.nextEntry ?: break
            require(++entries <= 2_000) { "The Excel workbook contains too many files." }
            val name = entry.name
            val retain = name.startsWith("xl/") && (name.endsWith(".xml") || name.endsWith(".rels")) && !entry.isDirectory
            val data = if (retain) ByteArrayOutputStream() else null
            while (true) {
                val size = archive.read(buffer)
                if (size < 0) break
                expanded += size
                require(expanded <= MAX_WORKBOOK_BYTES) { "The Excel workbook is too large to import." }
                data?.write(buffer, 0, size)
            }
            if (data != null) put(name, data.toByteArray())
        }
    }
}

private fun readTags(bytes: ByteArray, visit: (XmlPullParser) -> Unit) {
    val parser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        setInput(bytes.inputStream(), null)
    }
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG) visit(parser)
    }
}

private fun XmlPullParser.attr(name: String): String? =
    (0 until attributeCount).firstOrNull { getAttributeName(it) == name }?.let(::getAttributeValue)

private inline fun XmlPullParser.readChildren(visit: (XmlPullParser) -> Unit) {
    val parentDepth = depth
    while (true) {
        when (next()) {
            XmlPullParser.START_TAG -> visit(this)
            XmlPullParser.END_TAG -> if (depth == parentDepth) return
            XmlPullParser.END_DOCUMENT -> throw IllegalArgumentException("The Excel worksheet is incomplete.")
        }
    }
}

private fun readTextRuns(parser: XmlPullParser): String = buildString {
    val root = parser.depth
    var phonetic = false
    parser.readChildren {
        if (it.depth == root + 1) phonetic = it.name == "rPh"
        if (it.name == "t" && !phonetic) append(it.nextText())
    }
}

private fun readWorksheetRow(parser: XmlPullParser, fallback: Int, strings: List<String>): ImportRow {
    val rowNumber = parser.attr("r")?.toIntOrNull() ?: fallback
    val cells = MutableList(4) { "" }
    var nextColumn = 0
    parser.readChildren { cell ->
        if (cell.name != "c") return@readChildren
        val column = cell.attr("r")?.takeWhile { it in 'A'..'Z' }?.let {
            when (it) { "A" -> 0; "B" -> 1; "C" -> 2; else -> 3 }
        } ?: nextColumn
        nextColumn = column + 1
        val type = cell.attr("t")
        var value = ""
        cell.readChildren {
            when (it.name) {
                "v" -> value = it.nextText()
                "is" -> value = readTextRuns(it)
            }
        }
        if (type == "s") value = value.toIntOrNull()?.let(strings::getOrNull)
            ?: throw IllegalArgumentException("Row $rowNumber contains an invalid Excel cell.")
        val index = column.coerceIn(0, 3)
        if (value.isNotBlank()) cells[index] = value
    }
    return ImportRow(rowNumber, cells)
}
