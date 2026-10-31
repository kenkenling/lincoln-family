package com.practicepartner.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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

    @Test
    fun blankAssignmentIdIsGeneratedForTeacherCreatedRows() {
        val bytes = workbookBytesWithAssignmentRow(
            listOf(
                "",
                "2026-11-01",
                "Tremolo",
                "YOUTUBE",
                "https://youtu.be/VZIWCt-jW6g",
                0,
                20,
                4,
                "Stay relaxed.",
                "Build even bow motion.",
                "",
                "No",
            ),
        )

        val result = XlsxWorkbookReader.readAssignments(ByteArrayInputStream(bytes))

        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.assignments.size)
        assertTrue(result.assignments.single().id.isNotBlank())
        assertTrue(result.assignments.single().generatedId)
    }

    @Test
    fun acceptsNativeExcelDateNumbers() {
        val expectedDate = LocalDate.of(2026, 11, 1)
        val excelSerial = ChronoUnit.DAYS.between(
            LocalDate.of(1899, 12, 30),
            expectedDate,
        )
        val bytes = workbookBytesWithAssignmentRow(
            listOf(
                "assignment-id",
                excelSerial,
                "Tremolo",
                "YOUTUBE",
                "https://youtu.be/VZIWCt-jW6g",
                0,
                20,
                4,
                "",
                "",
                "",
                "No",
            ),
        )

        val result = XlsxWorkbookReader.readAssignments(ByteArrayInputStream(bytes))

        assertTrue(result.errors.isEmpty())
        assertEquals(expectedDate.toString(), result.assignments.single().assignedDate)
    }

    @Test
    fun verifiesEveryRequiredSheetHeader() {
        val validBytes = workbookBytesWithAssignmentRow(
            listOf(
                "assignment-id",
                "2026-11-01",
                "Tremolo",
                "YOUTUBE",
                "https://youtu.be/VZIWCt-jW6g",
                0,
                20,
                4,
                "",
                "",
                "",
                "No",
            ),
        )
        val invalidSheets = PracticeWorkbookTemplate.sheets(
            profile = StudentProfile(id = "profile"),
            snapshot = PracticeSnapshot(),
            updatedAtMillis = 1_798_000_000_000,
        ).map { sheet ->
            if (sheet.name == "Assignments") {
                sheet.copy(
                    rows = listOf(
                        PracticeWorkbookTemplate.assignmentHeaders.dropLast(1),
                    ),
                )
            } else {
                sheet
            }
        }
        val invalidOutput = ByteArrayOutputStream()
        XlsxWorkbookWriter.write(invalidOutput, invalidSheets)

        assertTrue(
            XlsxWorkbookReader.hasCurrentTemplateHeaders(
                ByteArrayInputStream(validBytes),
            ),
        )
        assertFalse(
            XlsxWorkbookReader.hasCurrentTemplateHeaders(
                ByteArrayInputStream(invalidOutput.toByteArray()),
            ),
        )
    }

    private fun workbookBytesWithAssignmentRow(row: List<Any>): ByteArray {
        val sheets = PracticeWorkbookTemplate.sheets(
            profile = StudentProfile(id = "profile"),
            snapshot = PracticeSnapshot(),
            updatedAtMillis = 1_798_000_000_000,
        ).map { sheet ->
            if (sheet.name == "Assignments") {
                sheet.copy(
                    rows = listOf(
                        PracticeWorkbookTemplate.assignmentHeaders,
                        row,
                    ),
                )
            } else {
                sheet
            }
        }
        return ByteArrayOutputStream().use { output ->
            XlsxWorkbookWriter.write(output, sheets)
            output.toByteArray()
        }
    }
}
