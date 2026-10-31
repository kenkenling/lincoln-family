package com.practicepartner.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingNamesTest {
    @Test
    fun recordingNameUsesSortablePracticeMetadata() {
        assertEquals(
            "2026-10-31 - Lincoln - Boreas Violin 2 - Take 01.mp4",
            practiceRecordingFileName(
                practiceDate = "2026-10-31",
                studentName = "Lincoln",
                assignmentTitle = "Boreas Violin 2",
                takeNumber = 1,
            ),
        )
    }

    @Test
    fun recordingNameRemovesUnsafeAndAmbiguousSeparators() {
        assertEquals(
            "2026-10-31 - Lincoln-Mom - Boreas-Violin 2-mm. 1-2 - Take 02.mp4",
            practiceRecordingFileName(
                practiceDate = "2026-10-31",
                studentName = " Lincoln / Mom ",
                assignmentTitle = "Boreas - Violin 2: mm. 1/2?",
                takeNumber = 2,
            ),
        )
    }

    @Test
    fun nextTakeUsesTheHighestExistingTakeForTheTaskAndDate() {
        val events = listOf(
            recordingEvent(
                id = "one",
                fileName = "2026-10-31 - Lincoln - Shifting - Take 01.mp4",
            ),
            recordingEvent(
                id = "three",
                fileName = "2026-10-31 - Lincoln - Shifting - Take 03.mp4",
            ),
            recordingEvent(
                id = "other-task",
                taskId = "tremolo",
                fileName = "2026-10-31 - Lincoln - Tremolo - Take 09.mp4",
            ),
        )

        assertEquals(
            4,
            nextRecordingTakeNumber(
                events = events,
                practiceDate = "2026-10-31",
                taskId = "shifting",
            ),
        )
    }

    @Test
    fun managedNameCheckAcceptsCurrentAndLegacyRecordingsOnly() {
        assertTrue(
            isManagedPracticeRecordingFileName(
                "2026-10-31 - Lincoln - Shifting - Take 01.mp4",
            ),
        )
        assertTrue(isManagedPracticeRecordingFileName("PracticePartner-1790000000000.mp4"))
        assertFalse(isManagedPracticeRecordingFileName("unrelated-video.mp4"))
        assertFalse(isManagedPracticeRecordingFileName("../PracticePartner-1790000000000.mp4"))
    }

    private fun recordingEvent(
        id: String,
        taskId: String = "shifting",
        fileName: String,
    ): PracticeEvent = PracticeEvent(
        id = id,
        date = "2026-10-31",
        taskId = taskId,
        taskTitle = "Shifting",
        event = "Practice take saved privately",
        atMillis = 1_798_000_000_000,
        videoUri = "content://practice/$id",
        videoPath = "/private/$fileName",
        videoFileName = fileName,
    )
}
