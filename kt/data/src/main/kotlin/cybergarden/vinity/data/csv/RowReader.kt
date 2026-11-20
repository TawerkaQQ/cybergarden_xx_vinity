package cybergarden.vinity.data.csv

import cybergarden.vinity.domain.model.Coded
import cybergarden.vinity.domain.model.Severity
import cybergarden.vinity.domain.model.Violation
import cybergarden.vinity.domain.model.codeOf

internal class RowReader(
    private val table: SubmissionTable,
    private val row: Map<String, String>,
    private val violations: MutableList<Violation>,
) {
    private val publicationId = row["publication_id"].orEmpty()
    private val recordId = table.columns.getOrNull(1)?.let { row[it] }

    fun required(column: String): String = text(column) ?: "".also { report("пустое обязательное поле $column") }

    fun text(column: String): String? = row[column]?.trim()?.takeIf { it.isNotEmpty() }

    fun list(column: String): List<String> =
        text(column)?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    fun int(column: String): Int? = parsed(column, "целое число") { it.toIntOrNull() }

    fun double(column: String): Double? = parsed(column, "число") { it.toDoubleOrNull() }

    fun bool(column: String): Boolean? = parsed(column, "true или false") { it.toBooleanStrictOrNull() }

    inline fun <reified E> code(column: String): E? where E : Enum<E>, E : Coded =
        parsed(column, enumValues<E>().joinToString { it.code }) { codeOf<E>(it) }

    fun <T> parsed(column: String, expected: String, parse: (String) -> T?): T? {
        val value = text(column) ?: return null
        return parse(value) ?: null.also { report("$column = «$value», ожидается: $expected") }
    }

    private fun report(message: String) {
        violations += Violation(Severity.ERROR, table.tableName, publicationId.ifEmpty { null }, recordId, message)
    }
}
