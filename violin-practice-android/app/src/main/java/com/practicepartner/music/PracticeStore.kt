package com.practicepartner.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PracticeTask(
    val id: String,
    val title: String,
    val source: String,
    val sourceType: String,
    val startSeconds: Int,
    val endSeconds: Int?,
    val repetitions: Int,
)

data class PracticeEvent(
    val taskTitle: String,
    val event: String,
    val atMillis: Long,
    val videoUri: String? = null,
)

class PracticeStore(context: Context) {
    private val prefs = context.getSharedPreferences("practice_partner_v1", Context.MODE_PRIVATE)

    fun tasks(): List<PracticeTask> = runCatching {
        val rows = JSONArray(prefs.getString("tasks", "[]"))
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            PracticeTask(
                id = row.getString("id"),
                title = row.getString("title"),
                source = row.getString("source"),
                sourceType = row.optString("sourceType", "youtube"),
                startSeconds = row.optInt("startSeconds", 0),
                endSeconds = if (row.isNull("endSeconds")) null else row.optInt("endSeconds"),
                repetitions = row.optInt("repetitions", 1),
            )
        }
    }.getOrDefault(emptyList())

    fun saveTasks(tasks: List<PracticeTask>) {
        val rows = JSONArray()
        tasks.forEach { task ->
            rows.put(JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("source", task.source)
                put("sourceType", task.sourceType)
                put("startSeconds", task.startSeconds)
                put("endSeconds", task.endSeconds ?: JSONObject.NULL)
                put("repetitions", task.repetitions)
            })
        }
        prefs.edit().putString("tasks", rows.toString()).apply()
    }

    fun events(): List<PracticeEvent> = runCatching {
        val rows = JSONArray(prefs.getString("events", "[]"))
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            PracticeEvent(
                taskTitle = row.optString("taskTitle"),
                event = row.optString("event"),
                atMillis = row.optLong("atMillis"),
                videoUri = row.optString("videoUri").takeIf { it.isNotBlank() },
            )
        }
    }.getOrDefault(emptyList())

    fun saveEvents(events: List<PracticeEvent>) {
        val rows = JSONArray()
        events.takeLast(500).forEach { event ->
            rows.put(JSONObject().apply {
                put("taskTitle", event.taskTitle)
                put("event", event.event)
                put("atMillis", event.atMillis)
                put("videoUri", event.videoUri ?: JSONObject.NULL)
            })
        }
        prefs.edit().putString("events", rows.toString()).apply()
    }

    fun elapsedSeconds(): Long = prefs.getLong("elapsedSeconds", 0L)
    fun saveElapsedSeconds(seconds: Long) { prefs.edit().putLong("elapsedSeconds", seconds).apply() }
    fun completedIds(): Set<String> = prefs.getStringSet("completedIds", emptySet())?.toSet() ?: emptySet()
    fun saveCompletedIds(ids: Set<String>) { prefs.edit().putStringSet("completedIds", ids.toSet()).apply() }
}
