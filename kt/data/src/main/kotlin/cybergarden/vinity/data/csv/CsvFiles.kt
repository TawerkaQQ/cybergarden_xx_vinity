package cybergarden.vinity.data.csv

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.nio.file.Path
import kotlin.io.path.readText

internal data class CsvRow(val number: Int, val cells: Map<String, String>, val complete: Boolean)

internal data class CsvContent(val header: List<String>, val rows: List<CsvRow>)

internal object CsvFiles {
    private const val BOM = "﻿"
    private const val FIRST_ROW = 2

    private val readFormat = CSVFormat.DEFAULT.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .build()

    fun read(path: Path): CsvContent {
        val text = path.readText(Charsets.UTF_8).removePrefix(BOM)
        CSVParser.parse(text, readFormat).use { parser ->
            val header = parser.headerNames
            val rows = parser.records.mapIndexed { index, record ->
                val cells = header.associateWith { column -> if (record.isMapped(column)) record.get(column).orEmpty().trim() else "" }
                CsvRow(index + FIRST_ROW, cells, record.isConsistent)
            }
            return CsvContent(header, rows)
        }
    }
}
