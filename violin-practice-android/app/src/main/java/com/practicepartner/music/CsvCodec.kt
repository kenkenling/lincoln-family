package com.practicepartner.music

import java.time.LocalDate
import java.util.UUID

data class CsvImportResult(
    val tasks: List<PracticeTask>,
    val errors: List<String>,
)

object CsvCodec {
    private val headers = listOf(
        "date",
        "title",
        "reference_type",
        "source",
        "start_seconds",
        "end_seconds",
        "repetitions",
        "coach_note",
        "why_important",
        "sheet_music_uri",
        "local_video_requires_reattachment",
        "local_sheet_music_requires_reattachment",
    )

    fun encode(tasks: List<PracticeTask>): String = buildString {
        appendLine(headers.joinToString(","))
        tasks.forEach { task ->
            appendLine(
                listOf(
                    task.assignedDate,
                    task.title,
                    task.sourceType.name,
                    task.source.takeIf { task.sourceType == SourceType.YOUTUBE }.orEmpty(),
                    task.startSeconds.toString(),
                    task.endSeconds?.toString().orEmpty(),
                    task.repetitions.toString(),
                    task.coachNote,
                    task.whyImportant,
                    task.sheetMusicUri?.takeIf { it.startsWith("https://") }.orEmpty(),
                    if (task.sourceType == SourceType.LOCAL) "Yes" else "No",
                    if (task.sheetMusicUri?.startsWith("content://") == true) "Yes" else "No",
                ).joinToString(",") { csvCell(it) },
            )
        }
    }

    fun decode(
        text: String,
        defaultDate: String,
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): CsvImportResult {
        val records = runCatching { parseRecords(text) }.getOrElse {
            return CsvImportResult(
                emptyList(),
                listOf(it.message ?: "The CSV could not be read."),
            )
        }
        if (records.isEmpty()) return CsvImportResult(emptyList(), listOf("The CSV is empty."))

        val header = records.first().mapIndexed { index, value ->
            value.trim()
                .let { if (index == 0) it.removePrefix("\uFEFF") else it }
                .lowercase()
        }
        val indexByName = header.withIndex().associate { it.value to it.index }
        val requiredHeader = listOf("title", "source", "start_seconds", "end_seconds", "repetitions")
        if (!requiredHeader.all(indexByName::containsKey)) {
            return CsvImportResult(
                emptyList(),
                listOf("The CSV must include title, source, start_seconds, end_seconds, and repetitions."),
            )
        }

        val tasks = mutableListOf<PracticeTask>()
        val errors = mutableListOf<String>()
        records.drop(1).forEachIndexed { rowIndex, row ->
            if (row.all(String::isBlank)) return@forEachIndexed
            fun field(name: String): String = indexByName[name]?.let { row.getOrNull(it) }.orEmpty().trim()

            val lineNumber = rowIndex + 2
            val date = field("date").ifBlank { defaultDate }
            val title = field("title")
            val source = field("source")
            val declaredSourceType = field("reference_type")
                .takeIf(String::isNotBlank)
                ?.let { runCatching { SourceType.valueOf(it.uppercase()) }.getOrNull() }
            val sourceType = declaredSourceType ?: sourceTypeFor(source)
            val start = field("start_seconds").ifBlank { "0" }.toIntOrNull()
            val end = field("end_seconds").takeIf(String::isNotBlank)?.toIntOrNull()
            val repetitions = field("repetitions").toIntOrNull()

            val error = when {
                runCatching { LocalDate.parse(date) }.isFailure -> "invalid date"
                title.isBlank() -> "missing title"
                field("reference_type").isNotBlank() && declaredSourceType == null ->
                    "invalid reference_type"
                sourceType == null -> "source is not a supported YouTube or local content URI"
                sourceType == SourceType.YOUTUBE && youtubeVideoId(source) == null ->
                    "source is not a supported YouTube link"
                sourceType == SourceType.LOCAL &&
                    source.isNotBlank() &&
                    !source.startsWith("content://") ->
                    "local source must be reattached on this device"
                start == null || start < 0 -> "invalid start_seconds"
                field("end_seconds").isNotBlank() && end == null -> "invalid end_seconds"
                end != null && end <= start -> "end_seconds must be after start_seconds"
                repetitions == null || repetitions !in 1..20 -> "repetitions must be between 1 and 20"
                else -> null
            }
            if (error != null) {
                errors += "Line $lineNumber: $error."
            } else {
                tasks += PracticeTask(
                    id = idFactory(),
                    assignedDate = date,
                    title = title,
                    source = source,
                    sourceType = sourceType!!,
                    startSeconds = start!!,
                    endSeconds = end,
                    repetitions = repetitions!!,
                    coachNote = field("coach_note"),
                    whyImportant = field("why_important"),
                    sheetMusicUri = field("sheet_music_uri").takeIf(String::isNotBlank),
                )
                if (sourceType == SourceType.LOCAL && source.isBlank()) {
                    errors += "Line $lineNumber: reattach the local reference video on this device."
                }
                if (field("local_sheet_music_requires_reattachment").equals("yes", true)) {
                    errors += "Line $lineNumber: reattach the local sheet music on this device."
                }
            }
        }
        return CsvImportResult(tasks, errors)
    }

    private fun parseRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun finishField() {
            row += field.toString()
            field.setLength(0)
        }

        fun finishRow() {
            finishField()
            records += row
            row = mutableListOf()
        }

        while (index < text.length) {
            val char = text[index]
            when {
                inQuotes && char == '"' && text.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                !inQuotes && char == ',' -> finishField()
                !inQuotes && (char == '\n' || char == '\r') -> {
                    if (char == '\r' && text.getOrNull(index + 1) == '\n') index += 1
                    finishRow()
                }
                else -> field.append(char)
            }
            index += 1
        }
        require(!inQuotes) { "The CSV contains an unclosed quoted field." }
        if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
        return records
    }

    private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
