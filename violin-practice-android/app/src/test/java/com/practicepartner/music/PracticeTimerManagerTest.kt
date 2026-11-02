package com.practicepartner.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeTimerManagerTest {
    @Test
    fun tracksElapsedTimeAndPersistsEveryFiveSeconds() {
        var now = 1_000L
        val task = PracticeTask(
            id = "assignment",
            childProfileId = "child",
            assignedDate = DateKey("2026-11-01"),
            title = "Scales",
            source = "",
            sourceType = SourceType.NONE,
            startSeconds = 0,
            endSeconds = null,
            repetitions = 1,
            goalType = PracticeGoalType.TIME,
            targetSeconds = 5,
        )
        val session = PracticeSession(
            id = "session",
            childProfileId = "child",
            assignmentId = task.id,
            assignmentTitle = task.title,
            instrument = "Violin",
            date = DateKey("2026-11-01"),
            mode = PracticeMode.PLAY_YOURSELF,
            startedAtMillis = 100L,
        )
        var snapshot = PracticeSnapshot(
            tasks = listOf(task),
            sessions = listOf(session),
        )
        val ticker = ManualPracticeTimerTicker()
        val manager = PracticeTimerManager(
            scope = CoroutineScope(Dispatchers.Unconfined),
            elapsedRealtime = { now },
            profileProvider = {
                ChildProfile(id = "child", name = "Lincoln", instrument = "Violin")
            },
            dateProvider = { DateKey("2026-11-01") },
            activeTaskProvider = { task },
            activeSessionIdProvider = { session.id },
            updateSnapshot = { transform -> snapshot = transform(snapshot) },
            onTick = {},
            ticker = ticker,
        )

        manager.start(elapsedSeconds = 2L, sessionElapsedSeconds = 1L)
        now += 3_000L
        ticker.tick()

        assertEquals(5L, manager.elapsed.value.elapsedSeconds)
        assertEquals(4L, manager.elapsed.value.sessionElapsedSeconds)
        assertEquals(5L, snapshot.progressByDate.getValue(DateKey("2026-11-01")).elapsedSeconds)
        assertEquals(4L, snapshot.sessions.single().elapsedSeconds)
        assertFalse(task.id in snapshot.progressByDate.getValue(DateKey("2026-11-01")).completedTaskIds)

        now += 1_000L
        val paused = manager.pause()

        assertEquals(6L, paused?.elapsedSeconds)
        assertEquals(5L, snapshot.sessions.single().elapsedSeconds)
        assertTrue(task.id in snapshot.progressByDate.getValue(DateKey("2026-11-01")).completedTaskIds)
        assertFalse(manager.isRunning)
    }

    @Test
    fun resetCancelsTickerAndPauseIsNoOpWhenStopped() {
        val ticker = ManualPracticeTimerTicker()
        val manager = PracticeTimerManager(
            scope = CoroutineScope(Dispatchers.Unconfined),
            elapsedRealtime = { 0L },
            profileProvider = {
                ChildProfile(id = "child", name = "Lincoln", instrument = "Violin")
            },
            dateProvider = { DateKey("2026-11-01") },
            activeTaskProvider = { null },
            activeSessionIdProvider = { null },
            updateSnapshot = {},
            onTick = {},
            ticker = ticker,
        )

        manager.start(elapsedSeconds = 4L, sessionElapsedSeconds = 2L)
        manager.reset(elapsedSeconds = 9L)

        assertTrue(ticker.job.isCancelled)
        assertEquals(PracticeTimerReading(9L, 0L), manager.elapsed.value)
        assertNull(manager.pause())
    }
}

private class ManualPracticeTimerTicker : PracticeTimerTicker {
    lateinit var job: Job
        private set
    private var onTick: (() -> Unit)? = null

    override fun start(scope: CoroutineScope, onTick: () -> Unit): Job {
        this.onTick = onTick
        return Job().also { job = it }
    }

    fun tick() {
        onTick?.invoke()
    }
}
