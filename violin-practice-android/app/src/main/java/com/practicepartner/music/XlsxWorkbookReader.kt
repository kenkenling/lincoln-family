package com.practicepartner.music

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.floor

data class WorkbookAssignment(
    val id: String,
    val assignedDate: String,
    val title: String,
    val sourceType: SourceType,
    val source: String,
    val startSeconds: Int,
    val endSeconds: Int?,
    val repetitions: Int,
    val coachNote: String,
    val whyImportant: String,
    val sheetMusicUri: String?,
    val needsSheetMusicReattachment: Boolean,
    val generatedId: Boolean = false,
)

data class WorkbookAssignmentImportResult(
    val assignments: List<WorkbookAssignment>,
    val errors: List<String>,
)

object XlsxWorkbookReader {
    fun hasCurrentTemplateHeaders(input: InputStream): Boolean = runCatching {
        val entries = readEntries(input)
        val workbook = entries["xl/workbook.xml"] ?: return@runCatching false
        val relationships = entries["xl/_rels/workbook.xml.rels"] ?: return@runCatching false
        val sharedStrings = entries["xl/sharedStrings.xml"]
            ?.let(::parseSharedStrings)
            .orEmpty()
        val paths = sheetPaths(workbook, relationships)

        PracticeWorkbookTemplate.headersBySheet.all { (sheetName, expectedHeaders) ->
            val worksheet = paths[sheetName]?.let(entries::get) ?: return@all false
            val actualHeaders = parseRows(worksheet, sharedStrings)
                .firstOrNull()
                ?.map(String::trim)
                .orEmpty()
            actualHeaders == expectedHeaders
        }
    }.getOrDefault(false)

    fun readAssignments(input: InputStream): WorkbookAssignmentImportResult {
        val entries = readEntries(input)
        val workbook = entries["xl/workbook.xml"]
            ?: return failure("The workbook metadata is missing.")
        val relationships = entries["xl/_rels/workbook.xml.rels"]
            ?: return failure("The workbook relationships are missing.")
        val sheetPath = sheetPaths(workbook, relationships)["Assignments"]
            ?: return failure("The Assignments worksheet is missing.")
        val worksheet = entries[sheetPath]
            ?: return failure("The Assignments worksheet content is missing.")
        val sharedStrings = entries["xl/sharedStrings.xml"]
            ?.let(::parseSharedStrings)
            .orEmpty()
        val rows = parseRows(worksheet, sharedStrings)
        if (rows.isEmpty()) return WorkbookAssignmentImportResult(emptyList(), emptyList())

        val header = rows.first().mapIndexed { index, value ->
            value.trim().lowercase() to index
        }.toMap()
        val required = PracticeWorkbookTemplate.assignmentHeaders
            .take(8)
            .map(String::lowercase)
        if (!required.all(header::containsKey)) {
            return failure("The Assignments worksheet does not use the Practice Partner columns.")
        }

        val assignments = mutableListOf<WorkbookAssignment>()
        val errors = mutableListOf<String>()
        val seenAssignmentIds = mutableSetOf<String>()
        rows.drop(1).forEachIndexed { index, row ->
            if (row.all(String::isBlank)) return@forEachIndexed
            fun field(name: String): String = header[name]
                ?.let { row.getOrNull(it) }
                .orEmpty()
                .trim()

            val rowNumber = index + 2
            val suppliedId = field("assignment id")
            val id = suppliedId.ifBlank { UUID.randomUUID().toString() }
            val date = normalizedWorkbookDate(field("assigned date"))
            val title = field("title")
            val source = field("reference link")
            val declaredType = field("reference type")
                .takeIf(String::isNotBlank)
                ?.let { runCatching { SourceType.valueOf(it.uppercase()) }.getOrNull() }
            val type = declaredType ?: sourceTypeFor(source)
            val start = field("clip start seconds").ifBlank { "0" }.toIntOrNull()
            val end = field("clip end seconds").takeIf(String::isNotBlank)?.toIntOrNull()
            val repetitions = field("target repetitions").toIntOrNull()
            val error = when {
                date == null -> "invalid assigned date; use YYYY-MM-DD or an Excel date"
                title.isBlank() -> "missing title"
                field("reference type").isNotBlank() && declaredType == null ->
                    "invalid reference type"
                type == null -> "reference type or link is missing"
                type == SourceType.YOUTUBE && youtubeVideoId(source) == null ->
                    "invalid YouTube reference"
                type == SourceType.LOCAL && source.isNotBlank() && !source.startsWith("content://") ->
                    "invalid local reference"
                start == null || start < 0 -> "invalid clip start"
                field("clip end seconds").isNotBlank() && end == null -> "invalid clip end"
                end != null && end <= start -> "clip end must be after clip start"
                repetitions == null || repetitions !in 1..20 ->
                    "target repetitions must be between 1 and 20"
                id in seenAssignmentIds -> "duplicate assignment ID"
                else -> null
            }
            if (error != null) {
                errors += "Assignments row $rowNumber: $error."
            } else {
                seenAssignmentIds += id
                assignments += WorkbookAssignment(
                    id = id,
                    assignedDate = date!!,
                    title = title,
                    sourceType = type!!,
                    source = source,
                    startSeconds = start!!,
                    endSeconds = end,
                    repetitions = repetitions!!,
                    coachNote = field("teacher note"),
                    whyImportant = field("why important"),
                    sheetMusicUri = field("sheet music link").takeIf(String::isNotBlank),
                    needsSheetMusicReattachment =
                        field("has local sheet music").equals("yes", true),
                    generatedId = suppliedId.isBlank(),
                )
            }
        }
        return WorkbookAssignmentImportResult(assignments, errors)
    }

    private fun readEntries(input: InputStream): Map<String, ByteArray> {
        var totalBytes = 0
        return buildMap {
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            require(totalBytes <= MAX_READER_BYTES) {
                                "The workbook is unexpectedly large."
                            }
                            output.write(buffer, 0, read)
                        }
                        put(entry.name, output.toByteArray())
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun sheetPaths(
        workbookBytes: ByteArray,
        relationshipBytes: ByteArray,
    ): Map<String, String> {
        val workbook = parseXml(workbookBytes)
        val relationships = parseXml(relationshipBytes)
            .getElementsByTagNameNS("*", "Relationship")
            .asElements()
            .associate { relationship ->
                relationship.getAttribute("Id") to relationship.getAttribute("Target")
            }
        return workbook.getElementsByTagNameNS("*", "sheet")
            .asElements()
            .mapNotNull { sheet ->
                val name = sheet.getAttribute("name").takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val relationshipId = sheet
                    .getAttributeNS(OFFICE_RELATIONSHIPS_NS, "id")
                    .takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val target = relationships[relationshipId]
                    ?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val path = if (target.startsWith('/')) {
                    target.removePrefix("/")
                } else {
                    "xl/${target.removePrefix("./")}"
                }
                name to path
            }
            .toMap()
    }

    private fun normalizedWorkbookDate(value: String): String? {
        val trimmed = value.trim()
        runCatching { LocalDate.parse(trimmed) }.getOrNull()?.let { return it.toString() }
        val serial = trimmed.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 1.0 }
            ?: return null
        return runCatching {
            EXCEL_DATE_EPOCH.plusDays(floor(serial).toLong()).toString()
        }.getOrNull()
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val document = parseXml(bytes)
        return document.getElementsByTagNameNS("*", "si")
            .asElements()
            .map { element ->
                element.getElementsByTagNameNS("*", "t")
                    .asElements()
                    .joinToString("") { it.textContent.orEmpty() }
            }
    }

    private fun parseRows(
        worksheetBytes: ByteArray,
        sharedStrings: List<String>,
    ): List<List<String>> {
        val document = parseXml(worksheetBytes)
        return document.getElementsByTagNameNS("*", "row")
            .asElements()
            .map { row ->
                val valuesByColumn = row.getElementsByTagNameNS("*", "c")
                    .asElements()
                    .associate { cell ->
                        val column = columnIndex(cell.getAttribute("r"))
                        column to cellValue(cell, sharedStrings)
                    }
                val columnCount = (valuesByColumn.keys.maxOrNull() ?: -1) + 1
                List(columnCount) { valuesByColumn[it].orEmpty() }
            }
    }

    private fun cellValue(cell: Element, sharedStrings: List<String>): String {
        val type = cell.getAttribute("t")
        return when (type) {
            "inlineStr" -> cell.getElementsByTagNameNS("*", "t")
                .asElements()
                .joinToString("") { it.textContent.orEmpty() }
            "s" -> cell.getElementsByTagNameNS("*", "v")
                .item(0)
                ?.textContent
                ?.toIntOrNull()
                ?.let(sharedStrings::getOrNull)
                .orEmpty()
            else -> cell.getElementsByTagNameNS("*", "v")
                .item(0)
                ?.textContent
                .orEmpty()
        }
    }

    private fun columnIndex(reference: String): Int {
        val letters = reference.takeWhile(Char::isLetter).uppercase()
        if (letters.isBlank()) return 0
        return letters.fold(0) { value, character ->
            value * 26 + (character.code - 'A'.code + 1)
        } - 1
    }

    private fun parseXml(bytes: ByteArray) =
        secureDocumentBuilderFactory()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { isXIncludeAware = false }
            setExpandEntityReferences(false)
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            runCatching {
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
        }

    private fun failure(message: String) =
        WorkbookAssignmentImportResult(emptyList(), listOf(message))
}

private fun org.w3c.dom.NodeList.asElements(): List<Element> =
    (0 until length).mapNotNull { item(it) as? Element }

private const val OFFICE_RELATIONSHIPS_NS =
    "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val MAX_READER_BYTES = 20 * 1024 * 1024
private val EXCEL_DATE_EPOCH = LocalDate.of(1899, 12, 30)
