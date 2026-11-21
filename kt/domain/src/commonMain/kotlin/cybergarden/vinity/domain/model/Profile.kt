package cybergarden.vinity.domain.model

data class Profile(
    val name: String,
    val title: String,
    val scope: String,
    val tables: List<TableSpec>,
    val values: ValuesSpec,
    val issues: TableSpec,
    val sources: SourcesSpec,
    val units: UnitsSpec,
    val ranges: Map<String, ClosedFloatingPointRange<Double>>,
    val checks: List<String>,
    val display: DisplaySpec,
    val rules: List<CheckRule> = checks.map { CheckRule(it, it, emptyMap()) },
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
        display.references().filterNot { it in valueTable.references }
            .forEach { fail("display: $it не ссылка таблицы ${values.table}") }
        display.overview.filterIsInstance<ChartSpec.Scatter>()
            .filterNot { chart -> valueTable.references[chart.x.column]?.let { table(it).columns.contains(chart.x.field) } == true }
            .forEach { fail("display: нет колонки ${it.x.field} у таблицы по ссылке ${it.x.column}") }
    }

    fun table(name: String): TableSpec = allTables.firstOrNull { it.name == name } ?: fail("нет таблицы $name")

    private fun fail(message: String): Nothing = throw IllegalArgumentException("Профиль $name: $message")
}

data class CheckRule(val name: String, val rule: String, val params: Map<String, List<String>>) {
    fun list(key: String): List<String> = params[key].orEmpty()

    fun number(key: String, index: Int): Double? = params[key]?.getOrNull(index)?.toDoubleOrNull()
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
) {
    fun quoteCandidates(fragment: String): List<String> = listOfNotNull(
        fragment,
        rowLabel(fragment),
    )

    fun rowLabel(fragment: String): String? =
        TableReference.parse(fragment)?.label?.takeIf { tableFragment == TableFragment.ROW_LABEL && it.isNotEmpty() }
}

enum class TableFragment { ROW_LABEL, LITERAL }

data class UnitsSpec(
    val aliases: Map<String, String>,
    val conversions: List<UnitConversion>,
)

data class UnitConversion(val from: String, val to: String, val factor: Double)

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
