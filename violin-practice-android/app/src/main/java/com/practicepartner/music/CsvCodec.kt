package com.practicepartner.music

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CsvImportResult(
    val tasks: List<PracticeTask>,
    val errors: List<String>,
)

object CsvCodec {
    private val headers = listOf(
        "assignment_id",
        "student_id",
        "instrument",
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
        "assignment_type",
        "goal_type",
        "target_minutes",
        "practice_steps",
        "revision",
        "archived_at_utc",
    )

    fun encode(tasks: List<PracticeTask>): String = buildString {
        appendLine(headers.joinToString(","))
        tasks.forEach { task ->
            appendLine(
                listOf(
                    task.id,
                    task.childProfileId,
                    task.instrument,
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
                    task.assignmentType.name,
                    task.goalType.name,
                    if (task.goalType == PracticeGoalType.TIME) {
                        (task.targetSeconds / 60).toString()
                    } else {
                        ""
                    },
                    PracticeMode.entries
                        .filter(task.enabledModes::contains)
                        .joinToString("|", transform = PracticeMode::name),
                    task.revision.toString(),
                    task.archivedAtMillis?.let {
                        Instant.ofEpochMilli(it).toString()
                    }.orEmpty(),
                ).joinToString(",") { csvCell(it) },
            )
        }
    }

    fun decode(
        text: String,
        defaultDate: String,
        childProfileId: String = "",
        instrument: String = "",
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
            val id = field("assignment_id").ifBlank(idFactory)
            val rowChildProfileId = field("student_id").ifBlank { childProfileId }
            val rowInstrument = field("instrument").ifBlank { instrument }
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
            val declaredAssignmentType = field("assignment_type")
                .takeIf(String::isNotBlank)
                ?.let { runCatching { AssignmentType.valueOf(it.uppercase()) }.getOrNull() }
            val assignmentType = declaredAssignmentType ?: AssignmentType.TECHNIQUE
            val declaredGoalType = field("goal_type")
                .takeIf(String::isNotBlank)
                ?.let { runCatching { PracticeGoalType.valueOf(it.uppercase()) }.getOrNull() }
            val goalType = declaredGoalType ?: PracticeGoalType.REPETITIONS
            val targetMinutes = field("target_minutes")
                .takeIf(String::isNotBlank)
                ?.toIntOrNull()
            val practiceStepsText = field("practice_steps")
            val enabledModes = if (practiceStepsText.isBlank()) {
                if (sourceType == SourceType.NONE) {
                    setOf(PracticeMode.PLAY_YOURSELF)
                } else {
                    PracticeMode.entries.toSet()
                }
            } else {
                practiceStepsText
                    .split('|', ',', ';')
                    .mapNotNullTo(linkedSetOf()) { value ->
                        runCatching {
                            PracticeMode.valueOf(value.trim().uppercase())
                        }.getOrNull()
                    }
            }
            val revision = field("revision").ifBlank { "1" }.toIntOrNull()
            val archivedAtText = field("archived_at_utc")
            val archivedAtMillis = archivedAtText
                .takeIf(String::isNotBlank)
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

            val error = when {
                runCatching { LocalDate.parse(date) }.isFailure -> "invalid date"
                title.isBlank() -> "missing title"
                childProfileId.isNotBlank() &&
                    rowChildProfileId.isNotBlank() &&
                    rowChildProfileId != childProfileId -> "student_id belongs to another profile"
                field("reference_type").isNotBlank() && declaredSourceType == null ->
                    "invalid reference_type"
                sourceType == null -> "source is not a supported YouTube or local content URI"
                sourceType == SourceType.NONE && source.isNotBlank() ->
                    "reference_type NONE requires an empty source"
                sourceType == SourceType.YOUTUBE && youtubeVideoId(source) == null ->
                    "source is not a supported YouTube link"
                sourceType == SourceType.LOCAL &&
                    source.isNotBlank() &&
                    !source.startsWith("content://") ->
                    "local source must be reattached on this device"
                start == null || start < 0 -> "invalid start_seconds"
                field("end_seconds").isNotBlank() && end == null -> "invalid end_seconds"
                end != null && end <= start -> "end_seconds must be after start_seconds"
                goalType == PracticeGoalType.REPETITIONS &&
                    (repetitions == null || repetitions !in 1..20) ->
                    "repetitions must be between 1 and 20"
                field("assignment_type").isNotBlank() && declaredAssignmentType == null ->
                    "invalid assignment_type"
                field("goal_type").isNotBlank() && declaredGoalType == null ->
                    "invalid goal_type"
                goalType == PracticeGoalType.TIME &&
                    (targetMinutes == null || targetMinutes !in 1..60) ->
                    "target_minutes must be between 1 and 60"
                practiceStepsText.isNotBlank() && enabledModes.isEmpty() ->
                    "practice_steps are invalid"
                sourceType == SourceType.NONE &&
                    enabledModes != setOf(PracticeMode.PLAY_YOURSELF) ->
                    "assignments without a reference must use PLAY_YOURSELF"
                revision == null || revision < 1 -> "revision must be one or greater"
                archivedAtText.isNotBlank() && archivedAtMillis == null ->
                    "archived_at_utc must be an ISO-8601 timestamp"
                else -> null
            }
            if (error != null) {
                errors += "Line $lineNumber: $error."
            } else {
                tasks += PracticeTask(
                    id = id,
                    childProfileId = rowChildProfileId,
                    instrument = rowInstrument,
                    assignedDate = date,
                    title = title,
                    source = source,
                    sourceType = sourceType!!,
                    startSeconds = start!!,
                    endSeconds = end,
                    repetitions = if (goalType == PracticeGoalType.REPETITIONS) {
                        repetitions!!
                    } else {
                        1
                    },
                    coachNote = field("coach_note"),
                    whyImportant = field("why_important"),
                    sheetMusicUri = field("sheet_music_uri").takeIf(String::isNotBlank),
                    assignmentType = assignmentType,
                    goalType = goalType,
                    targetSeconds = if (goalType == PracticeGoalType.TIME) {
                        targetMinutes!! * 60
                    } else {
                        0
                    },
                    enabledModes = enabledModes,
                    revision = revision!!,
                    archivedAtMillis = archivedAtMillis,
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
