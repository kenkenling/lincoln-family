package com.practicepartner.music

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ProfileArchiveResult(
    val recordingCount: Int,
    val skippedRecordingCount: Int,
)

object ProfileArchiveWriter {
    fun fileName(profile: ChildProfile, date: DateKey = currentDateKey()): String {
        val student = safeArchiveName(profile.name, "Student")
        return "LK Practice - $student - data - ${date.value}.zip"
    }

    fun write(
        output: OutputStream,
        profile: ChildProfile,
        snapshot: PracticeSnapshot,
        recordingFiles: List<File>,
        exportedAtMillis: Long,
    ): ProfileArchiveResult {
        var recordingCount = 0
        var skippedRecordingCount = 0
        val usedRecordingNames = mutableSetOf<String>()
        ZipOutputStream(output.buffered()).use { zip ->
            zip.writeTextEntry(
                "README.txt",
                archiveReadme(profile, exportedAtMillis),
            )
            zip.writeTextEntry(
                "profile.json",
                profileJson(profile, exportedAtMillis).toString(2),
            )
            zip.writeTextEntry(
                "assignments.csv",
                CsvCodec.encode(snapshot.tasks),
            )
            zip.writeTextEntry(
                "practice-history.json",
                historyJson(profile, snapshot, exportedAtMillis).toString(2),
            )

            recordingFiles.distinctBy { it.absolutePath }.forEach { file ->
                if (!file.isFile || !file.canRead()) {
                    skippedRecordingCount += 1
                    return@forEach
                }
                val baseName = safeArchiveName(file.name, "practice-recording.mp4")
                val entryName = uniqueRecordingEntryName(baseName, usedRecordingNames)
                zip.putNextEntry(ZipEntry("recordings/$entryName"))
                file.inputStream().buffered().use { input ->
                    input.copyTo(zip)
                }
                zip.closeEntry()
                recordingCount += 1
            }
        }
        return ProfileArchiveResult(
            recordingCount = recordingCount,
            skippedRecordingCount = skippedRecordingCount,
        )
    }

    private fun profileJson(
        profile: ChildProfile,
        exportedAtMillis: Long,
    ): JSONObject = JSONObject().apply {
        put("archiveVersion", 1)
        put("exportedAtUtc", Instant.ofEpochMilli(exportedAtMillis).toString())
        put("studentId", profile.id)
        put("studentName", profile.name)
        put("instrument", profile.instrument)
    }

    private fun historyJson(
        profile: ChildProfile,
        snapshot: PracticeSnapshot,
        exportedAtMillis: Long,
    ): JSONObject = JSONObject().apply {
        put("archiveVersion", 1)
        put("exportedAtUtc", Instant.ofEpochMilli(exportedAtMillis).toString())
        put("studentId", profile.id)
        put("dailyProgress", JSONArray().apply {
            snapshot.progressByDate.values.sortedBy(DailyProgress::date).forEach { progress ->
                put(JSONObject().apply {
                    put("date", progress.date.value)
                    put("elapsedSeconds", progress.elapsedSeconds)
                    put("completedAssignmentIds", JSONArray(progress.completedTaskIds.toList()))
                    put("repetitionsByAssignment", JSONObject(progress.repetitionsByTask))
                })
            }
        })
        put("sessions", JSONArray().apply {
            snapshot.sessions.sortedBy(PracticeSession::startedAtMillis).forEach { session ->
                put(JSONObject().apply {
                    put("id", session.id)
                    put("date", session.date.value)
                    put("assignmentId", session.assignmentId)
                    put("assignmentRevision", session.assignmentRevision)
                    put("assignmentTitle", session.assignmentTitle)
                    put("assignmentType", session.assignmentType.name)
                    put("instrument", session.instrument)
                    put("goalType", session.goalType.name)
                    put("targetRepetitions", session.targetRepetitions)
                    put("targetSeconds", session.targetSeconds)
                    put("mode", session.mode.name)
                    put(
                        "modesUsed",
                        JSONArray(session.orderedModesUsed().map(PracticeMode::name)),
                    )
                    put("startedAtUtc", Instant.ofEpochMilli(session.startedAtMillis).toString())
                    put(
                        "endedAtUtc",
                        session.endedAtMillis?.let {
                            Instant.ofEpochMilli(it).toString()
                        } ?: JSONObject.NULL,
                    )
                    put("elapsedSeconds", session.elapsedSeconds)
                    put("repetitionsCompleted", session.repetitionsCompleted)
                    put("status", session.status.name)
                    put("reflection", session.reflection?.name ?: JSONObject.NULL)
                    put("masteryStatus", session.masteryStatus.name)
                })
            }
        })
        put("events", JSONArray().apply {
            snapshot.events.sortedBy(PracticeEvent::atMillis).forEach { event ->
                put(JSONObject().apply {
                    put("id", event.id)
                    put("date", event.date.value)
                    put("sessionId", event.sessionId ?: JSONObject.NULL)
                    put("assignmentId", event.taskId ?: JSONObject.NULL)
                    put("assignmentTitle", event.taskTitle)
                    put("event", event.event)
                    put("timestampUtc", Instant.ofEpochMilli(event.atMillis).toString())
                    put("recordingFilename", event.videoFileName ?: JSONObject.NULL)
                })
            }
        })
    }

    private fun archiveReadme(
        profile: ChildProfile,
        exportedAtMillis: Long,
    ): String = """
        LK Practice student data archive

        Student: ${profile.name}
        Instrument: ${profile.instrument}
        Exported UTC: ${Instant.ofEpochMilli(exportedAtMillis)}

        Contents:
        - profile.json: student profile metadata
        - assignments.csv: portable assignment plan
        - practice-history.json: progress, sessions, reflections, and event metadata
        - recordings/: app-managed practice videos that were available at export time

        Private Android content URIs, absolute device paths, and external sheet-music files
        are not written into this archive. This release does not import a full archive back
        into the app; keep it as a readable family backup.
    """.trimIndent()

    private fun ZipOutputStream.writeTextEntry(name: String, value: String) {
        putNextEntry(ZipEntry(name))
        write(value.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun uniqueRecordingEntryName(
        baseName: String,
        usedNames: MutableSet<String>,
    ): String {
        if (usedNames.add(baseName)) return baseName
        val extension = baseName.substringAfterLast('.', missingDelimiterValue = "")
        val stem = baseName.removeSuffix(if (extension.isBlank()) "" else ".$extension")
        var suffix = 2
        while (true) {
            val candidate = if (extension.isBlank()) {
                "$stem-$suffix"
            } else {
                "$stem-$suffix.$extension"
            }
            if (usedNames.add(candidate)) return candidate
            suffix += 1
        }
    }

    private fun safeArchiveName(value: String, fallback: String): String {
        val cleaned = buildString(value.length) {
            value.forEach { character ->
                append(
                    when {
                        character.code < 32 || character.code == 127 -> ' '
                        character in """\/:*?"<>|""" -> '-'
                        else -> character
                    },
                )
            }
        }.replace(Regex("\\s+"), " ")
            .trim { it == ' ' || it == '.' || it == '-' }
            .take(120)
            .trim { it == ' ' || it == '.' || it == '-' }
        return cleaned.ifBlank { fallback }
    }
}
