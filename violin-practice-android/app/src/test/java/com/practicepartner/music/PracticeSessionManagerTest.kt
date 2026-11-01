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
            assignedDate = "2026-11-01",
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
            currentDate = "2026-11-01",
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
        assertTrue(state.snapshot.events.any { it.event.contains("Repetition 1") })
    }

    @Test
    fun refusesToFinishWhileRecording() {
        var state = PracticeUiState(
            snapshot = PracticeSnapshot(),
            activeChildProfileId = "child",
            currentDate = "2026-11-01",
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
