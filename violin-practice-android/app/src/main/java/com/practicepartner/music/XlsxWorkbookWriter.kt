package com.practicepartner.music

import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

object XlsxWorkbookWriter {
    const val MIME_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun write(
        output: OutputStream,
        sheets: List<WorkbookSheet>,
        createdAt: Instant = Instant.now(),
    ) {
        require(sheets.isNotEmpty()) { "An Excel workbook needs at least one sheet." }
        require(sheets.map(WorkbookSheet::name).distinct().size == sheets.size) {
            "Excel sheet names must be unique."
        }

        ZipOutputStream(output.buffered()).use { zip ->
            zip.putXml("[Content_Types].xml", contentTypesXml(sheets.size))
            zip.putXml("_rels/.rels", packageRelationshipsXml())
            zip.putXml("docProps/core.xml", corePropertiesXml(createdAt))
            zip.putXml("docProps/app.xml", appPropertiesXml(sheets))
            zip.putXml("xl/workbook.xml", workbookXml(sheets))
            zip.putXml("xl/_rels/workbook.xml.rels", workbookRelationshipsXml(sheets.size))
            zip.putXml("xl/styles.xml", stylesXml())
            sheets.forEachIndexed { index, sheet ->
                zip.putXml(
                    "xl/worksheets/sheet${index + 1}.xml",
                    worksheetXml(sheet),
                )
            }
        }
    }

    private fun contentTypesXml(sheetCount: Int): String = buildString {
        append(XML_HEADER)
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        append("""<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>""")
        append("""<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>""")
        repeat(sheetCount) { index ->
            append(
                """<Override PartName="/xl/worksheets/sheet${index + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""",
            )
        }
        append("</Types>")
    }

    private fun packageRelationshipsXml(): String =
        XML_HEADER +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>""" +
            """<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>""" +
            "</Relationships>"

    private fun corePropertiesXml(createdAt: Instant): String =
        XML_HEADER +
            """<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">""" +
            "<dc:creator>Practice Partner</dc:creator>" +
            "<cp:lastModifiedBy>Practice Partner</cp:lastModifiedBy>" +
            """<dcterms:created xsi:type="dcterms:W3CDTF">${xmlText(createdAt.toString())}</dcterms:created>""" +
            """<dcterms:modified xsi:type="dcterms:W3CDTF">${xmlText(createdAt.toString())}</dcterms:modified>""" +
            "</cp:coreProperties>"

    private fun appPropertiesXml(sheets: List<WorkbookSheet>): String = buildString {
        append(XML_HEADER)
        append(
            """<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">""",
        )
        append("<Application>Practice Partner</Application>")
        append("<HeadingPairs><vt:vector size=\"2\" baseType=\"variant\">")
        append("<vt:variant><vt:lpstr>Worksheets</vt:lpstr></vt:variant>")
        append("<vt:variant><vt:i4>${sheets.size}</vt:i4></vt:variant>")
        append("</vt:vector></HeadingPairs>")
        append("<TitlesOfParts><vt:vector size=\"${sheets.size}\" baseType=\"lpstr\">")
        sheets.forEach { append("<vt:lpstr>${xmlText(it.name)}</vt:lpstr>") }
        append("</vt:vector></TitlesOfParts>")
        append("</Properties>")
    }

    private fun workbookXml(sheets: List<WorkbookSheet>): String = buildString {
        append(XML_HEADER)
        append(
            """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""",
        )
        append("<bookViews><workbookView/></bookViews><sheets>")
        sheets.forEachIndexed { index, sheet ->
            append(
                """<sheet name="${xmlAttribute(sheet.name)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""",
            )
        }
        append("</sheets><calcPr calcId=\"0\"/></workbook>")
    }

    private fun workbookRelationshipsXml(sheetCount: Int): String = buildString {
        append(XML_HEADER)
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        repeat(sheetCount) { index ->
            append(
                """<Relationship Id="rId${index + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${index + 1}.xml"/>""",
            )
        }
        append(
            """<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""",
        )
        append("</Relationships>")
    }

    private fun stylesXml(): String =
        XML_HEADER +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF2F6B57"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
            """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"><alignment wrapText="1"/></xf></cellXfs>""" +
            """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
            "</styleSheet>"

    private fun worksheetXml(sheet: WorkbookSheet): String {
        val columnCount = max(1, sheet.rows.maxOfOrNull(List<Any>::size) ?: 1)
        val rowCount = max(1, sheet.rows.size)
        val lastCell = "${columnName(columnCount)}$rowCount"
        return buildString {
            append(XML_HEADER)
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("""<dimension ref="A1:$lastCell"/>""")
            append(
                """<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""",
            )
            append("<cols>")
            repeat(columnCount) { columnIndex ->
                val width = columnWidth(sheet.rows, columnIndex)
                append(
                    """<col min="${columnIndex + 1}" max="${columnIndex + 1}" width="$width" customWidth="1"/>""",
                )
            }
            append("</cols><sheetData>")
            sheet.rows.forEachIndexed { rowIndex, row ->
                val excelRow = rowIndex + 1
                append("""<row r="$excelRow">""")
                row.forEachIndexed { columnIndex, value ->
                    append(cellXml(columnIndex + 1, excelRow, value, rowIndex == 0))
                }
                append("</row>")
            }
            append("</sheetData>")
            if (sheet.rows.isNotEmpty() && sheet.rows.first().isNotEmpty()) {
                append("""<autoFilter ref="A1:${columnName(columnCount)}$rowCount"/>""")
            }
            append("</worksheet>")
        }
    }

    private fun cellXml(
        column: Int,
        row: Int,
        value: Any,
        header: Boolean,
    ): String {
        val reference = "${columnName(column)}$row"
        val style = if (header) """ s="1"""" else ""
        return when (value) {
            is Byte,
            is Short,
            is Int,
            is Long,
            is Float,
            is Double,
            -> """<c r="$reference"$style t="n"><v>${xmlText(value.toString())}</v></c>"""

            else -> {
                val text = value.toString()
                val preserve = if (text.startsWith(' ') || text.endsWith(' ')) {
                    """ xml:space="preserve""""
                } else {
                    ""
                }
                """<c r="$reference"$style t="inlineStr"><is><t$preserve>${xmlText(text)}</t></is></c>"""
            }
        }
    }

    private fun columnWidth(rows: List<List<Any>>, columnIndex: Int): Int {
        val longest = rows.maxOfOrNull { row ->
            row.getOrNull(columnIndex)?.toString()?.length ?: 0
        } ?: 0
        return min(45, max(10, longest + 2))
    }

    private fun columnName(oneBasedColumn: Int): String {
        require(oneBasedColumn > 0)
        var value = oneBasedColumn
        return buildString {
            while (value > 0) {
                val remainder = (value - 1) % 26
                append(('A'.code + remainder).toChar())
                value = (value - 1) / 26
            }
        }.reversed()
    }

    private fun xmlText(value: String): String =
        value.asSequence()
            .filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }
            .joinToString("")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun xmlAttribute(value: String): String =
        xmlText(value).replace("\"", "&quot;").replace("'", "&apos;")

    private fun ZipOutputStream.putXml(path: String, value: String) {
        putNextEntry(ZipEntry(path))
        write(value.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private const val XML_HEADER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
}
