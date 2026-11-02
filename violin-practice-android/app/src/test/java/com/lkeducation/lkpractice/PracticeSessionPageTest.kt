package com.lkeducation.lkpractice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeSessionPageTest {
    @Test
    fun stepIndicatorOnlyAppearsWhenTheStudentHasAChoice() {
        assertFalse(
            shouldShowPracticeModeStepIndicator(
                listOf(PracticeMode.PLAY_YOURSELF),
            ),
        )
        assertTrue(
            shouldShowPracticeModeStepIndicator(
                listOf(PracticeMode.LISTEN, PracticeMode.PLAY_YOURSELF),
            ),
        )
    }
}
