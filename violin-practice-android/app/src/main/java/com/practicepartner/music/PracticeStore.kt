package com.practicepartner.music

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PracticeStore(context: Context) {
    private val prefs = context.getSharedPreferences("practice_partner_v2", Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences("practice_partner_v1", Context.MODE_PRIVATE)

    fun load(currentDate: String = currentDateKey()): PracticeSnapshot {
        val stored = prefs.getString("snapshot", null)
        if (stored != null) {
            return runCatching { decodeSnapshot(JSONObject(stored)) }.getOrElse { PracticeSnapshot() }
        }
        val migrated = migrateLegacy(currentDate)
        save(migrated)
        return migrated
    }

    fun save(snapshot: PracticeSnapshot) {
        prefs.edit {
            putString("snapshot", encodeSnapshot(snapshot).toString())
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

    private fun decodeSnapshot(root: JSONObject): PracticeSnapshot {
        val taskRows = root.optJSONArray("tasks") ?: JSONArray()
        val tasks = (0 until taskRows.length()).map { index ->
            val row = taskRows.getJSONObject(index)
            PracticeTask(
                id = row.getString("id"),
                assignedDate = row.optString("assignedDate", currentDateKey()),
                title = row.getString("title"),
                source = row.getString("source"),
                sourceType = runCatching { SourceType.valueOf(row.optString("sourceType")) }
                    .getOrElse { sourceTypeFor(row.optString("source")) ?: SourceType.YOUTUBE },
                startSeconds = row.optInt("startSeconds", 0),
                endSeconds = row.optIntOrNull("endSeconds"),
                repetitions = row.optInt("repetitions", 1).coerceIn(1, 20),
                coachNote = row.optString("coachNote"),
                whyImportant = row.optString("whyImportant"),
                sheetMusicUri = row.optNullableString("sheetMusicUri"),
            )
        }

        val progressRows = root.optJSONArray("progress") ?: JSONArray()
        val progress = (0 until progressRows.length()).associate { index ->
            val row = progressRows.getJSONObject(index)
            val date = row.getString("date")
            val completedRows = row.optJSONArray("completedTaskIds") ?: JSONArray()
            val completed = (0 until completedRows.length()).mapTo(mutableSetOf()) {
                completedRows.getString(it)
            }
            val repetitionsObject = row.optJSONObject("repetitionsByTask") ?: JSONObject()
            val repetitions = buildMap {
                repetitionsObject.keys().forEach { taskId ->
                    put(taskId, repetitionsObject.optInt(taskId, 0))
                }
            }
            date to DailyProgress(
                date = date,
                completedTaskIds = completed,
                repetitionsByTask = repetitions,
                elapsedSeconds = row.optLong("elapsedSeconds", 0L).coerceAtLeast(0L),
            )
        }

        val eventRows = root.optJSONArray("events") ?: JSONArray()
        val events = (0 until eventRows.length()).map { index ->
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
        }
        return PracticeSnapshot(tasks, progress, events.takeLast(500))
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf(String::isNotBlank)

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)
