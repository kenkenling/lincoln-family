package com.practicepartner.music

import org.junit.Assert.assertEquals
import org.junit.Test

class TodayPageTest {
    @Test
    fun welcomeCopyUsesSupportiveAbsenceThresholds() {
        assertEquals("Hi, Maya!", todayWelcomeGreeting("Maya Chen", 0))
        assertEquals("Welcome back, Maya!", todayWelcomeGreeting("Maya Chen", 1))
        assertEquals(
            "Welcome back! Let's pick up where you left off.",
            todayWelcomeGreeting("Maya Chen", 2),
        )
        assertEquals(
            "Welcome back! Let's pick up where you left off.",
            todayWelcomeGreeting("Maya Chen", 6),
        )
        assertEquals(
            "It's great to see you again! Ready for some music?",
            todayWelcomeGreeting("Maya Chen", 7),
        )
        assertEquals("Hi, Musician!", todayWelcomeGreeting("  ", 0))
    }

    @Test
    fun weeklyDotsKeepMondayToSundayLabelsAndPracticePattern() {
        val weekly = WeeklyPracticeSummary(
            startDate = "2026-10-26",
            endDate = "2026-11-01",
            days = listOf(
                WeeklyPracticeDay("2026-10-26", practiced = true, seconds = 300L),
                WeeklyPracticeDay("2026-10-27", practiced = false, seconds = 0L),
                WeeklyPracticeDay("2026-10-28", practiced = true, seconds = 120L),
                WeeklyPracticeDay("2026-10-29", practiced = false, seconds = 0L),
                WeeklyPracticeDay("2026-10-30", practiced = true, seconds = 240L),
                WeeklyPracticeDay("2026-10-31", practiced = false, seconds = 0L),
                WeeklyPracticeDay("2026-11-01", practiced = true, seconds = 180L),
            ),
        )

        val dots = weeklyDotItems(weekly)

        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), dots.map { it.dayLabel })
        assertEquals(
            listOf(true, false, true, false, true, false, true),
            dots.map { it.practiced },
        )
        assertEquals(
            "A gentle pattern, not a streak—rest days are okay.",
            WEEKLY_PATTERN_COPY,
        )
    }
}
