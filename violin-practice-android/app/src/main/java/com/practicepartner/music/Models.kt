package com.practicepartner.music

import kotlinx.serialization.Serializable
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

enum class AssignmentType(val label: String) {
    WARM_UP("Warm-up"),
    TECHNIQUE("Technique"),
    REPERTOIRE("Repertoire"),
    OTHER("Other"),
}

enum class PracticeGoalType(val label: String) {
    REPETITIONS("Repetitions"),
    TIME("Practice time"),
}

enum class PracticeReflection(
    val kidLabel: String,
    val summaryLabel: String,
) {
    PROUD("I’m proud", "Felt proud"),
    MAKING_PROGRESS("I’m getting there", "Making progress"),
    NEED_HELP("I need help", "Would like help"),
}

enum class MasteryStatus(val summaryLabel: String) {
    NOT_ASSESSED("Not assessed"),
    STUDENT_CONFIDENT("Student feels confident"),
    TEACHER_APPROVED("Teacher approved"),
}

enum class SourceType {
    NONE,
    YOUTUBE,
    LOCAL,
}

@Serializable
data class Household(
    val id: String,
    val name: String = "Our Family",
    val childProfiles: List<ChildProfile>,
    val activeChildProfileId: String,
) {
    val activeChildProfile: ChildProfile?
        get() = childProfiles.firstOrNull { it.id == activeChildProfileId }
            ?: childProfiles.firstOrNull()
}

@Serializable
data class ChildProfile(
    val id: String,
    val name: String = "Student",
    val instrument: String = "Violin",
    val excelWorkbookUri: String? = null,
    val excelWorkbookName: String? = null,
)

typealias StudentProfile = ChildProfile

/**
 * Phase D model only. A teacher profile does not grant access by itself; runtime
 * authorization must be based on an active parent-granted [StudentTeacherLink].
 */
@Serializable
data class TeacherProfile(
    val id: String,
    val name: String,
    val email: String,
)

/**
 * Records parent-controlled access between one teacher and one child.
 *
 * Future teacher features must require a matching link with [revokedAtMillis] == null.
 * Only the granting parent may create or revoke this link, and revocation must stop all
 * subsequent teacher reads and writes for the child.
 */
@Serializable
data class StudentTeacherLink(
    val teacherId: String,
    val childProfileId: String,
    val grantedByParentId: String,
    val grantedAtMillis: Long,
    val revokedAtMillis: Long? = null,
)

/**
 * Phase D feedback record. Future writes must verify that [teacherId] has an active,
 * parent-granted link to the child who owns [sessionId]. This model alone grants no access.
 */
@Serializable
data class TeacherFeedback(
    val sessionId: String,
    val teacherId: String,
    val comment: String,
    val atMillis: Long,
)

enum class LocalFileStatus {
    NOT_CONFIGURED,
    READY,
    UNSAVED,
    SAVING,
    ERROR,
}

data class LocalFileUiState(
    val status: LocalFileStatus,
    val fileName: String? = null,
    val fileUri: String? = null,
    val lastSavedAtMillis: Long? = null,
    val hasUnsavedChanges: Boolean = false,
    val detail: String = "",
)

@Serializable
data class Assignment(
    val id: String,
    val assignedDate: DateKey,
    val title: String,
    val source: String,
    val sourceType: SourceType,
    val startSeconds: Int,
    val endSeconds: Int?,
    val repetitions: Int,
    val coachNote: String = "",
    val whyImportant: String = "",
    val sheetMusicUri: String? = null,
    val childProfileId: String = "",
    // Null means parent-created. Future teacher writes require an active StudentTeacherLink.
    val teacherId: String? = null,
    val instrument: String = "",
    val assignmentType: AssignmentType = AssignmentType.TECHNIQUE,
    val goalType: PracticeGoalType = PracticeGoalType.REPETITIONS,
    val targetSeconds: Int = 0,
    val enabledModes: Set<PracticeMode> = setOf(PracticeMode.PLAY_YOURSELF),
    val revision: Int = 1,
    val archivedAtMillis: Long? = null,
)

typealias PracticeTask = Assignment

@Serializable
data class AssignmentHistory(
    val assignmentId: String,
    val childProfileId: String,
    // The revision represented by previousAssignment.
    val revision: Int,
    val changedAtMillis: Long,
    // Field names changed by the edit that replaced this revision.
    val changedFields: Set<String> = emptySet(),
    // Full immutable snapshot so completed practice can still resolve revision N.
    val previousAssignment: Assignment,
)

@Serializable
data class DailyProgress(
    val date: DateKey,
    val childProfileId: String = "",
    // Completion means the assigned practice goal was done; it does not imply mastery.
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
                repetitionsByTask = if (task.goalType == PracticeGoalType.REPETITIONS) {
                    repetitionsByTask + (
                        task.id to (task.repetitions - 1).coerceAtLeast(0)
                        )
                } else {
                    repetitionsByTask
                },
            )
        } else {
            copy(
                completedTaskIds = completedTaskIds + task.id,
                repetitionsByTask = if (task.goalType == PracticeGoalType.REPETITIONS) {
                    repetitionsByTask + (task.id to task.repetitions)
                } else {
                    repetitionsByTask
                },
            )
        }
    }

    fun withoutTask(taskId: String): DailyProgress = copy(
        completedTaskIds = completedTaskIds - taskId,
        repetitionsByTask = repetitionsByTask - taskId,
    )
}

@Serializable
data class PracticeEvent(
    val id: String,
    val date: DateKey,
    val taskId: String?,
    val taskTitle: String,
    val event: String,
    val atMillis: Long,
    val videoUri: String? = null,
    val videoPath: String? = null,
    val videoFileName: String? = null,
    val childProfileId: String = "",
    val sessionId: String? = null,
)

enum class PracticeSessionStatus {
    ACTIVE,
    COMPLETED,
    ENDED,
    INTERRUPTED,
}

@Serializable
data class PracticeSession(
    val id: String,
    val childProfileId: String,
    val assignmentId: String,
    val assignmentTitle: String,
    val instrument: String,
    val date: DateKey,
    val mode: PracticeMode,
    val modesUsed: Set<PracticeMode> = setOf(mode),
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val repetitionsCompleted: Int = 0,
    val status: PracticeSessionStatus = PracticeSessionStatus.ACTIVE,
    val assignmentRevision: Int = 1,
    val assignmentType: AssignmentType = AssignmentType.TECHNIQUE,
    val goalType: PracticeGoalType = PracticeGoalType.REPETITIONS,
    val targetRepetitions: Int = 1,
    val targetSeconds: Int = 0,
    val elapsedSeconds: Long = 0L,
    val reflection: PracticeReflection? = null,
    val masteryStatus: MasteryStatus = MasteryStatus.NOT_ASSESSED,
)

@Serializable
data class PracticeSnapshot(
    val tasks: List<PracticeTask> = emptyList(),
    val progressByDate: Map<DateKey, DailyProgress> = emptyMap(),
    val events: List<PracticeEvent> = emptyList(),
    val sessions: List<PracticeSession> = emptyList(),
    val assignmentHistory: List<AssignmentHistory> = emptyList(),
)

data class RecordingFile(
    val uri: String,
    val path: String,
    val fileName: String,
)

data class PracticeUiState(
    val snapshot: PracticeSnapshot,
    val activeChildProfileId: String,
    val currentDate: DateKey,
    val summaryDate: DateKey = currentDate,
    val page: AppPage = AppPage.TODAY,
    val mode: PracticeMode = PracticeMode.LISTEN,
    val selectedTaskId: String? = null,
    val activeTaskId: String? = null,
    val activeSessionId: String? = null,
    val running: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val activeSessionElapsedSeconds: Long = 0L,
    val recordingActive: Boolean = false,
    val pendingReflectionSessionId: String? = null,
    val message: String = "",
) {
    val todayTasks: List<PracticeTask> = snapshot.tasks.filter {
        it.assignedDate == currentDate && it.archivedAtMillis == null
    }

    val todayProgress: DailyProgress = snapshot.progressByDate[currentDate] ?: DailyProgress(
        date = currentDate,
        childProfileId = activeChildProfileId,
    )

    val todayEvents: List<PracticeEvent> =
        snapshot.events.filter { it.date == currentDate }

    val todaySessions: List<PracticeSession> =
        snapshot.sessions.filter { it.date == currentDate }

    val daysSinceLastPractice: Int =
        calculateDaysSinceLastPractice(snapshot, currentDate)

    val summaryTasks: List<PracticeTask> =
        snapshot.tasks.filter { it.assignedDate == summaryDate }

    val summaryProgress: DailyProgress = snapshot.progressByDate[summaryDate] ?: DailyProgress(
        date = summaryDate,
        childProfileId = activeChildProfileId,
    )

    val summaryEvents: List<PracticeEvent> =
        snapshot.events.filter { it.date == summaryDate }

    val summarySessions: List<PracticeSession> =
        snapshot.sessions.filter { it.date == summaryDate }

    private val assignmentHistoryById: Map<String, List<AssignmentHistory>> =
        snapshot.assignmentHistory
            .groupBy(AssignmentHistory::assignmentId)
            .mapValues { (_, history) ->
                history.sortedWith(
                    compareByDescending<AssignmentHistory> { it.revision }
                        .thenByDescending { it.changedAtMillis },
                )
            }

    val historyDates: List<DateKey> = (
        snapshot.tasks.map(PracticeTask::assignedDate) +
            snapshot.progressByDate.keys +
            snapshot.events.map(PracticeEvent::date) +
            snapshot.sessions.map(PracticeSession::date) +
            snapshot.assignmentHistory.map { it.previousAssignment.assignedDate } +
            currentDate
        ).distinct().sorted()

    val activeTask: PracticeTask? =
        snapshot.tasks.firstOrNull { it.id == activeTaskId }

    val activeSession: PracticeSession? =
        snapshot.sessions.firstOrNull { it.id == activeSessionId }

    val activeSessionModesUsed: Set<PracticeMode> =
        activeSession?.resolvedModesUsed().orEmpty()

    val historyCapacityWarning: String? = practiceHistoryCapacityWarning(
        eventCount = snapshot.events.size,
        sessionCount = snapshot.sessions.size,
    )

    val pendingReflectionSession: PracticeSession? =
        snapshot.sessions.firstOrNull { it.id == pendingReflectionSessionId }

    fun tasksForDate(date: DateKey): List<PracticeTask> =
        snapshot.tasks.filter {
            it.assignedDate == date && it.archivedAtMillis == null
        }

    fun archivedTasksForDate(date: DateKey): List<PracticeTask> =
        snapshot.tasks.filter {
            it.assignedDate == date && it.archivedAtMillis != null
        }

    fun assignmentHistoryFor(assignmentId: String): List<AssignmentHistory> =
        assignmentHistoryById[assignmentId].orEmpty()

    fun assignmentAtRevision(
        assignmentId: String,
        revision: Int,
    ): Assignment? =
        snapshot.tasks.firstOrNull {
            it.id == assignmentId && it.revision == revision
        } ?: assignmentHistoryById[assignmentId]
            ?.firstOrNull { it.revision == revision }
            ?.previousAssignment

    fun secondsForTask(taskId: String, date: DateKey): Long =
        snapshot.sessions
            .asSequence()
            .filter { it.assignmentId == taskId && it.date == date }
            .sumOf { session ->
                if (session.id == activeSessionId) {
                    maxOf(session.elapsedSeconds, activeSessionElapsedSeconds)
                } else {
                    session.elapsedSeconds
                }
            }

    fun goalMet(task: PracticeTask, date: DateKey = task.assignedDate): Boolean {
        val progress = snapshot.progressByDate[date] ?: DailyProgress(date)
        if (task.id in progress.completedTaskIds) return true
        return when (task.goalType) {
            PracticeGoalType.REPETITIONS ->
                progress.repetitionsFor(task.id) >= task.repetitions
            PracticeGoalType.TIME ->
                task.targetSeconds > 0 && secondsForTask(task.id, date) >= task.targetSeconds
        }
    }

    fun progressLabel(task: PracticeTask, date: DateKey = task.assignedDate): String =
        when (task.goalType) {
            PracticeGoalType.REPETITIONS ->
                "${snapshot.progressByDate[date]?.repetitionsFor(task.id) ?: 0} / " +
                    "${task.repetitions} repetitions"
            PracticeGoalType.TIME ->
                "${formatGoalDuration(secondsForTask(task.id, date))} / " +
                    "${formatGoalDuration(task.targetSeconds.toLong())}"
        }

    fun weeklySummary(anchorDate: DateKey = summaryDate): WeeklyPracticeSummary {
        val anchor = anchorDate.toLocalDate()
        val firstDay = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
        val days = (0L..6L).map { offset ->
            val date = DateKey.from(firstDay.plusDays(offset))
            val sessionSeconds = snapshot.sessions
                .filter { it.date == date }
                .sumOf(PracticeSession::elapsedSeconds)
            val progressSeconds = if (date == currentDate) {
                elapsedSeconds
            } else {
                snapshot.progressByDate[date]?.elapsedSeconds ?: 0L
            }
            val seconds = maxOf(sessionSeconds, progressSeconds)
            val practiced = seconds > 0L ||
                snapshot.sessions.any {
                    it.date == date && it.repetitionsCompleted > 0
                }
            WeeklyPracticeDay(
                date = date,
                practiced = practiced,
                seconds = seconds,
            )
        }
        return WeeklyPracticeSummary(
            startDate = DateKey.from(firstDay),
            endDate = DateKey.from(firstDay.plusDays(6)),
            days = days,
        )
    }
}

internal fun PracticeTask.availablePracticeModes(): List<PracticeMode> {
    if (sourceType == SourceType.NONE) return listOf(PracticeMode.PLAY_YOURSELF)
    return PracticeMode.entries
        .filter(enabledModes::contains)
        .ifEmpty { listOf(PracticeMode.PLAY_YOURSELF) }
}

internal fun PracticeSession.resolvedModesUsed(): Set<PracticeMode> =
    modesUsed.ifEmpty { setOf(mode) }

internal fun PracticeSession.orderedModesUsed(): List<PracticeMode> {
    val used = resolvedModesUsed()
    return PracticeMode.entries.filter(used::contains)
}

internal fun calculateDaysSinceLastPractice(
    snapshot: PracticeSnapshot,
    currentDate: DateKey,
): Int {
    val today = currentDate.toLocalDate()
    val practiceDates = buildList {
        snapshot.sessions.mapTo(this) { session ->
            session.date.toLocalDate()
        }
        snapshot.progressByDate.values
            .asSequence()
            .filter(DailyProgress::hasPracticeActivity)
            .mapTo(this) { progress ->
                progress.date.toLocalDate()
            }
    }
    val lastPracticeDate = practiceDates
        .asSequence()
        .filterNot { it.isAfter(today) }
        .maxOrNull()
        ?: return 0
    return ChronoUnit.DAYS.between(lastPracticeDate, today)
        .coerceAtLeast(0L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

private fun DailyProgress.hasPracticeActivity(): Boolean =
    elapsedSeconds > 0L ||
        completedTaskIds.isNotEmpty() ||
        repetitionsByTask.values.any { it > 0 }

data class WeeklyPracticeDay(
    val date: DateKey,
    val practiced: Boolean,
    val seconds: Long,
)

data class WeeklyPracticeSummary(
    val startDate: DateKey,
    val endDate: DateKey,
    val days: List<WeeklyPracticeDay>,
) {
    val practicedDays: Int
        get() = days.count(WeeklyPracticeDay::practiced)

    val totalSeconds: Long
        get() = days.sumOf(WeeklyPracticeDay::seconds)
}

data class AssignmentTemplate(
    val label: String,
    val title: String,
    val assignmentType: AssignmentType,
    val goalType: PracticeGoalType,
    val repetitions: Int = 3,
    val targetSeconds: Int = 0,
    val enabledModes: Set<PracticeMode> = setOf(PracticeMode.PLAY_YOURSELF),
    val coachNote: String = "",
)

fun assignmentTemplatesFor(instrument: String): List<AssignmentTemplate> {
    val normalized = instrument.trim().lowercase()
    return when {
        normalized.contains("violin") -> listOf(
            AssignmentTemplate(
                label = "Violin warm-up",
                title = "Scales and bow warm-up",
                assignmentType = AssignmentType.WARM_UP,
                goalType = PracticeGoalType.REPETITIONS,
                repetitions = 3,
                coachNote = "Use a relaxed bow hold and listen for an even sound.",
            ),
            AssignmentTemplate(
                label = "Violin technique",
                title = "Technique focus",
                assignmentType = AssignmentType.TECHNIQUE,
                goalType = PracticeGoalType.REPETITIONS,
                repetitions = 5,
                coachNote = "Start slowly, stay relaxed, and repeat the small section.",
            ),
            AssignmentTemplate(
                label = "Violin piece",
                title = "Repertoire passage",
                assignmentType = AssignmentType.REPERTOIRE,
                goalType = PracticeGoalType.TIME,
                targetSeconds = 10 * 60,
                coachNote = "Choose a short passage and improve one detail at a time.",
            ),
        )
        normalized.contains("piano") -> listOf(
            AssignmentTemplate(
                label = "Piano warm-up",
                title = "Scales and arpeggios",
                assignmentType = AssignmentType.WARM_UP,
                goalType = PracticeGoalType.REPETITIONS,
                repetitions = 3,
                coachNote = "Use steady rhythm and relaxed hands.",
            ),
            AssignmentTemplate(
                label = "Piano technique",
                title = "Hands-separate practice",
                assignmentType = AssignmentType.TECHNIQUE,
                goalType = PracticeGoalType.REPETITIONS,
                repetitions = 5,
                coachNote = "Practice each hand slowly before putting them together.",
            ),
            AssignmentTemplate(
                label = "Piano piece",
                title = "Repertoire passage",
                assignmentType = AssignmentType.REPERTOIRE,
                goalType = PracticeGoalType.TIME,
                targetSeconds = 10 * 60,
                coachNote = "Choose a small section and keep the pulse steady.",
            ),
        )
        else -> listOf(
            AssignmentTemplate(
                label = "Warm-up",
                title = "Warm-up",
                assignmentType = AssignmentType.WARM_UP,
                goalType = PracticeGoalType.TIME,
                targetSeconds = 5 * 60,
            ),
            AssignmentTemplate(
                label = "Technique",
                title = "Technique focus",
                assignmentType = AssignmentType.TECHNIQUE,
                goalType = PracticeGoalType.REPETITIONS,
                repetitions = 5,
            ),
            AssignmentTemplate(
                label = "Music",
                title = "Repertoire passage",
                assignmentType = AssignmentType.REPERTOIRE,
                goalType = PracticeGoalType.TIME,
                targetSeconds = 10 * 60,
            ),
        )
    }
}

fun formatGoalDuration(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return if (remainingSeconds == 0L) {
        "$minutes min"
    } else {
        "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
    }
}

fun currentDateKey(): DateKey = DateKey.today()

fun dateKeyForMillis(millis: Long): DateKey =
    DateKey.from(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())

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
    value.isBlank() -> SourceType.NONE
    value.trim().startsWith("content://") -> SourceType.LOCAL
    youtubeVideoId(value) != null -> SourceType.YOUTUBE
    else -> null
}

fun PracticeSnapshot.scopedTo(profile: ChildProfile): PracticeSnapshot {
    fun belongsTo(childProfileId: String): Boolean =
        childProfileId.isBlank() || childProfileId == profile.id

    val scopedTasks = tasks
        .filter { belongsTo(it.childProfileId) }
        .map { task ->
            task.copy(
                childProfileId = profile.id,
                instrument = task.instrument.ifBlank { profile.instrument },
            )
        }
    val scopedTaskIds = scopedTasks.mapTo(mutableSetOf()) { it.id }
    val scopedProgress = progressByDate.mapNotNull { (date, progress) ->
        if (!belongsTo(progress.childProfileId)) {
            null
        } else {
            date to progress.copy(
                childProfileId = profile.id,
                completedTaskIds = progress.completedTaskIds.intersect(scopedTaskIds),
                repetitionsByTask = progress.repetitionsByTask.filterKeys(scopedTaskIds::contains),
            )
        }
    }.toMap()
    val scopedSessions = sessions
        .filter { belongsTo(it.childProfileId) }
        .map { it.copy(childProfileId = profile.id) }
    val scopedSessionIds = scopedSessions.mapTo(mutableSetOf()) { it.id }
    val scopedEvents = events
        .filter { belongsTo(it.childProfileId) }
        .map { event ->
            event.copy(
                childProfileId = profile.id,
                sessionId = event.sessionId?.takeIf(scopedSessionIds::contains),
            )
        }
    val scopedAssignmentHistory = assignmentHistory
        .filter { belongsTo(it.childProfileId) }
        .map { history ->
            history.copy(
                childProfileId = profile.id,
                previousAssignment = history.previousAssignment.copy(
                    id = history.assignmentId,
                    childProfileId = profile.id,
                    instrument = history.previousAssignment.instrument
                        .ifBlank { profile.instrument },
                    revision = history.revision,
                ),
            )
        }
    return PracticeSnapshot(
        tasks = scopedTasks,
        progressByDate = scopedProgress,
        events = scopedEvents,
        sessions = scopedSessions,
        assignmentHistory = scopedAssignmentHistory,
    )
}

internal fun preserveAssignmentVersionHistory(
    previous: PracticeSnapshot,
    candidate: PracticeSnapshot,
    changedAtMillis: Long,
): PracticeSnapshot {
    val previousById = previous.tasks.associateBy(PracticeTask::id)
    val existingKeys = candidate.assignmentHistory
        .mapTo(mutableSetOf()) { it.assignmentId to it.revision }
    val addedHistory = mutableListOf<AssignmentHistory>()
    val normalizedTasks = candidate.tasks.map { nextTask ->
        val previousTask = previousById[nextTask.id]
            ?: return@map nextTask
        if (previousTask == nextTask) return@map nextTask

        val versionedTask = if (nextTask.revision > previousTask.revision) {
            nextTask
        } else {
            nextTask.copy(revision = previousTask.revision + 1)
        }
        val historyKey = previousTask.id to previousTask.revision
        if (existingKeys.add(historyKey)) {
            addedHistory += AssignmentHistory(
                assignmentId = previousTask.id,
                childProfileId = previousTask.childProfileId,
                revision = previousTask.revision,
                changedAtMillis = changedAtMillis,
                changedFields = assignmentChangedFields(previousTask, versionedTask),
                previousAssignment = previousTask,
            )
        }
        versionedTask
    }
    if (addedHistory.isEmpty() && normalizedTasks == candidate.tasks) return candidate
    return candidate.copy(
        tasks = normalizedTasks,
        assignmentHistory = candidate.assignmentHistory + addedHistory,
    )
}

private fun assignmentChangedFields(
    previous: Assignment,
    next: Assignment,
): Set<String> = buildSet {
    if (previous.assignedDate != next.assignedDate) add("assignedDate")
    if (previous.title != next.title) add("title")
    if (previous.source != next.source) add("source")
    if (previous.sourceType != next.sourceType) add("sourceType")
    if (previous.startSeconds != next.startSeconds) add("startSeconds")
    if (previous.endSeconds != next.endSeconds) add("endSeconds")
    if (previous.repetitions != next.repetitions) add("repetitions")
    if (previous.coachNote != next.coachNote) add("coachNote")
    if (previous.whyImportant != next.whyImportant) add("whyImportant")
    if (previous.sheetMusicUri != next.sheetMusicUri) add("sheetMusicUri")
    if (previous.teacherId != next.teacherId) add("teacherId")
    if (previous.instrument != next.instrument) add("instrument")
    if (previous.assignmentType != next.assignmentType) add("assignmentType")
    if (previous.goalType != next.goalType) add("goalType")
    if (previous.targetSeconds != next.targetSeconds) add("targetSeconds")
    if (previous.enabledModes != next.enabledModes) add("enabledModes")
    if (previous.archivedAtMillis != next.archivedAtMillis) add("archivedAtMillis")
    if (isEmpty()) add("revision")
}
