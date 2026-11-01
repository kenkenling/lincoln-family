package com.practicepartner.music

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PracticeLoadResult(
    val snapshot: PracticeSnapshot,
    val warning: String? = null,
)

class PracticeStore(context: Context) {
    private val prefs = context.getSharedPreferences("practice_partner_v2", Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences("practice_partner_v1", Context.MODE_PRIVATE)

    fun load(
        profile: ChildProfile,
        currentDate: String = currentDateKey(),
    ): PracticeSnapshot = loadWithStatus(profile, currentDate).snapshot

    fun loadWithStatus(
        profile: ChildProfile,
        currentDate: String = currentDateKey(),
    ): PracticeLoadResult {
        val key = snapshotKey(profile.id)
        val backupKey = snapshotBackupKey(profile.id)
        val stored = prefs.getString(key, null)
        if (stored != null) {
            decodeStoredSnapshot(stored, profile)?.let { decoded ->
                ensureLegacySnapshotOwner(profile.id)
                return PracticeLoadResult(
                    snapshot = decoded.snapshot,
                    warning = skippedRecordWarning(decoded.skippedRecords),
                )
            }

            val backup = prefs.getString(backupKey, null)
            val recovered = backup?.let { decodeStoredSnapshot(it, profile) }
            if (recovered != null) {
                prefs.edit(commit = true) {
                    putString(key, backup)
                    putString(LEGACY_SNAPSHOT_OWNER_KEY, profile.id)
                }
                return PracticeLoadResult(
                    snapshot = recovered.snapshot,
                    warning = buildString {
                        append("Practice data was recovered from the last local backup.")
                        skippedRecordWarning(recovered.skippedRecords)?.let {
                            append(' ')
                            append(it)
                        }
                    },
                )
            }

            return PracticeLoadResult(
                snapshot = PracticeSnapshot(),
                warning = "The local practice file could not be read and no valid local backup was available.",
            )
        }
        val upgraded = previousSnapshotKeys(profile.id)
            .asSequence()
            .mapNotNull { previousKey -> prefs.getString(previousKey, null) }
            .mapNotNull { value -> decodeStoredSnapshot(value, profile) }
            .firstOrNull()
        if (upgraded != null) {
            val encoded = encodeSnapshot(upgraded.snapshot).toString()
            prefs.edit(commit = true) {
                putString(key, encoded)
                putString(LEGACY_SNAPSHOT_OWNER_KEY, profile.id)
            }
            return PracticeLoadResult(
                snapshot = upgraded.snapshot,
                warning = skippedRecordWarning(upgraded.skippedRecords),
            )
        }
        val migrated = claimLegacySnapshot(profile, currentDate)
        val encoded = encodeSnapshot(migrated.snapshot.scopedTo(profile)).toString()
        prefs.edit(commit = true) {
            putString(key, encoded)
            putString(LEGACY_SNAPSHOT_OWNER_KEY, profile.id)
        }
        return migrated
    }

    fun save(profile: ChildProfile, snapshot: PracticeSnapshot) {
        val key = snapshotKey(profile.id)
        val backupKey = snapshotBackupKey(profile.id)
        val encoded = encodeSnapshot(snapshot.scopedTo(profile)).toString()
        val previous = prefs.getString(key, null)
            ?.takeIf { decodeStoredSnapshot(it, profile) != null }
        prefs.edit(commit = true) {
            if (previous != null) {
                putString(backupKey, previous)
            }
            putString(key, encoded)
        }
    }

    fun delete(profile: ChildProfile) {
        val ownsLegacySnapshot =
            prefs.getString(LEGACY_SNAPSHOT_OWNER_KEY, null) == profile.id
        prefs.edit(commit = true) {
            remove(snapshotKey(profile.id))
            remove(snapshotBackupKey(profile.id))
            previousSnapshotKeys(profile.id).forEach(::remove)
            if (ownsLegacySnapshot) {
                remove("snapshot")
                remove("snapshot_backup")
                remove(LEGACY_SNAPSHOT_OWNER_KEY)
            }
        }
        if (ownsLegacySnapshot) {
            legacyPrefs.edit(commit = true) {
                clear()
            }
        }
    }

    fun referencedRecordingPaths(profiles: List<ChildProfile>): Set<String> =
        profiles.flatMap { profile ->
            load(profile).events.mapNotNull(PracticeEvent::videoPath)
        }.toSet()

    private fun claimLegacySnapshot(
        profile: ChildProfile,
        currentDate: String,
    ): PracticeLoadResult {
        if (prefs.contains(LEGACY_SNAPSHOT_OWNER_KEY)) {
            return PracticeLoadResult(PracticeSnapshot())
        }

        val primary = prefs.getString("snapshot", null)
            ?.let { decodeStoredSnapshot(it, profile) }
        val backup = prefs.getString("snapshot_backup", null)
            ?.let { decodeStoredSnapshot(it, profile) }
        val migrated = when {
            primary != null -> PracticeLoadResult(
                snapshot = primary.snapshot,
                warning = skippedRecordWarning(primary.skippedRecords),
            )
            backup != null -> PracticeLoadResult(
                snapshot = backup.snapshot,
                warning = "Practice data was recovered from the previous single-student backup.",
            )
            else -> PracticeLoadResult(migrateLegacy(currentDate, profile))
        }
        return migrated
    }

    private fun ensureLegacySnapshotOwner(childProfileId: String) {
        if (prefs.contains(LEGACY_SNAPSHOT_OWNER_KEY)) return
        prefs.edit(commit = true) {
            putString(LEGACY_SNAPSHOT_OWNER_KEY, childProfileId)
        }
    }

    private fun migrateLegacy(
        currentDate: String,
        profile: ChildProfile,
    ): PracticeSnapshot {
        val tasks = runCatching {
            val rows = JSONArray(legacyPrefs.getString("tasks", "[]"))
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                PracticeTask(
                    id = row.optString("id").ifBlank { UUID.randomUUID().toString() },
                    assignedDate = currentDate,
                    title = row.optString("title"),
                    source = row.optString("source"),
                    sourceType = runCatching {
                        SourceType.valueOf(row.optString("sourceType", "youtube").uppercase())
                    }.getOrElse {
                        sourceTypeFor(row.optString("source")) ?: SourceType.NONE
                    },
                    startSeconds = row.optInt("startSeconds", 0),
                    endSeconds = row.optIntOrNull("endSeconds"),
                    repetitions = row.optInt("repetitions", 1).coerceIn(1, 20),
                    childProfileId = profile.id,
                    instrument = profile.instrument,
                )
            }
        }.getOrDefault(emptyList())

        val completed = legacyPrefs.getStringSet("completedIds", emptySet())?.toSet().orEmpty()
        val taskIds = tasks.mapTo(mutableSetOf()) { it.id }
        val progress = DailyProgress(
            date = currentDate,
            childProfileId = profile.id,
            completedTaskIds = completed.intersect(taskIds),
            repetitionsByTask = tasks.associate { task ->
                task.id to if (task.id in completed) task.repetitions else 0
            },
            elapsedSeconds = legacyPrefs.getLong("elapsedSeconds", 0L).coerceAtLeast(0L),
        )
        val events = runCatching {
            val rows = JSONArray(legacyPrefs.getString("events", "[]"))
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val atMillis = row.optLong("atMillis", System.currentTimeMillis())
                val videoPath = row.optNullableString("videoPath")
                PracticeEvent(
                    id = UUID.randomUUID().toString(),
                    date = dateKeyForMillis(atMillis),
                    taskId = null,
                    taskTitle = row.optString("taskTitle", "Practice session"),
                    event = row.optString("event"),
                    atMillis = atMillis,
                    videoUri = row.optNullableString("videoUri"),
                    videoPath = videoPath,
                    videoFileName = recordingFileNameFromPath(videoPath),
                    childProfileId = profile.id,
                )
            }
        }.getOrDefault(emptyList())
        return PracticeSnapshot(
            tasks = tasks,
            progressByDate = if (tasks.isEmpty() && progress.elapsedSeconds == 0L) {
                emptyMap()
            } else {
                mapOf(currentDate to progress)
            },
            events = events.takeLast(500),
        )
    }

    private fun encodeSnapshot(snapshot: PracticeSnapshot): JSONObject = JSONObject().apply {
        put("version", 7)
        put("tasks", JSONArray().apply {
            snapshot.tasks.forEach { task ->
                put(JSONObject().apply {
                    put("id", task.id)
                    put("childProfileId", task.childProfileId)
                    put("instrument", task.instrument)
                    put("assignedDate", task.assignedDate)
                    put("title", task.title)
                    put("source", task.source)
                    put("sourceType", task.sourceType.name)
                    put("startSeconds", task.startSeconds)
                    put("endSeconds", task.endSeconds ?: JSONObject.NULL)
                    put("repetitions", task.repetitions)
                    put("coachNote", task.coachNote)
                    put("whyImportant", task.whyImportant)
                    put("sheetMusicUri", task.sheetMusicUri ?: JSONObject.NULL)
                    put("assignmentType", task.assignmentType.name)
                    put("goalType", task.goalType.name)
                    put("targetSeconds", task.targetSeconds)
                    put("enabledModes", JSONArray(task.enabledModes.map(PracticeMode::name)))
                    put("revision", task.revision)
                    put("archivedAtMillis", task.archivedAtMillis ?: JSONObject.NULL)
                })
            }
        })
        put("progress", JSONArray().apply {
            snapshot.progressByDate.values.sortedBy { it.date }.forEach { progress ->
                put(JSONObject().apply {
                    put("date", progress.date)
                    put("childProfileId", progress.childProfileId)
                    put("completedTaskIds", JSONArray(progress.completedTaskIds.toList()))
                    put("repetitionsByTask", JSONObject(progress.repetitionsByTask))
                    put("elapsedSeconds", progress.elapsedSeconds)
                })
            }
        })
        put("events", JSONArray().apply {
            snapshot.events.takeLast(500).forEach { event ->
                put(JSONObject().apply {
                    put("id", event.id)
                    put("date", event.date)
                    put("childProfileId", event.childProfileId)
                    put("sessionId", event.sessionId ?: JSONObject.NULL)
                    put("taskId", event.taskId ?: JSONObject.NULL)
                    put("taskTitle", event.taskTitle)
                    put("event", event.event)
                    put("atMillis", event.atMillis)
                    put("videoUri", event.videoUri ?: JSONObject.NULL)
                    put("videoPath", event.videoPath ?: JSONObject.NULL)
                    put("videoFileName", event.videoFileName ?: JSONObject.NULL)
                })
            }
        })
        put("sessions", JSONArray().apply {
            snapshot.sessions.takeLast(MAX_SESSIONS).forEach { session ->
                put(JSONObject().apply {
                    put("id", session.id)
                    put("childProfileId", session.childProfileId)
                    put("assignmentId", session.assignmentId)
                    put("assignmentTitle", session.assignmentTitle)
                    put("instrument", session.instrument)
                    put("date", session.date)
                    put("mode", session.mode.name)
                    put("startedAtMillis", session.startedAtMillis)
                    put("endedAtMillis", session.endedAtMillis ?: JSONObject.NULL)
                    put("repetitionsCompleted", session.repetitionsCompleted)
                    put("status", session.status.name)
                    put("assignmentRevision", session.assignmentRevision)
                    put("assignmentType", session.assignmentType.name)
                    put("goalType", session.goalType.name)
                    put("targetRepetitions", session.targetRepetitions)
                    put("targetSeconds", session.targetSeconds)
                    put("elapsedSeconds", session.elapsedSeconds)
                    put("reflection", session.reflection?.name ?: JSONObject.NULL)
                })
            }
        })
    }

    private fun decodeStoredSnapshot(
        value: String,
        profile: ChildProfile,
    ): DecodedPracticeSnapshot? =
        runCatching { decodeSnapshot(JSONObject(value), profile) }.getOrNull()

    private fun decodeSnapshot(
        root: JSONObject,
        profile: ChildProfile,
    ): DecodedPracticeSnapshot {
        require(
            root.has("tasks") &&
                root.has("progress") &&
                root.has("events"),
        ) {
            "Practice snapshot is missing required sections."
        }
        var skippedRecords = 0
        val taskRows = root.optJSONArray("tasks") ?: JSONArray()
        val tasks = (0 until taskRows.length()).mapNotNull { index ->
            runCatching {
                val row = taskRows.getJSONObject(index)
                val id = row.getString("id").trim()
                val title = row.getString("title").trim()
                val source = row.optString("source").trim()
                require(id.isNotBlank() && title.isNotBlank())
                val sourceType = runCatching { SourceType.valueOf(row.optString("sourceType")) }
                    .getOrElse { sourceTypeFor(source) ?: error("Unsupported source") }
                val modeRows = row.optJSONArray("enabledModes")
                val enabledModes = modeRows?.let { modes ->
                    (0 until modes.length()).mapNotNullTo(linkedSetOf()) { modeIndex ->
                        runCatching {
                            PracticeMode.valueOf(modes.getString(modeIndex))
                        }.getOrNull()
                    }
                }.orEmpty().ifEmpty {
                    if (sourceType == SourceType.NONE) {
                        setOf(PracticeMode.PLAY_YOURSELF)
                    } else {
                        PracticeMode.entries.toSet()
                    }
                }
                PracticeTask(
                    id = id,
                    childProfileId = row.optString("childProfileId").ifBlank { profile.id },
                    instrument = row.optString("instrument").ifBlank { profile.instrument },
                    assignedDate = row.optString("assignedDate", currentDateKey()),
                    title = title,
                    source = source,
                    sourceType = sourceType,
                    startSeconds = row.optInt("startSeconds", 0).coerceAtLeast(0),
                    endSeconds = row.optIntOrNull("endSeconds"),
                    repetitions = row.optInt("repetitions", 1).coerceIn(1, 20),
                    coachNote = row.optString("coachNote"),
                    whyImportant = row.optString("whyImportant"),
                    sheetMusicUri = row.optNullableString("sheetMusicUri"),
                    assignmentType = runCatching {
                        AssignmentType.valueOf(row.optString("assignmentType"))
                    }.getOrDefault(AssignmentType.TECHNIQUE),
                    goalType = runCatching {
                        PracticeGoalType.valueOf(row.optString("goalType"))
                    }.getOrDefault(PracticeGoalType.REPETITIONS),
                    targetSeconds = row.optInt("targetSeconds", 0).coerceAtLeast(0),
                    enabledModes = enabledModes,
                    revision = row.optInt("revision", 1).coerceAtLeast(1),
                    archivedAtMillis = row.optLongOrNull("archivedAtMillis"),
                )
            }.getOrElse {
                skippedRecords += 1
                null
            }
        }

        val progressRows = root.optJSONArray("progress") ?: JSONArray()
        val progress = (0 until progressRows.length()).mapNotNull { index ->
            runCatching {
                val row = progressRows.getJSONObject(index)
                val date = row.getString("date")
                val completedRows = row.optJSONArray("completedTaskIds") ?: JSONArray()
                val completed = (0 until completedRows.length()).mapTo(mutableSetOf()) {
                    completedRows.getString(it)
                }
                val repetitionsObject = row.optJSONObject("repetitionsByTask") ?: JSONObject()
                val repetitions = buildMap {
                    repetitionsObject.keys().forEach { taskId ->
                        put(taskId, repetitionsObject.optInt(taskId, 0).coerceAtLeast(0))
                    }
                }
                date to DailyProgress(
                    date = date,
                    childProfileId = row.optString("childProfileId").ifBlank { profile.id },
                    completedTaskIds = completed,
                    repetitionsByTask = repetitions,
                    elapsedSeconds = row.optLong("elapsedSeconds", 0L).coerceAtLeast(0L),
                )
            }.getOrElse {
                skippedRecords += 1
                null
            }
        }.toMap()

        val eventRows = root.optJSONArray("events") ?: JSONArray()
        val events = (0 until eventRows.length()).mapNotNull { index ->
            runCatching {
                val row = eventRows.getJSONObject(index)
                val atMillis = row.optLong("atMillis", System.currentTimeMillis())
                val videoPath = row.optNullableString("videoPath")
                PracticeEvent(
                    id = row.optString("id").ifBlank { UUID.randomUUID().toString() },
                    date = row.optString("date").ifBlank { dateKeyForMillis(atMillis) },
                    taskId = row.optNullableString("taskId"),
                    taskTitle = row.optString("taskTitle", "Practice session"),
                    event = row.optString("event"),
                    atMillis = atMillis,
                    videoUri = row.optNullableString("videoUri"),
                    videoPath = videoPath,
                    videoFileName = row.optNullableString("videoFileName")
                        ?: recordingFileNameFromPath(videoPath),
                    childProfileId = row.optString("childProfileId").ifBlank { profile.id },
                    sessionId = row.optNullableString("sessionId"),
                )
            }.getOrElse {
                skippedRecords += 1
                null
            }
        }
        val sessionRows = root.optJSONArray("sessions") ?: JSONArray()
        val taskById = tasks.associateBy(PracticeTask::id)
        val sessions = (0 until sessionRows.length()).mapNotNull { index ->
            runCatching {
                val row = sessionRows.getJSONObject(index)
                val endedAtMillis = row.optLongOrNull("endedAtMillis")
                val assignmentId = row.getString("assignmentId")
                val task = taskById[assignmentId]
                PracticeSession(
                    id = row.getString("id").also { require(it.isNotBlank()) },
                    childProfileId = row.optString("childProfileId").ifBlank { profile.id },
                    assignmentId = assignmentId,
                    assignmentTitle = row.optString("assignmentTitle", "Practice session"),
                    instrument = row.optString("instrument").ifBlank { profile.instrument },
                    date = row.getString("date"),
                    mode = runCatching {
                        PracticeMode.valueOf(row.optString("mode"))
                    }.getOrDefault(PracticeMode.PLAY_YOURSELF),
                    startedAtMillis = row.getLong("startedAtMillis"),
                    endedAtMillis = endedAtMillis,
                    repetitionsCompleted =
                        row.optInt("repetitionsCompleted", 0).coerceAtLeast(0),
                    status = runCatching {
                        PracticeSessionStatus.valueOf(row.optString("status"))
                    }.getOrDefault(
                        if (endedAtMillis == null) {
                            PracticeSessionStatus.ACTIVE
                        } else {
                            PracticeSessionStatus.COMPLETED
                        },
                    ),
                    assignmentRevision =
                        row.optInt("assignmentRevision", task?.revision ?: 1).coerceAtLeast(1),
                    assignmentType = runCatching {
                        AssignmentType.valueOf(row.optString("assignmentType"))
                    }.getOrDefault(task?.assignmentType ?: AssignmentType.TECHNIQUE),
                    goalType = runCatching {
                        PracticeGoalType.valueOf(row.optString("goalType"))
                    }.getOrDefault(task?.goalType ?: PracticeGoalType.REPETITIONS),
                    targetRepetitions =
                        row.optInt("targetRepetitions", task?.repetitions ?: 1)
                            .coerceAtLeast(1),
                    targetSeconds =
                        row.optInt("targetSeconds", task?.targetSeconds ?: 0).coerceAtLeast(0),
                    elapsedSeconds = row.optLong("elapsedSeconds", 0L).coerceAtLeast(0L),
                    reflection = row.optNullableString("reflection")?.let { value ->
                        runCatching { PracticeReflection.valueOf(value) }.getOrNull()
                    },
                )
            }.getOrElse {
                skippedRecords += 1
                null
            }
        }
        return DecodedPracticeSnapshot(
            snapshot = PracticeSnapshot(
                tasks = tasks,
                progressByDate = progress,
                events = events.takeLast(500),
                sessions = sessions.takeLast(MAX_SESSIONS),
            ).scopedTo(profile),
            skippedRecords = skippedRecords,
        )
    }

    private fun snapshotKey(childProfileId: String): String =
        "snapshot_v7:$childProfileId"

    private fun snapshotBackupKey(childProfileId: String): String =
        "snapshot_v7_backup:$childProfileId"

    private fun previousSnapshotKeys(childProfileId: String): List<String> = listOf(
        "snapshot_v6:$childProfileId",
        "snapshot_v6_backup:$childProfileId",
        "snapshot_v5:$childProfileId",
        "snapshot_v5_backup:$childProfileId",
    )
}

private const val LEGACY_SNAPSHOT_OWNER_KEY = "legacy_snapshot_owner_child_id"
private const val MAX_SESSIONS = 500

private data class DecodedPracticeSnapshot(
    val snapshot: PracticeSnapshot,
    val skippedRecords: Int,
)

private fun skippedRecordWarning(skippedRecords: Int): String? =
    skippedRecords.takeIf { it > 0 }?.let {
        "Recovered the readable data and skipped $it damaged record${if (it == 1) "" else "s"}."
    }
