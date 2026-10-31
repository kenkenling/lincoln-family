package com.practicepartner.music

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

object PracticeWorkbookDetector {
    private const val MAX_INSPECTED_PART_BYTES = 1_000_000

    fun matches(input: InputStream): Boolean = runCatching {
        var workbookXml: String? = null
        var profileSheetXml: String? = null

        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "xl/workbook.xml" -> workbookXml = zip.readEntryText()
                    "xl/worksheets/sheet1.xml" -> profileSheetXml = zip.readEntryText()
                }
                zip.closeEntry()
                if (workbookXml != null && profileSheetXml != null) break
                entry = zip.nextEntry
            }
        }

        val workbook = workbookXml.orEmpty()
        val profile = profileSheetXml.orEmpty()
        val hasCurrentSheets = PracticeWorkbookTemplate.requiredSheetNames.all { name ->
            workbook.contains("""name="$name"""")
        }
        val hasLegacySheets = PracticeWorkbookTemplate.legacyRequiredSheetNames.all { name ->
            workbook.contains("""name="$name"""")
        }
        (hasCurrentSheets || hasLegacySheets) &&
            profile.contains("Template Version") &&
            profile.contains("Student ID")
    }.getOrDefault(false)

    private fun ZipInputStream.readEntryText(): String {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_INSPECTED_PART_BYTES) {
                throw IOException("Workbook metadata is unexpectedly large.")
            }
            bytes.write(buffer, 0, read)
        }
        return bytes.toString(Charsets.UTF_8.name())
    }
}
