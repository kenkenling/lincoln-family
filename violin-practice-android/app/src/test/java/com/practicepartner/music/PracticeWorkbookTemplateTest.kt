package com.practicepartner.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeWorkbookTemplateTest {
    @Test
    fun workbookContainsTheFiveExpectedSheets() {
        val sheets = PracticeWorkbookTemplate.sheets(
            profile = StudentProfile(id = "profile"),
            snapshot = PracticeSnapshot(),
            updatedAtMillis = 1_798_000_000_000,
        )

        assertEquals(PracticeWorkbookTemplate.requiredSheetNames, sheets.map(WorkbookSheet::name))
    }

    @Test
    fun dailyPracticeSecondsAppearOncePerDateInsteadOfOncePerAssignment() {
        val tasks = listOf(
            PracticeTask(
                id = "one",
                assignedDate = "2026-10-31",
                title = "Shifting",
                source = "https://youtu.be/VZIWCt-jW6g",
                sourceType = SourceType.YOUTUBE,
                startSeconds = 0,
                endSeconds = 10,
                repetitions = 3,
            ),
            PracticeTask(
                id = "two",
                assignedDate = "2026-10-31",
                title = "Tremolo",
                source = "https://youtu.be/nq-50MmDmWY",
                sourceType = SourceType.YOUTUBE,
                startSeconds = 0,
                endSeconds = 10,
                repetitions = 2,
            ),
        )
        val sheets = PracticeWorkbookTemplate.sheets(
            profile = StudentProfile(id = "profile"),
            snapshot = PracticeSnapshot(
                tasks = tasks,
                progressByDate = mapOf(
                    "2026-10-31" to DailyProgress(
                        date = "2026-10-31",
                        repetitionsByTask = mapOf("one" to 2, "two" to 1),
                        elapsedSeconds = 125,
                    ),
                ),
            ),
            updatedAtMillis = 1_798_000_000_000,
        )

        val progress = sheets.single { it.name == "Daily Progress" }
        val summary = sheets.single { it.name == "Daily Summary" }

        assertFalse(progress.rows.first().contains("Total Practice Seconds"))
        assertEquals(2, summary.rows.size)
        assertEquals(125L, summary.rows[1][1])
        assertEquals(3, summary.rows[1][4])
    }

    @Test
    fun workbookRowsKeepPracticeMetadataButRemovePrivateDevicePaths() {
        val localTask = PracticeTask(
            id = "local-task",
            assignedDate = "2026-10-31",
            title = "Shifting",
            source = "content://private/reference",
            sourceType = SourceType.LOCAL,
            startSeconds = 3,
            endSeconds = 12,
            repetitions = 4,
            coachNote = "Relax the thumb.",
            whyImportant = "Smooth position changes.",
            sheetMusicUri = "content://private/sheet",
        )
        val youtubeTask = localTask.copy(
            id = "youtube-task",
            title = "Tremolo",
            source = "https://youtu.be/VZIWCt-jW6g",
            sourceType = SourceType.YOUTUBE,
            sheetMusicUri = null,
        )
        val snapshot = PracticeSnapshot(
            tasks = listOf(localTask, youtubeTask),
            events = listOf(
                PracticeEvent(
                    id = "event",
                    date = "2026-10-31",
                    taskId = "local-task",
                    taskTitle = "Shifting",
                    event = "Practice take saved privately",
                    atMillis = 1_798_000_000_000,
                    videoUri = "content://private/take",
                    videoPath = "/data/user/0/private/take.mp4",
                ),
            ),
        )

        val rendered = PracticeWorkbookTemplate.sheets(
            profile = StudentProfile(id = "profile-id"),
            snapshot = snapshot,
            updatedAtMillis = 1_798_000_001_000,
        ).flatMap(WorkbookSheet::rows).flatten().joinToString("|")

        assertTrue(rendered.contains("Relax the thumb."))
        assertTrue(rendered.contains("https://youtu.be/VZIWCt-jW6g"))
        assertFalse(rendered.contains("content://private"))
        assertFalse(rendered.contains("/data/user/0"))
    }
}
