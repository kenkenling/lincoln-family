package com.practicepartner.music

import java.io.File
import java.time.LocalDate

private val currentRecordingName = Regex(
    """^(\d{4}-\d{2}-\d{2} - .+ - .+ - Take )(\d+)(\.mp4)$""",
)
private val legacyRecordingName = Regex("""^PracticePartner-\d+\.mp4$""")
private val whitespaceAroundDash = Regex("""\s*-\s*""")
private val repeatedWhitespace = Regex("""\s+""")
private val repeatedDashes = Regex("""-{2,}""")

fun practiceRecordingFileName(
    practiceDate: String,
    studentName: String,
    assignmentTitle: String,
    takeNumber: Int,
): String {
    val date = LocalDate.parse(practiceDate).toString()
    val student = safeRecordingNamePart(studentName, fallback = "Student", maxLength = 48)
    val assignment = safeRecordingNamePart(
        assignmentTitle,
        fallback = "Practice",
        maxLength = 96,
    )
    val take = takeNumber.coerceAtLeast(1).toString().padStart(2, '0')
    return "$date - $student - $assignment - Take $take.mp4"
}

fun nextRecordingTakeNumber(
    events: List<PracticeEvent>,
    practiceDate: String,
    taskId: String,
): Int {
    val recordings = events.filter { event ->
        event.date == practiceDate &&
            event.taskId == taskId &&
            (event.videoUri != null || event.videoPath != null || event.videoFileName != null)
    }
    val highestNamedTake = recordings
        .mapNotNull { event ->
            event.videoFileName
                ?.let(currentRecordingName::matchEntire)
                ?.groupValues
                ?.get(2)
                ?.toIntOrNull()
        }
        .maxOrNull()
        ?: 0
    return maxOf(recordings.size, highestNamedTake) + 1
}

fun nextAvailableRecordingFile(directory: File, requestedFileName: String): File {
    var candidate = File(directory, requestedFileName)
    if (!candidate.exists()) return candidate

    val match = currentRecordingName.matchEntire(requestedFileName)
        ?: return File(directory, "PracticePartner-${System.currentTimeMillis()}.mp4")
    val prefix = match.groupValues[1]
    val extension = match.groupValues[3]
    var takeNumber = match.groupValues[2].toIntOrNull()?.plus(1) ?: 2
    while (candidate.exists()) {
        candidate = File(
            directory,
            "$prefix${takeNumber.toString().padStart(2, '0')}$extension",
        )
        takeNumber += 1
    }
    return candidate
}

fun isManagedPracticeRecordingFileName(fileName: String): Boolean =
    currentRecordingName.matches(fileName) || legacyRecordingName.matches(fileName)

fun recordingFileNameFromPath(path: String?): String? =
    path
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
        ?.name
        ?.takeIf(String::isNotBlank)

private fun safeRecordingNamePart(
    value: String,
    fallback: String,
    maxLength: Int,
): String {
    val replaced = buildString(value.length) {
        value.forEach { character ->
            append(
                when {
                    character.code < 32 || character.code == 127 -> ' '
                    character in """\/:*?"<>|""" -> '-'
                    else -> character
                },
            )
        }
    }
    val cleaned = replaced
        .replace(whitespaceAroundDash, "-")
        .replace(repeatedWhitespace, " ")
        .replace(repeatedDashes, "-")
        .trim { it == ' ' || it == '.' || it == '-' }
        .take(maxLength)
        .trim { it == ' ' || it == '.' || it == '-' }
    return cleaned.ifBlank { fallback }
}
