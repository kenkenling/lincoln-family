package com.practicepartner.music

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
            assignedDate = "2026-10-30",
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
            defaultDate = "2026-10-30",
            idFactory = { "imported-id" },
        )

        assertTrue(CsvCodec.encode(listOf(original)).startsWith("assignment_id,date,title"))
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

        val decoded = CsvCodec.decode(csv, defaultDate = "2026-10-30") { "legacy-id" }

        assertTrue(decoded.errors.isEmpty())
        assertEquals("Tremolo", decoded.tasks.single().title)
        assertEquals("2026-10-30", decoded.tasks.single().assignedDate)
        assertEquals(4, decoded.tasks.single().repetitions)
    }

    @Test
    fun acceptsUtf8BomFromSpreadsheetExports() {
        val csv = """
            ﻿title,source,start_seconds,end_seconds,repetitions
            "Shifting","https://youtu.be/VZIWCt-jW6g",0,20,3
        """.trimIndent()

        val decoded = CsvCodec.decode(csv, defaultDate = "2026-10-30")

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

        val decoded = CsvCodec.decode(csv, defaultDate = "2026-10-30")

        assertEquals(1, decoded.tasks.size)
        assertEquals(1, decoded.errors.size)
        assertTrue(decoded.errors.single().contains("Line 3"))
    }

    @Test
    fun localReferencesExportAsPortableReattachmentPlaceholders() {
        val local = PracticeTask(
            id = "local",
            assignedDate = "2026-10-31",
            title = "Teacher demonstration",
            source = "content://private/video",
            sourceType = SourceType.LOCAL,
            startSeconds = 2,
            endSeconds = 8,
            repetitions = 2,
            sheetMusicUri = "content://private/sheet",
        )

        val encoded = CsvCodec.encode(listOf(local))
        val decoded = CsvCodec.decode(encoded, defaultDate = "2026-10-31") { "imported" }

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
            defaultDate = "2026-10-31",
        )

        assertTrue(decoded.tasks.isEmpty())
        assertTrue(decoded.errors.single().contains("unclosed quoted field"))
    }
}
