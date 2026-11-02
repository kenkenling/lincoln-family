package com.lkeducation.lkpractice

import java.util.UUID

class PracticeSessionManager(
    private val profileProvider: () -> ChildProfile,
    private val stateProvider: () -> PracticeUiState,
    private val wallClock: () -> Long,
    private val canReadContentUri: (String?) -> Boolean,
    private val updateState: ((PracticeUiState) -> PracticeUiState) -> Unit,
    private val updateSnapshot: ((PracticeSnapshot) -> PracticeSnapshot) -> Unit,
    private val setMessage: (String) -> Unit,
    private val startTimer: (String) -> Unit,
    private val pauseTimer: (Boolean) -> Unit,
    private val prepareSessionTimer: () -> Unit,
    private val resetSessionTimer: (Long) -> Unit,
    private val deleteManagedRecordingFile: (String) -> Unit,
) {
    fun startPractice(
        task: PracticeTask,
        studentInitiated: Boolean = false,
    ) {
        val state = stateProvider()
        val profile = profileProvider()
        if (state.activeTaskId != null) {
            setMessage("Finish the current practice mission before starting another one.")
            return
        }
        if (task.archivedAtMillis != null) {
            setMessage("This assignment is archived. A grown-up can restore it in Parent Space.")
            return
        }
        if (
            task.childProfileId.isNotBlank() &&
            task.childProfileId != profile.id
        ) {
            setMessage("This assignment belongs to another student profile.")
            return
        }
        if (!studentInitiated && !task.isScheduledOn(state.currentDate)) {
            setMessage(
                "This assignment is scheduled for " +
                    "${assignmentDateRangeLabel(task.startDate, task.endDate)}, not today.",
            )
            return
        }
        val completedTodayReplay =
            state.allTodayTasksComplete &&
                task.isScheduledOn(state.currentDate) &&
                state.goalMet(task, state.currentDate)
        if (
            studentInitiated &&
            task.endDate >= state.currentDate &&
            !completedTodayReplay
        ) {
            setMessage("Choose a completed or past assignment for practice on your own.")
            return
        }
        if (
            task.sourceType == SourceType.LOCAL &&
            (task.source.isBlank() || !canReadContentUri(task.source))
        ) {
            setMessage(
                "This assignment’s device video is unavailable. Reattach it from Assign.",
            )
            return
        }
        val availableModes = task.availablePracticeModes()
        val resolvedMode = state.mode.takeIf(availableModes::contains)
            ?: availableModes.first()
        val session = PracticeSession(
            id = UUID.randomUUID().toString(),
            childProfileId = profile.id,
            assignmentId = task.id,
            assignmentTitle = task.title,
            instrument = task.instrument.ifBlank { profile.instrument },
            date = state.currentDate,
            mode = resolvedMode,
            modesUsed = setOf(resolvedMode),
            startedAtMillis = wallClock(),
            repetitionsCompleted = 0,
            assignmentRevision = task.revision,
            assignmentType = task.assignmentType,
            goalType = task.goalType,
            targetRepetitions = task.repetitions,
            targetSeconds = task.targetSeconds,
            elapsedSeconds = 0L,
            studentInitiated = studentInitiated,
        )
        prepareSessionTimer()
        updateState {
            it.copy(
                selectedTaskId = task.id,
                activeTaskId = task.id,
                activeSessionId = session.id,
                mode = resolvedMode,
                activeSessionElapsedSeconds = 0L,
                pendingReflectionSessionId = null,
            )
        }
        updateSnapshot { snapshot ->
            snapshot.copy(
                sessions = (snapshot.sessions + session)
                    .takeLast(MAX_PRACTICE_SESSIONS),
            )
        }
        if (!stateProvider().running) startTimer("Practice timer started")
        logEvent(
            task,
            if (studentInitiated) {
                "Self-directed practice started · ${stateProvider().mode.label}"
            } else {
                "${stateProvider().mode.label} started"
            },
        )
    }

    fun switchMode(mode: PracticeMode): Boolean {
        val state = stateProvider()
        val task = taskFor(state.activeTaskId)
        val sessionId = state.activeSessionId
        if (task == null || sessionId == null) {
            setMessage("Start a practice mission before changing steps.")
            return false
        }
        if (mode !in task.availablePracticeModes()) {
            setMessage("That practice step is not enabled for this assignment.")
            return false
        }
        if (state.mode == mode) return false
        if (state.snapshot.sessions.none { it.id == sessionId }) {
            setMessage("The active practice session could not be found.")
            return false
        }

        updateSnapshot { snapshot ->
            snapshot.copy(
                sessions = snapshot.sessions.map { session ->
                    if (session.id == sessionId) {
                        session.copy(
                            mode = mode,
                            modesUsed = session.resolvedModesUsed() + mode,
                        )
                    } else {
                        session
                    }
                },
            )
        }
        updateState { it.copy(mode = mode) }
        logEvent(task, "Switched to ${mode.kidLabel()}")
        return true
    }

    fun finishPractice(): Boolean {
        val state = stateProvider()
        if (state.recordingActive) {
            setMessage("Stop the current recording before finishing practice.")
            return false
        }
        val task = taskFor(state.activeTaskId)
        val sessionId = state.activeSessionId
        pauseTimer(false)
        val updatedState = stateProvider()
        val goalMet = task?.let {
            updatedState.goalMet(it, updatedState.currentDate)
        } == true
        if (sessionId != null) {
            updateSnapshot { snapshot ->
                snapshot.copy(
                    sessions = snapshot.sessions.map { session ->
                        if (session.id == sessionId) {
                            session.copy(
                                endedAtMillis = wallClock()
                                    .coerceAtLeast(session.startedAtMillis),
                                status = if (goalMet) {
                                    PracticeSessionStatus.COMPLETED
                                } else {
                                    PracticeSessionStatus.ENDED
                                },
                            )
                        } else {
                            session
                        }
                    },
                )
            }
        }
        if (task != null) logEvent(task, "Practice session finished")
        updateState {
            it.copy(
                activeTaskId = null,
                activeSessionId = null,
                activeSessionElapsedSeconds = 0L,
                recordingActive = false,
                pendingReflectionSessionId = sessionId,
            )
        }
        resetSessionTimer(stateProvider().elapsedSeconds)
        return true
    }

    fun recordRepetition(task: PracticeTask) {
        val state = stateProvider()
        if (task.goalType != PracticeGoalType.REPETITIONS) {
            setMessage("This mission uses a practice-time goal.")
            return
        }
        val before = state.todayProgress.repetitionsFor(task.id)
        if (before >= task.repetitions) {
            setMessage("All ${task.repetitions} repetitions are already complete.")
            return
        }
        updateDailyProgress { it.recordRepetition(task) }
        val after = stateProvider().todayProgress.repetitionsFor(task.id)
        stateProvider().activeSessionId?.let { sessionId ->
            updateSnapshot { snapshot ->
                snapshot.copy(
                    sessions = snapshot.sessions.map { session ->
                        if (session.id == sessionId) {
                            session.copy(
                                repetitionsCompleted =
                                    session.repetitionsCompleted + (after - before),
                            )
                        } else {
                            session
                        }
                    },
                )
            }
        }
        logEvent(task, "Repetition $after of ${task.repetitions} completed")
        setMessage(
            if (after >= task.repetitions) {
                "Assignment complete: ${task.repetitions} repetitions."
            } else {
                "Recorded repetition $after of ${task.repetitions}."
            },
        )
    }

    fun submitReflection(
        reflection: PracticeReflection?,
        studentConfident: Boolean = false,
    ) {
        val state = stateProvider()
        val sessionId = state.pendingReflectionSessionId ?: return
        val session = state.snapshot.sessions.firstOrNull { it.id == sessionId }
        updateSnapshot { snapshot ->
            snapshot.copy(
                sessions = snapshot.sessions.map {
                    if (it.id == sessionId) {
                        it.copy(
                            reflection = reflection,
                            masteryStatus = when {
                                it.masteryStatus == MasteryStatus.TEACHER_APPROVED ->
                                    MasteryStatus.TEACHER_APPROVED
                                studentConfident -> MasteryStatus.STUDENT_CONFIDENT
                                else -> MasteryStatus.NOT_ASSESSED
                            },
                        )
                    } else {
                        it
                    }
                },
            )
        }
        if (reflection != null) {
            logEvent(
                task = taskFor(session?.assignmentId),
                message = "Reflection: ${reflection.summaryLabel}",
                sessionIdOverride = sessionId,
            )
        }
        if (studentConfident) {
            logEvent(
                task = taskFor(session?.assignmentId),
                message = "Confidence: Student feels confident",
                sessionIdOverride = sessionId,
            )
        }
        updateState {
            it.copy(
                pendingReflectionSessionId = null,
                message = when {
                    studentConfident ->
                        "Confidence noted separately from completing the practice goal."
                    reflection == PracticeReflection.PROUD ->
                        "Nice work noticing what went well."
                    reflection == PracticeReflection.MAKING_PROGRESS ->
                        "Small steps count. Keep building."
                    reflection == PracticeReflection.NEED_HELP ->
                        "Good noticing. A grown-up can review this with you."
                    else -> "Practice saved."
                },
            )
        }
    }

    fun toggleCompleted(task: PracticeTask) {
        val wasCompleted = task.id in stateProvider().todayProgress.completedTaskIds
        updateDailyProgress { it.toggleCompleted(task) }
        logEvent(
            task,
            if (wasCompleted) "Assignment reopened" else "Assignment marked complete",
        )
    }

    fun recordingSaved(task: PracticeTask, recording: RecordingFile) {
        logEvent(
            task = task,
            message = "Practice take saved privately",
            videoUri = recording.uri,
            videoPath = recording.path,
            videoFileName = recording.fileName,
        )
        setMessage("Saved ${recording.fileName}. A grown-up can review it in Practice Summary.")
    }

    fun interruptActiveSession() {
        val sessionId = stateProvider().activeSessionId ?: return
        updateSnapshot { snapshot ->
            snapshot.copy(
                sessions = snapshot.sessions.map { session ->
                    if (
                        session.id == sessionId &&
                        session.status == PracticeSessionStatus.ACTIVE
                    ) {
                        session.copy(
                            endedAtMillis = wallClock().coerceAtLeast(session.startedAtMillis),
                            status = PracticeSessionStatus.INTERRUPTED,
                        )
                    } else {
                        session
                    }
                },
            )
        }
    }

    fun closeInterruptedSessions(): String? {
        val state = stateProvider()
        val interrupted = interruptOpenSessions(state.snapshot, wallClock())
        if (interrupted == state.snapshot) return null
        updateSnapshot { interrupted }
        return "A practice session interrupted by the previous app exit was closed safely."
    }

    fun logEvent(
        task: PracticeTask?,
        message: String,
        videoUri: String? = null,
        videoPath: String? = null,
        videoFileName: String? = null,
        sessionIdOverride: String? = null,
    ) {
        val state = stateProvider()
        val now = wallClock()
        val event = PracticeEvent(
            id = UUID.randomUUID().toString(),
            date = dateKeyForMillis(now),
            taskId = task?.id,
            taskTitle = task?.title ?: "Practice session",
            event = message,
            atMillis = now,
            videoUri = videoUri,
            videoPath = videoPath,
            videoFileName = videoFileName,
            childProfileId = profileProvider().id,
            sessionId = sessionIdOverride ?: state.activeSessionId,
        )
        var droppedRecordingPaths = emptySet<String>()
        updateSnapshot { snapshot ->
            val combined = snapshot.events + event
            val retained = combined.takeLast(MAX_PRACTICE_EVENTS)
            val retainedPaths = retained.mapNotNull(PracticeEvent::videoPath).toSet()
            droppedRecordingPaths = combined
                .dropLast(retained.size)
                .mapNotNull(PracticeEvent::videoPath)
                .filterNot(retainedPaths::contains)
                .toSet()
            snapshot.copy(events = retained)
        }
        droppedRecordingPaths.forEach(deleteManagedRecordingFile)
    }

    fun taskFor(id: String?): PracticeTask? =
        stateProvider().snapshot.tasks.firstOrNull { it.id == id }

    private fun updateDailyProgress(transform: (DailyProgress) -> DailyProgress) {
        val state = stateProvider()
        val date = state.currentDate
        val profile = profileProvider()
        updateSnapshot { snapshot ->
            val current = snapshot.progressByDate[date] ?: DailyProgress(
                date = date,
                childProfileId = profile.id,
            )
            snapshot.copy(
                progressByDate = snapshot.progressByDate + (date to transform(current)),
            )
        }
    }
}

internal fun interruptOpenSessions(
    snapshot: PracticeSnapshot,
    atMillis: Long,
): PracticeSnapshot {
    val interrupted = snapshot.sessions.map { session ->
        if (session.status == PracticeSessionStatus.ACTIVE) {
            session.copy(
                endedAtMillis = atMillis.coerceAtLeast(session.startedAtMillis),
                status = PracticeSessionStatus.INTERRUPTED,
            )
        } else {
            session
        }
    }
    return if (interrupted == snapshot.sessions) {
        snapshot
    } else {
        snapshot.copy(sessions = interrupted)
    }
}
