package com.practicepartner.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun youtubeChannelLinksAreNormalizedAndVideoLinksAreRejected() {
        assertEquals(
            "https://www.youtube.com/@lincoln",
            normalizedYoutubeChannelUrl("@lincoln"),
        )
        assertEquals(
            "https://youtube.com/channel/UC123",
            normalizedYoutubeChannelUrl("youtube.com/channel/UC123"),
        )
        assertEquals("", normalizedYoutubeChannelUrl(" "))
        assertNull(normalizedYoutubeChannelUrl("https://youtu.be/VZIWCt-jW6g"))
        assertNull(
            normalizedYoutubeChannelUrl(
                "https://www.youtube.com/watch?v=VZIWCt-jW6g",
            ),
        )
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
    fun accountEmailIsNormalizedAndValidated() {
        assertEquals(
            "kenkenlincoln@gmail.com",
            normalizedAccountEmail(" KenKenLincoln@gmail.com "),
        )
        assertEquals("", normalizedAccountEmail(" "))
        assertNull(normalizedAccountEmail("not-an-email"))
    }
}
