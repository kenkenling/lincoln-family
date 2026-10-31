package com.practicepartner.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipInputStream

class XlsxWorkbookWriterTest {
    @Test
    fun writesACompleteExcelPackageWithAllWorksheets() {
        val output = ByteArrayOutputStream()
        val sheets = listOf(
            WorkbookSheet(
                "Profile",
                listOf(
                    listOf("Key", "Value"),
                    listOf("Student", "Lincoln & Family"),
                ),
            ),
            WorkbookSheet("Assignments", listOf(listOf("Title"), listOf("Tremolo"))),
            WorkbookSheet("Daily Progress", listOf(listOf("Date"), listOf("2026-10-31"))),
            WorkbookSheet("Practice Log", listOf(listOf("Event"), listOf("Completed <3"))),
        )

        XlsxWorkbookWriter.write(
            output = output,
            sheets = sheets,
            createdAt = Instant.parse("2026-10-31T12:00:00Z"),
        )

        val entries = unzip(output.toByteArray())
        val expected = setOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "docProps/core.xml",
            "docProps/app.xml",
            "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels",
            "xl/styles.xml",
            "xl/worksheets/sheet1.xml",
            "xl/worksheets/sheet2.xml",
            "xl/worksheets/sheet3.xml",
            "xl/worksheets/sheet4.xml",
        )

        assertEquals(expected, entries.keys)
        assertTrue(entries.getValue("xl/workbook.xml").contains("Daily Progress"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("Lincoln &amp; Family"))
        assertTrue(entries.getValue("xl/worksheets/sheet4.xml").contains("Completed &lt;3"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("""state="frozen""""))
        assertFalse(entries.values.joinToString("").contains("content://"))
    }

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
