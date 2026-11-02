package com.lkeducation.lkpractice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvCodecTest {
    @Test
    fun roundTripPreservesQuotedFieldsAndOptionalDetails() {
        val original = PracticeTask(
            id = "original-id",
            startDate = DateKey("2026-10-30"),
            endDate = DateKey("2026-11-01"),
            title = "Boreas, measures 12–20",
            source = "https://youtu.be/VZIWCt-jW6g",
            sourceType = SourceType.YOUTUBE,
            startSeconds = 4,
            endSeconds = 19,
            repetitions = 5,
            coachNote = "Relax, then repeat \"slowly\".",
            whyImportant = "Prepare the seating audition.",
            sheetMusicUri = "content://practice/sheet/1",
        )

        val decoded = CsvCodec.decode(
            CsvCodec.encode(listOf(original)),
            defaultDate = DateKey("2026-10-30"),
            idFactory = { "imported-id" },
        )

        assertTrue(
            CsvCodec.encode(listOf(original))
                .startsWith(
                    "assignment_id,student_id,instrument,start_date,end_date,title",
                ),
        )
        assertEquals(1, decoded.errors.size)
        assertTrue(decoded.errors.single().contains("sheet music"))
        assertEquals(1, decoded.tasks.size)
        assertEquals(
            original.copy(sheetMusicUri = null),
            decoded.tasks.single(),
        )
    }

    @Test
    fun importsLegacyFiveColumnCsv() {
        val csv = """
            title,source,start_seconds,end_seconds,repetitions
            "Tremolo","https://www.youtube.com/watch?v=c79nrCCcKuM",0,30,4
        """.trimIndent()

        val decoded = CsvCodec.decode(csv, defaultDate = DateKey("2026-10-30")) { "legacy-id" }

        assertTrue(decoded.errors.isEmpty())
        assertEquals("Tremolo", decoded.tasks.single().title)
        assertEquals(DateKey("2026-10-30"), decoded.tasks.single().startDate)
        assertEquals(DateKey("2026-10-30"), decoded.tasks.single().endDate)
        assertEquals(4, decoded.tasks.single().repetitions)
    }

    @Test
    fun legacyDateColumnFallsBackToSingleDayRange() {
        val csv = """
            date,title,source,start_seconds,end_seconds,repetitions
            2026-11-02,Scales,,0,,3
        """.trimIndent()

        val decoded = CsvCodec.decode(csv, defaultDate = DateKey("2026-11-03"))

        assertTrue(decoded.errors.isEmpty())
        assertEquals(DateKey("2026-11-02"), decoded.tasks.single().startDate)
        assertEquals(DateKey("2026-11-02"), decoded.tasks.single().endDate)
    }

    @Test
    fun rejectsRangesLongerThanThirtyDays() {
        val csv = """
            start_date,end_date,title,source,start_seconds,end_seconds,repetitions
            2026-11-01,2026-12-01,Scales,,0,,3
        """.trimIndent()

        val decoded = CsvCodec.decode(csv, defaultDate = DateKey("2026-11-01"))

        assertTrue(decoded.tasks.isEmpty())
        assertTrue(decoded.errors.single().contains("30 days"))
    }

    @Test
    fun acceptsUtf8BomFromSpreadsheetExports() {
        val csv = """
            ﻿title,source,start_seconds,end_seconds,repetitions
            "Shifting","https://youtu.be/VZIWCt-jW6g",0,20,3
        """.trimIndent()

        val decoded = CsvCodec.decode(csv, defaultDate = DateKey("2026-10-30"))

        assertTrue(decoded.errors.isEmpty())
        assertEquals("Shifting", decoded.tasks.single().title)
    }

    @Test
    fun reportsInvalidRowsWithoutDiscardingValidRows() {
        val csv = """
            date,title,source,start_seconds,end_seconds,repetitions
            2026-10-30,Good,https://youtu.be/VZIWCt-jW6g,0,10,3
            2026-10-30,Bad,https://example.com/watch?v=VZIWCt-jW6g,0,10,3
        """.trimIndent()

        val decoded = CsvCodec.decode(csv, defaultDate = DateKey("2026-10-30"))

        assertEquals(1, decoded.tasks.size)
        assertEquals(1, decoded.errors.size)
        assertTrue(decoded.errors.single().contains("Line 3"))
    }

    @Test
    fun localReferencesExportAsPortableReattachmentPlaceholders() {
        val local = PracticeTask(
            id = "local",
            startDate = DateKey("2026-10-31"),
            title = "Teacher demonstration",
            source = "content://private/video",
            sourceType = SourceType.LOCAL,
            startSeconds = 2,
            endSeconds = 8,
            repetitions = 2,
            sheetMusicUri = "content://private/sheet",
        )

        val encoded = CsvCodec.encode(listOf(local))
        val decoded = CsvCodec.decode(encoded, defaultDate = DateKey("2026-10-31")) { "imported" }

        assertFalse(encoded.contains("content://private"))
        assertEquals(2, decoded.errors.size)
        assertEquals(SourceType.LOCAL, decoded.tasks.single().sourceType)
        assertEquals("", decoded.tasks.single().source)
        assertNull(decoded.tasks.single().sheetMusicUri)
    }

    @Test
    fun rejectsAnUnclosedQuotedField() {
        val decoded = CsvCodec.decode(
            "title,source,start_seconds,end_seconds,repetitions\n\"Tremolo,https://youtu.be/VZIWCt-jW6g,0,10,3",
            defaultDate = DateKey("2026-10-31"),
        )

        assertTrue(decoded.tasks.isEmpty())
        assertTrue(decoded.errors.single().contains("unclosed quoted field"))
    }

    @Test
    fun optionalReferenceAndStudentOwnershipRoundTrip() {
        val assignment = PracticeTask(
            id = "piano-one",
            childProfileId = "student-two",
            instrument = "Piano",
            startDate = DateKey("2026-11-01"),
            title = "C major scale",
            source = "",
            sourceType = SourceType.NONE,
            startSeconds = 0,
            endSeconds = null,
            repetitions = 3,
        )

        val decoded = CsvCodec.decode(
            text = CsvCodec.encode(listOf(assignment)),
            defaultDate = DateKey("2026-11-01"),
            childProfileId = "student-two",
            instrument = "Piano",
        )

        assertTrue(decoded.errors.isEmpty())
        assertEquals(assignment, decoded.tasks.single())
    }

    @Test
    fun rejectsRowsOwnedByAnotherStudent() {
        val csv = """
            assignment_id,student_id,instrument,date,title,reference_type,source,start_seconds,end_seconds,repetitions
            other-task,other-child,Piano,2026-11-01,Scales,NONE,,0,,3
        """.trimIndent()

        val decoded = CsvCodec.decode(
            text = csv,
            defaultDate = DateKey("2026-11-01"),
            childProfileId = "current-child",
            instrument = "Violin",
        )

        assertTrue(decoded.tasks.isEmpty())
        assertTrue(decoded.errors.single().contains("another profile"))
    }

    @Test
    fun timeGoalStepsAndRevisionRoundTrip() {
        val assignment = PracticeTask(
            id = "time-goal",
            childProfileId = "student",
            instrument = "Piano",
            startDate = DateKey("2026-11-02"),
            title = "Repertoire passage",
            source = "https://youtu.be/VZIWCt-jW6g",
            sourceType = SourceType.YOUTUBE,
            startSeconds = 10,
            endSeconds = 40,
            repetitions = 1,
            assignmentType = AssignmentType.REPERTOIRE,
            goalType = PracticeGoalType.TIME,
            targetSeconds = 12 * 60,
            enabledModes = linkedSetOf(
                PracticeMode.LISTEN,
                PracticeMode.LISTEN_AND_PLAY,
                PracticeMode.PLAY_YOURSELF,
            ),
            revision = 4,
            archivedAtMillis = 1_800_000_000_000,
        )

        val decoded = CsvCodec.decode(
            text = CsvCodec.encode(listOf(assignment)),
            defaultDate = assignment.startDate,
            childProfileId = assignment.childProfileId,
            instrument = assignment.instrument,
        )

        assertTrue(decoded.errors.isEmpty())
        assertEquals(assignment, decoded.tasks.single())
    }
}
