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

    fun load(currentDate: String = currentDateKey()): PracticeSnapshot {
        return loadWithStatus(currentDate).snapshot
    }

    fun loadWithStatus(currentDate: String = currentDateKey()): PracticeLoadResult {
        val stored = prefs.getString("snapshot", null)
        if (stored != null) {
            decodeStoredSnapshot(stored)?.let { decoded ->
                return PracticeLoadResult(
                    snapshot = decoded.snapshot,
                    warning = skippedRecordWarning(decoded.skippedRecords),
                )
            }

            val backup = prefs.getString("snapshot_backup", null)
            val recovered = backup?.let(::decodeStoredSnapshot)
            if (recovered != null) {
                prefs.edit(commit = true) {
                    putString("snapshot", backup)
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
        val migrated = migrateLegacy(currentDate)
        save(migrated)
        return PracticeLoadResult(migrated)
    }

    fun save(snapshot: PracticeSnapshot) {
        val encoded = encodeSnapshot(snapshot).toString()
        val previous = prefs.getString("snapshot", null)
            ?.takeIf { decodeStoredSnapshot(it) != null }
        prefs.edit(commit = true) {
            if (previous != null) {
                putString("snapshot_backup", previous)
            }
            putString("snapshot", encoded)
        }
    }

    fun loadLocalRevision(): Long = prefs.getLong("local_data_revision", 0L).coerceAtLeast(0L)

    fun bumpLocalRevision(): Long {
        val next = loadLocalRevision() + 1L
        prefs.edit(commit = true) { putLong("local_data_revision", next) }
        return next
    }

    fun loadLastFileSaveRevision(): Long =
        prefs.getLong("last_local_file_revision", -1L).coerceAtLeast(-1L)

    fun saveLastFileSaveRevision(revision: Long) {
        prefs.edit(commit = true) {
            putLong("last_local_file_revision", revision.coerceAtLeast(0L))
        }
    }

    fun loadProfile(): StudentProfile {
        migrateRetiredConnections()
        val storedId = prefs.getString("student_profile_id", null)
        val profile = StudentProfile(
            id = storedId ?: UUID.randomUUID().toString(),
            name = prefs.getString("student_profile_name", "Lincoln").orEmpty().ifBlank { "Lincoln" },
            instrument = prefs.getString("student_profile_instrument", "Violin")
                .orEmpty()
                .ifBlank { "Violin" },
            excelWorkbookUri = prefs.getString("excel_workbook_uri", null),
            excelWorkbookName = prefs.getString("excel_workbook_name", null),
        )
        if (storedId == null) saveProfile(profile)
        return profile
    }

    fun saveProfile(profile: StudentProfile) {
        prefs.edit(commit = true) {
            putString("student_profile_id", profile.id)
            putString("student_profile_name", profile.name)
            putString("student_profile_instrument", profile.instrument)
            putString("excel_workbook_uri", profile.excelWorkbookUri)
            putString("excel_workbook_name", profile.excelWorkbookName)
            RETIRED_CONNECTION_KEYS.forEach(::remove)
        }
    }

    fun loadLastFileSaveAt(): Long? =
        prefs.getLong("last_local_file_save_at", 0L).takeIf { it > 0L }

    fun saveLastFileSaveAt(atMillis: Long) {
        prefs.edit(commit = true) { putLong("last_local_file_save_at", atMillis) }
    }

    private fun migrateRetiredConnections() {
        if (prefs.getBoolean(LOCAL_ONLY_MIGRATION_KEY, false)) return
        prefs.edit(commit = true) {
            RETIRED_CONNECTION_KEYS.forEach(::remove)
            remove("excel_workbook_uri")
            remove("excel_workbook_name")
            remove("last_cloud_sync_at")
            remove("last_synced_revision")
            putBoolean(LOCAL_ONLY_MIGRATION_KEY, true)
        }
    }

    private fun migrateLegacy(currentDate: String): PracticeSnapshot {
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
                    }.getOrDefault(SourceType.YOUTUBE),
                    startSeconds = row.optInt("startSeconds", 0),
                    endSeconds = row.optIntOrNull("endSeconds"),
                    repetitions = row.optInt("repetitions", 1).coerceIn(1, 20),
                )
            }
        }.getOrDefault(emptyList())

        val completed = legacyPrefs.getStringSet("completedIds", emptySet())?.toSet().orEmpty()
        val taskIds = tasks.mapTo(mutableSetOf()) { it.id }
        val progress = DailyProgress(
            date = currentDate,
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
                PracticeEvent(
                    id = UUID.randomUUID().toString(),
                    date = dateKeyForMillis(atMillis),
                    taskId = null,
                    taskTitle = row.optString("taskTitle", "Practice session"),
                    event = row.optString("event"),
                    atMillis = atMillis,
                    videoUri = row.optNullableString("videoUri"),
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
        put("version", 2)
        put("tasks", JSONArray().apply {
            snapshot.tasks.forEach { task ->
                put(JSONObject().apply {
                    put("id", task.id)
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
                })
            }
        })
        put("progress", JSONArray().apply {
            snapshot.progressByDate.values.sortedBy { it.date }.forEach { progress ->
                put(JSONObject().apply {
                    put("date", progress.date)
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
                    put("taskId", event.taskId ?: JSONObject.NULL)
                    put("taskTitle", event.taskTitle)
                    put("event", event.event)
                    put("atMillis", event.atMillis)
                    put("videoUri", event.videoUri ?: JSONObject.NULL)
                    put("videoPath", event.videoPath ?: JSONObject.NULL)
                })
            }
        })
    }

    private fun decodeStoredSnapshot(value: String): DecodedPracticeSnapshot? =
        runCatching { decodeSnapshot(JSONObject(value)) }.getOrNull()

    private fun decodeSnapshot(root: JSONObject): DecodedPracticeSnapshot {
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
                val source = row.getString("source").trim()
                require(id.isNotBlank() && title.isNotBlank())
                val sourceType = runCatching { SourceType.valueOf(row.optString("sourceType")) }
                    .getOrElse { sourceTypeFor(source) ?: error("Unsupported source") }
                PracticeTask(
                    id = id,
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
                PracticeEvent(
                    id = row.optString("id").ifBlank { UUID.randomUUID().toString() },
                    date = row.optString("date").ifBlank { dateKeyForMillis(atMillis) },
                    taskId = row.optNullableString("taskId"),
                    taskTitle = row.optString("taskTitle", "Practice session"),
                    event = row.optString("event"),
                    atMillis = atMillis,
                    videoUri = row.optNullableString("videoUri"),
                    videoPath = row.optNullableString("videoPath"),
                )
            }.getOrElse {
                skippedRecords += 1
                null
            }
        }
        return DecodedPracticeSnapshot(
            snapshot = PracticeSnapshot(tasks, progress, events.takeLast(500)),
            skippedRecords = skippedRecords,
        )
    }
}

private const val LOCAL_ONLY_MIGRATION_KEY = "local_only_profile_migration_v1"

private val RETIRED_CONNECTION_KEYS = listOf(
    "youtube_channel_url",
    "youtube_channel_id",
    "youtube_channel_title",
    "youtube_account_email",
    "drive_spreadsheet_id",
    "drive_spreadsheet_name",
    "drive_spreadsheet_url",
    "drive_account_email",
    "drive_folder_uri",
    "drive_folder_name",
)

private data class DecodedPracticeSnapshot(
    val snapshot: PracticeSnapshot,
    val skippedRecords: Int,
)

private fun skippedRecordWarning(skippedRecords: Int): String? =
    skippedRecords.takeIf { it > 0 }?.let {
        "Recovered the readable data and skipped $it damaged record${if (it == 1) "" else "s"}."
    }

private fun JSONObject.optNullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf(String::isNotBlank)

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)
