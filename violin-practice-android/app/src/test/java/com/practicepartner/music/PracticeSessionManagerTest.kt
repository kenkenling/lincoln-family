package com.practicepartner.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeSessionManagerTest {
    @Test
    fun startRepeatAndFinishUpdatesOneSession() {
        val task = PracticeTask(
            id = "assignment",
            childProfileId = "child",
            instrument = "Violin",
            assignedDate = DateKey("2026-11-01"),
            title = "Scales",
            source = "",
            sourceType = SourceType.NONE,
            startSeconds = 0,
            endSeconds = null,
            repetitions = 1,
        )
        var state = PracticeUiState(
            snapshot = PracticeSnapshot(tasks = listOf(task)),
            activeChildProfileId = "child",
            currentDate = DateKey("2026-11-01"),
        )
        val manager = manager(
            state = { state },
            updateState = { transform -> state = transform(state) },
            updateSnapshot = { transform ->
                state = state.copy(snapshot = transform(state.snapshot))
            },
            startTimer = {
                state = state.copy(running = true)
            },
            pauseTimer = {
                state = state.copy(running = false, elapsedSeconds = 12L)
            },
        )

        manager.startPractice(task)
        manager.recordRepetition(task)
        val finished = manager.finishPractice()

        assertTrue(finished)
        assertFalse(state.running)
        assertEquals(1, state.todayProgress.repetitionsFor(task.id))
        assertEquals(null, state.activeTaskId)
        assertNotNull(state.pendingReflectionSessionId)
        assertEquals(PracticeSessionStatus.COMPLETED, state.snapshot.sessions.single().status)
        assertEquals(
            MasteryStatus.NOT_ASSESSED,
            state.snapshot.sessions.single().masteryStatus,
        )
        assertEquals(
            setOf(PracticeMode.PLAY_YOURSELF),
            state.snapshot.sessions.single().modesUsed,
        )
        assertTrue(state.snapshot.events.any { it.event.contains("Repetition 1") })

        manager.submitReflection(PracticeReflection.PROUD, studentConfident = true)

        val reflectedSession = state.snapshot.sessions.single()
        assertEquals(PracticeSessionStatus.COMPLETED, reflectedSession.status)
        assertEquals(PracticeReflection.PROUD, reflectedSession.reflection)
        assertEquals(MasteryStatus.STUDENT_CONFIDENT, reflectedSession.masteryStatus)
        assertTrue(
            state.snapshot.events.any {
                it.event == "Confidence: Student feels confident"
            },
        )
    }

    @Test
    fun switchModeTracksExploredStepsWithoutResettingPractice() {
        val task = PracticeTask(
            id = "assignment",
            childProfileId = "child",
            instrument = "Violin",
            assignedDate = DateKey("2026-11-01"),
            title = "Boreas",
            source = "https://youtu.be/VZIWCt-jW6g",
            sourceType = SourceType.YOUTUBE,
            startSeconds = 0,
            endSeconds = null,
            repetitions = 3,
            enabledModes = PracticeMode.entries.toSet(),
        )
        var state = PracticeUiState(
            snapshot = PracticeSnapshot(tasks = listOf(task)),
            activeChildProfileId = "child",
            currentDate = DateKey("2026-11-01"),
            mode = PracticeMode.LISTEN,
            elapsedSeconds = 40L,
        )
        val manager = manager(
            state = { state },
            updateState = { transform -> state = transform(state) },
            updateSnapshot = { transform ->
                state = state.copy(snapshot = transform(state.snapshot))
            },
            startTimer = {
                state = state.copy(
                    running = true,
                    activeSessionElapsedSeconds = 9L,
                )
            },
        )

        manager.startPractice(task)
        manager.recordRepetition(task)
        val sessionId = state.activeSessionId
        val elapsedBeforeSwitch = state.elapsedSeconds
        val sessionElapsedBeforeSwitch = state.activeSessionElapsedSeconds

        assertTrue(manager.switchMode(PracticeMode.LISTEN_AND_PLAY))
        assertTrue(manager.switchMode(PracticeMode.PLAY_YOURSELF))
        assertFalse(manager.switchMode(PracticeMode.PLAY_YOURSELF))

        val session = state.snapshot.sessions.single()
        assertEquals(sessionId, state.activeSessionId)
        assertTrue(state.running)
        assertEquals(PracticeMode.PLAY_YOURSELF, state.mode)
        assertEquals(elapsedBeforeSwitch, state.elapsedSeconds)
        assertEquals(sessionElapsedBeforeSwitch, state.activeSessionElapsedSeconds)
        assertEquals(1, state.todayProgress.repetitionsFor(task.id))
        assertEquals(1, session.repetitionsCompleted)
        assertEquals(PracticeMode.PLAY_YOURSELF, session.mode)
        assertEquals(PracticeMode.entries.toSet(), session.modesUsed)
        assertEquals(
            listOf("Switched to Play along", "Switched to Play solo"),
            state.snapshot.events
                .map(PracticeEvent::event)
                .filter { it.startsWith("Switched to") },
        )
    }

    @Test
    fun runtimeHistoryKeepsMoreThanTheOldFiveHundredRecordCap() {
        val task = PracticeTask(
            id = "assignment",
            childProfileId = "child",
            instrument = "Violin",
            assignedDate = DateKey("2026-11-01"),
            title = "Scales",
            source = "",
            sourceType = SourceType.NONE,
            startSeconds = 0,
            endSeconds = null,
            repetitions = 1,
        )
        val events = List(500) { index ->
            PracticeEvent(
                id = "event-$index",
                date = DateKey("2026-11-01"),
                taskId = task.id,
                taskTitle = task.title,
                event = "Event $index",
                atMillis = index.toLong(),
                childProfileId = "child",
            )
        }
        val sessions = List(500) { index ->
            PracticeSession(
                id = "session-$index",
                childProfileId = "child",
                assignmentId = task.id,
                assignmentTitle = task.title,
                instrument = "Violin",
                date = DateKey("2026-11-01"),
                mode = PracticeMode.PLAY_YOURSELF,
                startedAtMillis = index.toLong(),
            )
        }
        var state = PracticeUiState(
            snapshot = PracticeSnapshot(
                tasks = listOf(task),
                events = events,
                sessions = sessions,
            ),
            activeChildProfileId = "child",
            currentDate = DateKey("2026-11-01"),
        )
        val manager = manager(
            state = { state },
            updateState = { transform -> state = transform(state) },
            updateSnapshot = { transform ->
                state = state.copy(snapshot = transform(state.snapshot))
            },
        )

        manager.startPractice(task)

        assertEquals(501, state.snapshot.events.size)
        assertEquals(501, state.snapshot.sessions.size)
        assertEquals("event-0", state.snapshot.events.first().id)
        assertEquals("session-0", state.snapshot.sessions.first().id)
    }

    @Test
    fun refusesToFinishWhileRecording() {
        var state = PracticeUiState(
            snapshot = PracticeSnapshot(),
            activeChildProfileId = "child",
            currentDate = DateKey("2026-11-01"),
            recordingActive = true,
        )
        val messages = mutableListOf<String>()
        val manager = manager(
            state = { state },
            updateState = { transform -> state = transform(state) },
            updateSnapshot = { transform ->
                state = state.copy(snapshot = transform(state.snapshot))
            },
            setMessage = messages::add,
        )

        assertFalse(manager.finishPractice())
        assertEquals("Stop the current recording before finishing practice.", messages.single())
    }

    @Test
    fun studentReflectionNeverRemovesTeacherApproval() {
        val session = PracticeSession(
            id = "session",
            childProfileId = "child",
            assignmentId = "assignment",
            assignmentTitle = "Scales",
            instrument = "Violin",
            date = DateKey("2026-11-01"),
            mode = PracticeMode.PLAY_YOURSELF,
            startedAtMillis = 500L,
            status = PracticeSessionStatus.COMPLETED,
            masteryStatus = MasteryStatus.TEACHER_APPROVED,
        )
        var state = PracticeUiState(
            snapshot = PracticeSnapshot(sessions = listOf(session)),
            activeChildProfileId = "child",
            currentDate = DateKey("2026-11-01"),
            pendingReflectionSessionId = session.id,
        )
        val manager = manager(
            state = { state },
            updateState = { transform -> state = transform(state) },
            updateSnapshot = { transform ->
                state = state.copy(snapshot = transform(state.snapshot))
            },
        )

        manager.submitReflection(
            PracticeReflection.MAKING_PROGRESS,
            studentConfident = false,
        )

        assertEquals(
            MasteryStatus.TEACHER_APPROVED,
            state.snapshot.sessions.single().masteryStatus,
        )
    }

    private fun manager(
        state: () -> PracticeUiState,
        updateState: (((PracticeUiState) -> PracticeUiState) -> Unit),
        updateSnapshot: (((PracticeSnapshot) -> PracticeSnapshot) -> Unit),
        startTimer: (String) -> Unit = {},
        pauseTimer: (Boolean) -> Unit = {},
        setMessage: (String) -> Unit = {},
    ): PracticeSessionManager = PracticeSessionManager(
        profileProvider = {
            ChildProfile(id = "child", name = "Lincoln", instrument = "Violin")
        },
        stateProvider = state,
        wallClock = { 1_000L },
        canReadContentUri = { true },
        updateState = updateState,
        updateSnapshot = updateSnapshot,
        setMessage = setMessage,
        startTimer = startTimer,
        pauseTimer = pauseTimer,
        prepareSessionTimer = {},
        resetSessionTimer = {},
        deleteManagedRecordingFile = {},
    )
}
