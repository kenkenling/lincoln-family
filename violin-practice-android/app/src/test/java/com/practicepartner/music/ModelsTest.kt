package com.practicepartner.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun youtubeParserAcceptsSupportedFormsOnly() {
        assertEquals("VZIWCt-jW6g", youtubeVideoId("https://youtu.be/VZIWCt-jW6g"))
        assertEquals(
            "c79nrCCcKuM",
            youtubeVideoId("https://www.youtube.com/watch?v=c79nrCCcKuM&feature=share"),
        )
        assertEquals(
            "WvVyLWpV6-4",
            youtubeVideoId("https://youtube.com/shorts/WvVyLWpV6-4"),
        )
        assertNull(youtubeVideoId("https://example.com/watch?v=VZIWCt-jW6g"))
    }

    @Test
    fun repetitionsCompleteAssignmentAtTarget() {
        val task = PracticeTask(
            id = "task",
            assignedDate = "2026-10-30",
            title = "Shifting",
            source = "https://youtu.be/VZIWCt-jW6g",
            sourceType = SourceType.YOUTUBE,
            startSeconds = 0,
            endSeconds = null,
            repetitions = 2,
        )

        val once = DailyProgress("2026-10-30").recordRepetition(task)
        assertEquals(1, once.repetitionsFor(task.id))
        assertFalse(task.id in once.completedTaskIds)

        val twice = once.recordRepetition(task)
        assertEquals(2, twice.repetitionsFor(task.id))
        assertTrue(task.id in twice.completedTaskIds)
    }

    @Test
    fun removingTaskAlsoRemovesItsProgress() {
        val progress = DailyProgress(
            date = "2026-10-30",
            completedTaskIds = setOf("task"),
            repetitionsByTask = mapOf("task" to 3),
        ).withoutTask("task")

        assertFalse("task" in progress.completedTaskIds)
        assertEquals(0, progress.repetitionsFor("task"))
    }

    @Test
    fun reopeningCompletedTaskKeepsEarlierRepetitions() {
        val task = PracticeTask(
            id = "task",
            assignedDate = "2026-10-31",
            title = "Tremolo",
            source = "https://youtu.be/VZIWCt-jW6g",
            sourceType = SourceType.YOUTUBE,
            startSeconds = 0,
            endSeconds = 20,
            repetitions = 4,
        )
        val completed = DailyProgress("2026-10-31").toggleCompleted(task)
        val reopened = completed.toggleCompleted(task)

        assertFalse(task.id in reopened.completedTaskIds)
        assertEquals(3, reopened.repetitionsFor(task.id))
    }

    @Test
    fun blankReferenceCreatesAValidNoReferenceAssignment() {
        assertEquals(SourceType.NONE, sourceTypeFor(""))
        assertEquals(SourceType.NONE, sourceTypeFor("   "))
    }

    @Test
    fun snapshotScopingMigratesLegacyRowsAndRemovesAnotherChildsData() {
        val current = ChildProfile(
            id = "current-child",
            name = "Current",
            instrument = "Piano",
        )
        val legacyTask = PracticeTask(
            id = "legacy-task",
            assignedDate = "2026-10-31",
            title = "C major scale",
            source = "",
            sourceType = SourceType.NONE,
            startSeconds = 0,
            endSeconds = null,
            repetitions = 3,
        )
        val currentTask = legacyTask.copy(
            id = "current-task",
            title = "Arpeggio",
            childProfileId = current.id,
            instrument = current.instrument,
        )
        val otherTask = legacyTask.copy(
            id = "other-task",
            title = "Other child",
            childProfileId = "other-child",
            instrument = "Violin",
        )
        val legacySession = PracticeSession(
            id = "legacy-session",
            childProfileId = "",
            assignmentId = legacyTask.id,
            assignmentTitle = legacyTask.title,
            instrument = "",
            date = "2026-10-31",
            mode = PracticeMode.PLAY_YOURSELF,
            startedAtMillis = 10L,
        )
        val otherSession = legacySession.copy(
            id = "other-session",
            childProfileId = "other-child",
            assignmentId = otherTask.id,
        )
        val scoped = PracticeSnapshot(
            tasks = listOf(legacyTask, currentTask, otherTask),
            progressByDate = mapOf(
                "2026-10-31" to DailyProgress(
                    date = "2026-10-31",
                    completedTaskIds = setOf(legacyTask.id, otherTask.id),
                    repetitionsByTask = mapOf(
                        legacyTask.id to 3,
                        otherTask.id to 2,
                    ),
                ),
            ),
            events = listOf(
                PracticeEvent(
                    id = "legacy-event",
                    date = "2026-10-31",
                    taskId = legacyTask.id,
                    taskTitle = legacyTask.title,
                    event = "Done",
                    atMillis = 20L,
                    sessionId = legacySession.id,
                ),
                PracticeEvent(
                    id = "other-event",
                    date = "2026-10-31",
                    taskId = otherTask.id,
                    taskTitle = otherTask.title,
                    event = "Done",
                    atMillis = 30L,
                    childProfileId = "other-child",
                    sessionId = otherSession.id,
                ),
            ),
            sessions = listOf(legacySession, otherSession),
        ).scopedTo(current)

        assertEquals(setOf("legacy-task", "current-task"), scoped.tasks.map { it.id }.toSet())
        assertTrue(scoped.tasks.all { it.childProfileId == current.id })
        assertTrue(scoped.tasks.all { it.instrument == current.instrument })
        assertEquals(setOf("legacy-task"), scoped.progressByDate.getValue("2026-10-31").completedTaskIds)
        assertEquals(listOf("legacy-event"), scoped.events.map(PracticeEvent::id))
        assertEquals(listOf("legacy-session"), scoped.sessions.map(PracticeSession::id))
        assertEquals(current.id, scoped.events.single().childProfileId)
        assertEquals(current.id, scoped.sessions.single().childProfileId)
    }

    @Test
    fun parentPinMustBeExactlyFourDigits() {
        assertTrue(isValidAdultPin("0319"))
        assertFalse(isValidAdultPin("319"))
        assertFalse(isValidAdultPin("03190"))
        assertFalse(isValidAdultPin("03a9"))
    }

    @Test
    fun timeGoalUsesAllSessionsForTheAssignment() {
        val task = PracticeTask(
            id = "time-task",
            assignedDate = "2026-11-01",
            title = "Repertoire passage",
            source = "",
            sourceType = SourceType.NONE,
            startSeconds = 0,
            endSeconds = null,
            repetitions = 1,
            goalType = PracticeGoalType.TIME,
            targetSeconds = 5 * 60,
        )
        val state = PracticeUiState(
            snapshot = PracticeSnapshot(
                tasks = listOf(task),
                sessions = listOf(
                    PracticeSession(
                        id = "one",
                        childProfileId = "student",
                        assignmentId = task.id,
                        assignmentTitle = task.title,
                        instrument = "Violin",
                        date = task.assignedDate,
                        mode = PracticeMode.PLAY_YOURSELF,
                        startedAtMillis = 1L,
                        elapsedSeconds = 120,
                        status = PracticeSessionStatus.ENDED,
                    ),
                    PracticeSession(
                        id = "two",
                        childProfileId = "student",
                        assignmentId = task.id,
                        assignmentTitle = task.title,
                        instrument = "Violin",
                        date = task.assignedDate,
                        mode = PracticeMode.PLAY_YOURSELF,
                        startedAtMillis = 2L,
                        elapsedSeconds = 180,
                        status = PracticeSessionStatus.COMPLETED,
                    ),
                ),
            ),
            activeChildProfileId = "student",
            currentDate = task.assignedDate,
        )

        assertTrue(state.goalMet(task))
        assertEquals("5 min / 5 min", state.progressLabel(task))
    }

    @Test
    fun weeklySummaryKeepsRestDaysWithoutInventingAStreak() {
        val state = PracticeUiState(
            snapshot = PracticeSnapshot(
                progressByDate = mapOf(
                    "2026-10-26" to DailyProgress(
                        date = "2026-10-26",
                        elapsedSeconds = 300,
                    ),
                    "2026-10-29" to DailyProgress(
                        date = "2026-10-29",
                        elapsedSeconds = 420,
                    ),
                ),
            ),
            activeChildProfileId = "student",
            currentDate = "2026-11-01",
            summaryDate = "2026-11-01",
        )

        val week = state.weeklySummary()

        assertEquals("2026-10-26", week.startDate)
        assertEquals("2026-11-01", week.endDate)
        assertEquals(7, week.days.size)
        assertEquals(2, week.practicedDays)
        assertEquals(720L, week.totalSeconds)
    }

    @Test
    fun instrumentTemplatesStaySmallAndInstrumentSpecific() {
        val violin = assignmentTemplatesFor("Violin")
        val piano = assignmentTemplatesFor("Piano")
        val generic = assignmentTemplatesFor("Cello")

        assertEquals(3, violin.size)
        assertEquals(3, piano.size)
        assertEquals(3, generic.size)
        assertTrue(violin.first().title.contains("bow", ignoreCase = true))
        assertTrue(piano.first().title.contains("arpeggios", ignoreCase = true))
        assertEquals("Warm-up", generic.first().title)
        assertTrue(
            (violin + piano + generic).all {
                it.enabledModes == setOf(PracticeMode.PLAY_YOURSELF)
            },
        )
    }

    @Test
    fun archivedAssignmentIsHiddenFromTodayButKeptInSummaryHistory() {
        val active = PracticeTask(
            id = "active",
            assignedDate = "2026-11-01",
            title = "Active",
            source = "",
            sourceType = SourceType.NONE,
            startSeconds = 0,
            endSeconds = null,
            repetitions = 3,
        )
        val archived = active.copy(
            id = "archived",
            title = "Archived",
            archivedAtMillis = 1_800_000_000_000,
            revision = 2,
        )
        val state = PracticeUiState(
            snapshot = PracticeSnapshot(tasks = listOf(active, archived)),
            activeChildProfileId = "student",
            currentDate = "2026-11-01",
            summaryDate = "2026-11-01",
        )

        assertEquals(listOf("active"), state.todayTasks.map(PracticeTask::id))
        assertEquals(
            setOf("active", "archived"),
            state.summaryTasks.map(PracticeTask::id).toSet(),
        )
        assertEquals(listOf("archived"), state.archivedTasksForDate("2026-11-01").map { it.id })
    }

    @Test
    fun daysSinceLastPracticeUsesLatestRealActivityWithoutFutureDates() {
        fun session(id: String, date: String) = PracticeSession(
            id = id,
            childProfileId = "student",
            assignmentId = "assignment",
            assignmentTitle = "Scales",
            instrument = "Violin",
            date = date,
            mode = PracticeMode.PLAY_YOURSELF,
            startedAtMillis = 1L,
        )

        fun days(snapshot: PracticeSnapshot) = PracticeUiState(
            snapshot = snapshot,
            activeChildProfileId = "student",
            currentDate = "2026-11-01",
        ).daysSinceLastPractice

        assertEquals(0, days(PracticeSnapshot()))
        assertEquals(
            0,
            days(PracticeSnapshot(sessions = listOf(session("today", "2026-11-01")))),
        )
        assertEquals(
            1,
            days(PracticeSnapshot(sessions = listOf(session("yesterday", "2026-10-31")))),
        )
        assertEquals(
            2,
            days(
                PracticeSnapshot(
                    progressByDate = mapOf(
                        "2026-10-30" to DailyProgress(
                            date = "2026-10-30",
                            elapsedSeconds = 60L,
                        ),
                    ),
                ),
            ),
        )
        assertEquals(
            6,
            days(PracticeSnapshot(sessions = listOf(session("six-days", "2026-10-26")))),
        )
        assertEquals(
            7,
            days(
                PracticeSnapshot(
                    progressByDate = mapOf(
                        "2026-11-01" to DailyProgress(date = "2026-11-01"),
                        "2026-10-25" to DailyProgress(
                            date = "2026-10-25",
                            repetitionsByTask = mapOf("assignment" to 1),
                        ),
                    ),
                    sessions = listOf(session("future", "2026-11-02")),
                ),
            ),
        )
    }

    @Test
    fun dateSpecificCollectionsAreCachedUntilStateIsCopied() {
        val today = "2026-11-01"
        val earlier = "2026-10-31"
        val todayTask = PracticeTask(
            id = "today-task",
            assignedDate = today,
            title = "Today",
            source = "",
            sourceType = SourceType.NONE,
            startSeconds = 0,
            endSeconds = null,
            repetitions = 3,
        )
        val earlierTask = todayTask.copy(
            id = "earlier-task",
            assignedDate = earlier,
            title = "Earlier",
        )
        val todayEvent = PracticeEvent(
            id = "today-event",
            date = today,
            taskId = todayTask.id,
            taskTitle = todayTask.title,
            event = "Started",
            atMillis = 1L,
        )
        val todaySession = PracticeSession(
            id = "today-session",
            childProfileId = "student",
            assignmentId = todayTask.id,
            assignmentTitle = todayTask.title,
            instrument = "Violin",
            date = today,
            mode = PracticeMode.PLAY_YOURSELF,
            startedAtMillis = 1L,
        )
        val state = PracticeUiState(
            snapshot = PracticeSnapshot(
                tasks = listOf(todayTask, earlierTask),
                events = listOf(todayEvent),
                sessions = listOf(todaySession),
            ),
            activeChildProfileId = "student",
            currentDate = today,
            summaryDate = earlier,
        )

        assertSame(state.todayTasks, state.todayTasks)
        assertSame(state.todayEvents, state.todayEvents)
        assertSame(state.todaySessions, state.todaySessions)
        assertSame(state.summaryTasks, state.summaryTasks)
        assertSame(state.summaryEvents, state.summaryEvents)
        assertSame(state.summarySessions, state.summarySessions)
        assertSame(state.historyDates, state.historyDates)

        val copied = state.copy(currentDate = earlier)

        assertNotSame(state.todayTasks, copied.todayTasks)
        assertEquals(listOf(earlierTask), copied.todayTasks)
        assertEquals(listOf(earlier, today), copied.historyDates)
    }
}
