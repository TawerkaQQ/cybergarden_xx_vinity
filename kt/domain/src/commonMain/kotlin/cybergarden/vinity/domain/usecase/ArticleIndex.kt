package cybergarden.vinity.domain.usecase

import cybergarden.vinity.domain.model.Issues
import cybergarden.vinity.domain.model.Profile
import cybergarden.vinity.domain.model.Publications
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.RecordKey
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.TableSpec

enum class ValueKind { TABLE, FIGURE, TEXT }

data class LinkedRecord(
    val table: String,
    val id: String,
    val label: String,
    val concise: Boolean,
    val fields: List<Pair<String, String>>,
)

data class RecordTable(val table: String, val records: List<LinkedRecord>)

data class Tally(val name: String, val count: Int)

class ArticleIndex(val submission: Submission) {

    val profile: Profile = submission.profile
    private val spec = profile.values
    private val publications = submission.publications.associateBy { it.publicationId }
    private val values = submission.values.groupBy { it.publicationId }
    private val valuesByKey = submission.values.associateBy { it.key() }
    private val issues = submission.issues.groupBy { it.publicationId }
    private val issuesByValue: Map<RecordKey, List<Record>> = submission.issues
        .flatMap { issue -> issue.list(Issues.RECORDS).map { RecordKey(issue.publicationId, it) to issue } }
        .groupBy({ it.first }, { it.second })
    private val links = profile.valueTable.references.filterKeys { it != spec.duplicateOf }
    private val describedTables = profile.tables.filter { it.name != Publications.TABLE && it.name != spec.table }
    private val linked: Map<String, Map<RecordKey, LinkedRecord>> = (links.values + describedTables.map { it.name }).toSet()
        .associateWith { name ->
            val table = profile.table(name)
            submission.rows(table).associate { record -> record.key() to describe(table, record) }
        }

    val repeats: Repeats by lazy { FindRepeats()(submission) }

    val articleIds: Set<String> get() = publications.keys + values.keys

    val allValues: List<Record> get() = submission.values

    val allIssues: List<Record> get() = submission.issues

    fun publication(id: String): Record? = publications[id]

    fun title(id: String): String? = publications[id]?.get(Publications.TITLE)

    fun values(id: String): List<Record> = values[id].orEmpty()

    fun issues(id: String): List<Record> = issues[id].orEmpty()

    fun issuesOf(row: Record): List<Record> = issuesByValue[row.key()].orEmpty()

    fun issueValues(issue: Record): List<Record> =
        issue.list(Issues.RECORDS).mapNotNull { valuesByKey[RecordKey(issue.publicationId, it)] }

    fun kind(row: Record): ValueKind = when {
        row[spec.sourceTable] != null -> ValueKind.TABLE
        row[spec.figure] != null -> ValueKind.FIGURE
        else -> ValueKind.TEXT
    }

    fun links(row: Record): List<LinkedRecord> = links.flatMap { (column, table) ->
        row.list(column).map { id -> linked[table]?.get(RecordKey(row.publicationId, id)) ?: LinkedRecord(table, id, id, true, emptyList()) }
    }

    fun linkedRecord(row: Record, column: String): LinkedRecord? {
        val table = links[column] ?: return null
        val id = row.list(column).firstOrNull() ?: return null
        return linked[table]?.get(RecordKey(row.publicationId, id)) ?: LinkedRecord(table, id, id, true, emptyList())
    }

    fun linkedField(row: Record, column: String, field: String): String? {
        val table = links[column] ?: return null
        val id = row.list(column).firstOrNull() ?: return null
        return linked[table]?.get(RecordKey(row.publicationId, id))?.fields?.firstOrNull { it.first == field }?.second
    }

    fun recordTables(id: String): List<RecordTable> = describedTables.mapNotNull { table ->
        val records = submission.rows(table).filter { it.publicationId == id }
            .mapNotNull { linked[table.name]?.get(it.key()) }
        records.takeIf { it.isNotEmpty() }?.let { RecordTable(table.name, it) }
    }

    private fun describe(table: TableSpec, record: Record): LinkedRecord {
        val label = label(table, record)
        return LinkedRecord(table.name, record.id, label.text, label.concise, fields(table, record))
    }

    private class Label(val text: String, val concise: Boolean)

    private fun label(table: TableSpec, record: Record): Label {
        val cells = descriptiveColumns(table).mapNotNull { record[it] }
        val first = cells.firstOrNull() ?: return Label(record.id, true)
        if (first.length > LABEL_LENGTH) return Label(first.take(LABEL_LENGTH).trimEnd() + "…", false)
        val second = cells.drop(1).firstOrNull { it.length <= LABEL_LENGTH && it !in first }
        return Label(listOfNotNull(first, second).joinToString(" · "), true)
    }

    private fun fields(table: TableSpec, record: Record): List<Pair<String, String>> =
        table.columns.filter { it != Publications.ID && it != table.id }.mapNotNull { column -> record[column]?.let { column to it } }

    private fun descriptiveColumns(table: TableSpec): List<String> {
        val technical = setOf(Publications.ID, table.id, spec.note) + table.references.keys + table.lists
        return table.columns.filterNot { it in technical }
    }

    companion object {
        private const val LABEL_LENGTH = 48
    }
}

fun Record.key(): RecordKey = RecordKey(publicationId, id)
