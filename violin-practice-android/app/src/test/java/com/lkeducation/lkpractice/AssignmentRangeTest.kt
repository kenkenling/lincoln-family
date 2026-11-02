package com.lkeducation.lkpractice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AssignmentRangeTest {
    @Test
    fun multiDayAssignmentAppearsEveryDayWithIndependentProgress() {
        val monday = DateKey("2026-11-02")
        val tuesday = DateKey("2026-11-03")
        val wednesday = DateKey("2026-11-04")
        val task = assignment(
            id = "range",
            startDate = monday,
            endDate = wednesday,
        )
        val mondayProgress = DailyProgress(
            date = monday,
            completedTaskIds = setOf(task.id),
            repetitionsByTask = mapOf(task.id to task.repetitions),
        )
        val snapshot = PracticeSnapshot(
            tasks = listOf(task),
            progressByDate = mapOf(monday to mondayProgress),
        )

        val mondayState = state(snapshot, monday)
        val tuesdayState = state(snapshot, tuesday)

        assertEquals(listOf(task), mondayState.todayTasks)
        assertEquals(listOf(task), tuesdayState.todayTasks)
        assertTrue(mondayState.goalMet(task))
        assertFalse(tuesdayState.goalMet(task))
        assertEquals(0, tuesdayState.todayProgress.repetitionsFor(task.id))
        assertEquals(listOf(monday, tuesday, wednesday), mondayState.historyDates)

        val tuesdayCompleted = snapshot.copy(
            progressByDate = snapshot.progressByDate + (
                tuesday to DailyProgress(tuesday)
                    .recordRepetition(task)
                    .recordRepetition(task)
                    .recordRepetition(task)
                ),
        )

        assertTrue(state(tuesdayCompleted, tuesday).goalMet(task))
        assertTrue(state(tuesdayCompleted, monday).goalMet(task))
    }

    @Test
    fun rangesAreInclusiveAndCappedAtThirtyDays() {
        val start = DateKey("2026-11-02")
        val thirtiethDay = DateKey("2026-12-01")
        val thirtyFirstDay = DateKey("2026-12-02")

        assertEquals(30, assignmentRangeDayCount(start, thirtiethDay))
        assertTrue(isValidAssignmentDateRange(start, thirtiethDay))
        assertFalse(isValidAssignmentDateRange(start, thirtyFirstDay))
        assertFalse(isValidAssignmentDateRange(thirtiethDay, start))
        assertThrows(IllegalArgumentException::class.java) {
            assignment("too-long", start, thirtyFirstDay)
        }
    }

    @Test
    fun quickDateRangesUseCalendarWeekAndSevenInclusiveDays() {
        val wednesday = DateKey("2026-11-04")

        assertEquals(
            DateKey("2026-11-02") to DateKey("2026-11-08"),
            thisWeekAssignmentRange(wednesday),
        )
        assertEquals(
            wednesday to DateKey("2026-11-10"),
            nextSevenDaysAssignmentRange(wednesday),
        )
    }

    @Test
    fun recentSelfDirectedOptionsArePastUnarchivedAndLimitedToFive() {
        val today = DateKey("2026-11-02")
        val past = (0..6).map { offset ->
            val date = DateKey.from(LocalDate.of(2026, 10, 20).plusDays(offset.toLong()))
            assignment("past-$offset", date, date)
        }
        val archived = assignment(
            "archived",
            DateKey("2026-11-01"),
            DateKey("2026-11-01"),
        ).copy(archivedAtMillis = 10L)
        val current = assignment("today", today, today)
        val future = assignment(
            "future",
            DateKey("2026-11-03"),
            DateKey("2026-11-03"),
        )

        val options = state(
            PracticeSnapshot(tasks = past + archived + current + future),
            today,
        ).selfDirectedPracticeOptions

        assertEquals(5, options.size)
        assertEquals(
            listOf("past-6", "past-5", "past-4", "past-3", "past-2"),
            options.map(PracticeTask::id),
        )
    }

    @Test
    fun completedTodayAssignmentsBecomeKeepPracticingOptions() {
        val today = DateKey("2026-11-02")
        val todayTask = assignment("today", today, today)
        val pastTask = assignment(
            "past",
            DateKey("2026-11-01"),
            DateKey("2026-11-01"),
        )
        val progress = DailyProgress(
            date = today,
            completedTaskIds = setOf(todayTask.id),
            repetitionsByTask = mapOf(todayTask.id to todayTask.repetitions),
        )

        val options = state(
            PracticeSnapshot(
                tasks = listOf(pastTask, todayTask),
                progressByDate = mapOf(today to progress),
            ),
            today,
        ).selfDirectedPracticeOptions

        assertEquals(listOf("today", "past"), options.map(PracticeTask::id))
    }

    private fun assignment(
        id: String,
        startDate: DateKey,
        endDate: DateKey,
    ): PracticeTask = PracticeTask(
        id = id,
        startDate = startDate,
        endDate = endDate,
        title = "Scales $id",
        source = "",
        sourceType = SourceType.NONE,
        startSeconds = 0,
        endSeconds = null,
        repetitions = 3,
    )

    private fun state(
        snapshot: PracticeSnapshot,
        currentDate: DateKey,
    ): PracticeUiState = PracticeUiState(
        snapshot = snapshot,
        activeChildProfileId = "child",
        currentDate = currentDate,
    )
}
