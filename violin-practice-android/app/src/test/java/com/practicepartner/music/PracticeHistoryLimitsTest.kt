package com.practicepartner.music

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeHistoryLimitsTest {
    @Test
    fun warningStartsAtEightyPercentOfEitherLimit() {
        assertNull(
            practiceHistoryCapacityWarning(
                eventCount = PRACTICE_EVENT_WARNING_COUNT - 1,
                sessionCount = PRACTICE_SESSION_WARNING_COUNT - 1,
            ),
        )

        val eventWarning = practiceHistoryCapacityWarning(
            eventCount = PRACTICE_EVENT_WARNING_COUNT,
            sessionCount = 0,
        )
        val sessionWarning = practiceHistoryCapacityWarning(
            eventCount = 0,
            sessionCount = PRACTICE_SESSION_WARNING_COUNT,
        )

        assertNotNull(eventWarning)
        assertNotNull(sessionWarning)
        assertTrue(eventWarning!!.contains("Export a profile archive soon"))
        assertTrue(sessionWarning!!.contains("Export a profile archive soon"))
    }

    @Test
    fun warningExplainsWhenTheCurrentLimitIsReached() {
        val warning = practiceHistoryCapacityWarning(
            eventCount = MAX_PRACTICE_EVENTS,
            sessionCount = MAX_PRACTICE_SESSIONS,
        )

        assertNotNull(warning)
        assertTrue(warning!!.contains("at its current limit"))
        assertTrue(warning.contains("keeps the newest records"))
    }
}
