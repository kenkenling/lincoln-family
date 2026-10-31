package com.practicepartner.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PracticeWorkbookDetectorTest {
    @Test
    fun acceptsWorkbookCreatedByPracticePartner() {
        val output = ByteArrayOutputStream()
        XlsxWorkbookWriter.write(
            output = output,
            sheets = PracticeWorkbookTemplate.sheets(
                profile = StudentProfile(id = "profile"),
                snapshot = PracticeSnapshot(),
                updatedAtMillis = 1_798_000_000_000,
            ),
        )

        assertTrue(
            PracticeWorkbookDetector.matches(ByteArrayInputStream(output.toByteArray())),
        )
    }

    @Test
    fun rejectsUnrelatedOrInvalidExcelContent() {
        assertFalse(
            PracticeWorkbookDetector.matches(
                ByteArrayInputStream("not an Excel workbook".toByteArray()),
            ),
        )

        val output = ByteArrayOutputStream()
        XlsxWorkbookWriter.write(
            output = output,
            sheets = listOf(
                WorkbookSheet("Family Budget", listOf(listOf("Month", "Amount"))),
            ),
        )
        assertFalse(
            PracticeWorkbookDetector.matches(ByteArrayInputStream(output.toByteArray())),
        )
    }

    @Test
    fun acceptsLegacyFourSheetPracticePartnerWorkbook() {
        val output = ByteArrayOutputStream()
        XlsxWorkbookWriter.write(
            output = output,
            sheets = listOf(
                WorkbookSheet(
                    "Profile",
                    listOf(
                        listOf("Key", "Value"),
                        listOf("Template Version", "1"),
                        listOf("Student ID", "profile"),
                    ),
                ),
                WorkbookSheet("Assignments", listOf(listOf("Assignment ID"))),
                WorkbookSheet("Daily Progress", listOf(listOf("Date"))),
                WorkbookSheet("Practice Log", listOf(listOf("Event ID"))),
            ),
        )

        assertTrue(
            PracticeWorkbookDetector.matches(ByteArrayInputStream(output.toByteArray())),
        )
    }
}
