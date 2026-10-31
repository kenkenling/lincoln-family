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

enum class WorkbookStorageKind {
    GOOGLE_DRIVE,
    DEVICE,
    OTHER,
}

data class WorkbookDestinationInfo(
    val kind: WorkbookStorageKind,
    val providerName: String,
    val accountEmail: String? = null,
) {
    val isGoogleDrive: Boolean
        get() = kind == WorkbookStorageKind.GOOGLE_DRIVE

    fun matchesExpectedAccount(expectedEmail: String?): Boolean {
        val expected = expectedEmail?.trim()?.lowercase().orEmpty()
        if (expected.isBlank()) return false
        return accountEmail?.trim()?.lowercase() == expected
    }
}

data class StudentProfile(
    val id: String,
    val name: String = "Lincoln",
    val instrument: String = "Violin",
    val youtubeChannelUrl: String = "",
    val youtubeChannelId: String? = null,
    val youtubeChannelTitle: String? = null,
    val youtubeAccountEmail: String? = null,
    val excelWorkbookUri: String? = null,
    val excelWorkbookName: String? = null,
    // Retained so an installation that tried the earlier Google API flow loses no metadata.
    val driveSpreadsheetId: String? = null,
    val driveSpreadsheetName: String? = null,
    val driveSpreadsheetUrl: String? = null,
    val driveAccountEmail: String? = null,
    // Retained only so existing folder-export installations can migrate safely.
    val driveFolderUri: String? = null,
    val driveFolderName: String? = null,
)

enum class CloudSyncStatus {
    DISCONNECTED,
    NEEDS_VERIFICATION,
    CONNECTED,
    UNSYNCED,
    SYNCING,
    ERROR,
}

data class CloudUiState(
    val status: CloudSyncStatus,
    val destinationName: String? = null,
    val destinationUri: String? = null,
    val destinationProviderName: String? = null,
    val destinationAccountEmail: String? = null,
    val isGoogleDrive: Boolean = false,
    val lastSyncedAtMillis: Long? = null,
    val hasUnsyncedChanges: Boolean = false,
    val detail: String = "",
)

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
                repetitionsByTask = repetitionsByTask + (
                    task.id to (task.repetitions - 1).coerceAtLeast(0)
                    ),
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
    val summaryDate: String = currentDate,
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

    val summaryTasks: List<PracticeTask>
        get() = snapshot.tasks.filter { it.assignedDate == summaryDate }

    val summaryProgress: DailyProgress
        get() = snapshot.progressByDate[summaryDate] ?: DailyProgress(summaryDate)

    val summaryEvents: List<PracticeEvent>
        get() = snapshot.events.filter { it.date == summaryDate }

    val historyDates: List<String>
        get() = (
            snapshot.tasks.map(PracticeTask::assignedDate) +
                snapshot.progressByDate.keys +
                snapshot.events.map(PracticeEvent::date) +
                currentDate
            ).distinct().sorted()

    val activeTask: PracticeTask?
        get() = snapshot.tasks.firstOrNull { it.id == activeTaskId }

    fun tasksForDate(date: String): List<PracticeTask> =
        snapshot.tasks.filter { it.assignedDate == date }
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

fun normalizedYoutubeChannelUrl(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    val candidate = when {
        trimmed.startsWith("@") -> "https://www.youtube.com/$trimmed"
        "://" !in trimmed -> "https://$trimmed"
        else -> trimmed
    }
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    val host = uri.host?.lowercase().orEmpty().removePrefix("www.").removePrefix("m.")
    if (uri.scheme?.lowercase() != "https" || host != "youtube.com") return null
    val firstSegment = uri.path.orEmpty().trim('/').substringBefore('/')
    val isChannelPath = firstSegment.startsWith("@") ||
        firstSegment in setOf("channel", "c", "user")
    return candidate.takeIf { isChannelPath }
}

fun normalizedAccountEmail(value: String): String? {
    val normalized = value.trim().lowercase()
    if (normalized.isBlank()) return ""
    return normalized.takeIf {
        it.length <= 254 &&
            it.matches(Regex("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)+"))
    }
}
