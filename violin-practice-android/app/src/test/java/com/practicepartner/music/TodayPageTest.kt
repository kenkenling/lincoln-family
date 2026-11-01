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
}
