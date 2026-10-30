package com.practicepartner.music

import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class AppPage {
    TODAY,
    ASSIGN,
    SUMMARY,
}

enum class PracticeMode(val label: String) {
    LISTEN("Listen to Teacher"),
    LISTEN_AND_PLAY("Listen & Play"),
    PLAY_YOURSELF("Play Yourself"),
}

enum class SourceType {
    YOUTUBE,
    LOCAL,
}

data class PracticeTask(
    val id: String,
    val assignedDate: String,
    val title: String,
    val source: String,
    val sourceType: SourceType,
    val startSeconds: Int,
    val endSeconds: Int?,
    val repetitions: Int,
    val coachNote: String = "",
    val whyImportant: String = "",
    val sheetMusicUri: String? = null,
)

data class DailyProgress(
    val date: String,
    val completedTaskIds: Set<String> = emptySet(),
    val repetitionsByTask: Map<String, Int> = emptyMap(),
    val elapsedSeconds: Long = 0L,
) {
    fun repetitionsFor(taskId: String): Int = repetitionsByTask[taskId] ?: 0

    fun recordRepetition(task: PracticeTask): DailyProgress {
        val nextCount = (repetitionsFor(task.id) + 1).coerceAtMost(task.repetitions)
        val nextCompleted = if (nextCount >= task.repetitions) completedTaskIds + task.id else completedTaskIds
        return copy(
            completedTaskIds = nextCompleted,
            repetitionsByTask = repetitionsByTask + (task.id to nextCount),
        )
    }

    fun toggleCompleted(task: PracticeTask): DailyProgress {
        val isCompleted = task.id in completedTaskIds
        return if (isCompleted) {
            copy(
                completedTaskIds = completedTaskIds - task.id,
                repetitionsByTask = repetitionsByTask + (task.id to 0),
            )
        } else {
            copy(
                completedTaskIds = completedTaskIds + task.id,
                repetitionsByTask = repetitionsByTask + (task.id to task.repetitions),
            )
        }
    }

    fun withoutTask(taskId: String): DailyProgress = copy(
        completedTaskIds = completedTaskIds - taskId,
        repetitionsByTask = repetitionsByTask - taskId,
    )
}

data class PracticeEvent(
    val id: String,
    val date: String,
    val taskId: String?,
    val taskTitle: String,
    val event: String,
    val atMillis: Long,
    val videoUri: String? = null,
    val videoPath: String? = null,
)

data class PracticeSnapshot(
    val tasks: List<PracticeTask> = emptyList(),
    val progressByDate: Map<String, DailyProgress> = emptyMap(),
    val events: List<PracticeEvent> = emptyList(),
)

data class RecordingFile(
    val uri: String,
    val path: String,
)

data class PracticeUiState(
    val snapshot: PracticeSnapshot,
    val currentDate: String,
    val page: AppPage = AppPage.TODAY,
    val mode: PracticeMode = PracticeMode.LISTEN,
    val selectedTaskId: String? = null,
    val activeTaskId: String? = null,
    val running: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val recordingActive: Boolean = false,
    val message: String = "",
) {
    val todayTasks: List<PracticeTask>
        get() = snapshot.tasks.filter { it.assignedDate == currentDate }

    val todayProgress: DailyProgress
        get() = snapshot.progressByDate[currentDate] ?: DailyProgress(currentDate)

    val todayEvents: List<PracticeEvent>
        get() = snapshot.events.filter { it.date == currentDate }

    val activeTask: PracticeTask?
        get() = snapshot.tasks.firstOrNull { it.id == activeTaskId }
}

fun currentDateKey(): String = LocalDate.now().toString()

fun dateKeyForMillis(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

fun youtubeVideoId(value: String): String? {
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
    val host = uri.host?.lowercase().orEmpty().removePrefix("www.")
    val path = uri.path.orEmpty()
    val id = when {
        host == "youtu.be" -> path.substringAfterLast('/')
        host == "youtube.com" || host == "m.youtube.com" -> when {
            path == "/watch" -> uri.rawQuery.orEmpty()
                .split('&')
                .mapNotNull {
                    val parts = it.split('=', limit = 2)
                    parts.getOrNull(1)?.let { valuePart -> parts.firstOrNull() to valuePart }
                }
                .firstOrNull { it.first == "v" }
                ?.second
                .orEmpty()
            path.startsWith("/embed/") || path.startsWith("/shorts/") -> path.substringAfterLast('/')
            else -> ""
        }
        host == "youtube-nocookie.com" && path.startsWith("/embed/") -> path.substringAfterLast('/')
        else -> ""
    }
    return id.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
}

fun sourceTypeFor(value: String): SourceType? = when {
    value.trim().startsWith("content://") -> SourceType.LOCAL
    youtubeVideoId(value) != null -> SourceType.YOUTUBE
    else -> null
}
