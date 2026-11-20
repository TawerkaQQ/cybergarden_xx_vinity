package cybergarden.vinity.domain.model

data class Profile(
    val name: String,
    val title: String,
    val tables: List<TableSpec>,
    val values: ValuesSpec,
    val issues: TableSpec,
    val sources: SourcesSpec,
) {
    val allTables: List<TableSpec> = tables + issues
    val publications: TableSpec = table(Publications.TABLE)
    val valueTable: TableSpec = table(values.table)

    init {
        allTables.forEach { spec ->
            val known = spec.columns.toSet()
            val declared = listOf(spec.id, Publications.ID) + spec.references.keys + spec.lists + spec.vocabularies.keys
            declared.filterNot { it in known }.forEach { fail("в таблице ${spec.name} нет колонки $it") }
            spec.references.values.filter { target -> allTables.none { it.name == target } }
                .forEach { fail("таблица ${spec.name} ссылается на неизвестную таблицу $it") }
        }
        val valueColumns = valueTable.columns.toSet()
        (values.roles.values + values.mirrors.keys + values.mirrors.values + values.duplicateKey.map(values::column) +
            values.match.values.flatten().map(values::column))
            .filterNot { it in valueColumns }
            .forEach { fail("в таблице значений ${values.table} нет колонки $it") }
    }

    fun table(name: String): TableSpec = allTables.firstOrNull { it.name == name } ?: fail("нет таблицы $name")

    private fun fail(message: String): Nothing = throw IllegalArgumentException("Профиль $name: $message")
}

data class TableSpec(
    val name: String,
    val file: String,
    val id: String,
    val columns: List<String>,
    val references: Map<String, String>,
    val lists: Set<String>,
    val vocabularies: Map<String, Set<String>>,
)

data class SourcesSpec(
    val tableFragment: TableFragment,
    val textVariants: List<String>,
)

enum class TableFragment { ROW_LABEL, LITERAL }

object Publications {
    const val TABLE = "publications"
    const val ID = "publication_id"
    const val TITLE = "title"
    const val YEAR = "year"
    const val DOI = "DOI"
    const val AUTHORS = "authors"
    const val IN_SCOPE = "in_scope"
    const val EXCLUSION_REASON = "exclusion_reason"
}

object Issues {
    const val ID = "issue_id"
    const val RECORDS = "record_ids"
    const val CHECK = "check_name"
    const val DESCRIPTION = "description"
    const val PAGE = "source_page"
    const val FRAGMENT = "source_fragment"

    fun table(name: String, file: String, valueTable: String) = TableSpec(
        name = name,
        file = file,
        id = ID,
        columns = listOf(Publications.ID, ID, RECORDS, CHECK, DESCRIPTION, PAGE, FRAGMENT),
        references = mapOf(RECORDS to valueTable),
        lists = setOf(RECORDS),
        vocabularies = emptyMap(),
    )
}
