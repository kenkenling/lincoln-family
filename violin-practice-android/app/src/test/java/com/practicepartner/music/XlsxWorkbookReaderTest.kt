package com.practicepartner.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class XlsxWorkbookReaderTest {
    @Test
    fun readsAssignmentsWrittenByCurrentTemplate() {
        val youtube = PracticeTask(
            id = "youtube",
            assignedDate = "2026-11-01",
            title = "Tremolo",
            source = "https://youtu.be/VZIWCt-jW6g",
            sourceType = SourceType.YOUTUBE,
            startSeconds = 4,
            endSeconds = 20,
            repetitions = 5,
            coachNote = "Stay relaxed.",
            whyImportant = "Build even bow motion.",
        )
        val local = youtube.copy(
            id = "local",
            title = "Teacher demonstration",
            source = "content://private/video",
            sourceType = SourceType.LOCAL,
        )
        val output = ByteArrayOutputStream()
        XlsxWorkbookWriter.write(
            output = output,
            sheets = PracticeWorkbookTemplate.sheets(
                profile = StudentProfile(id = "profile"),
                snapshot = PracticeSnapshot(tasks = listOf(youtube, local)),
                updatedAtMillis = 1_798_000_000_000,
            ),
        )

        val result = XlsxWorkbookReader.readAssignments(
            ByteArrayInputStream(output.toByteArray()),
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(2, result.assignments.size)
        assertEquals(youtube.source, result.assignments.first { it.id == "youtube" }.source)
        assertEquals("", result.assignments.first { it.id == "local" }.source)
    }
}
