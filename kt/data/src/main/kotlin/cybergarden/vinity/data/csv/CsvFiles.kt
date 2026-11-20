package cybergarden.vinity.data.csv

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

internal data class CsvContent(val header: List<String>, val rows: List<Map<String, String>>)

internal object CsvFiles {
    private const val BOM = "﻿"

    private val readFormat = CSVFormat.DEFAULT.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .build()

    fun read(path: Path): CsvContent {
        val text = path.readText(Charsets.UTF_8).removePrefix(BOM)
        CSVParser.parse(text, readFormat).use { parser ->
            val header = parser.headerNames
            val rows = parser.records.map { record ->
                header.associateWith { column -> if (record.isMapped(column)) record.get(column).orEmpty() else "" }
            }
            return CsvContent(header, rows)
        }
    }

    fun write(path: Path, columns: List<String>, rows: List<List<String>>) {
        Files.createDirectories(path.parent)
        val format = CSVFormat.DEFAULT.builder()
            .setHeader(*columns.toTypedArray())
            .setRecordSeparator("\r\n")
            .build()
        Files.newBufferedWriter(path, Charsets.UTF_8).use { writer ->
            writer.write(BOM)
            CSVPrinter(writer, format).use { printer -> rows.forEach { printer.printRecord(it) } }
        }
    }
}
