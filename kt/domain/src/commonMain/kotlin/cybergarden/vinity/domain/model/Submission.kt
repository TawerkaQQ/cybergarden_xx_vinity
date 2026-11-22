package cybergarden.vinity.domain.model

data class Record(
    val publicationId: String,
    val id: String,
    val cells: Map<String, String>,
) {
    operator fun get(column: String): String? = cells[column]?.takeIf { it.isNotEmpty() }

    fun list(column: String): List<String> =
        get(column)?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    fun number(column: String): Double? = get(column)?.toDoubleOrNull()?.takeIf { it.isFinite() }

    fun integer(column: String): Int? = get(column)?.toIntOrNull()
}

data class Submission(
    val profile: Profile,
    val tables: Map<String, List<Record>>,
) {
    fun rows(table: TableSpec): List<Record> = tables[table.name].orEmpty()

    val publications: List<Record> get() = rows(profile.publications)
    val values: List<Record> get() = rows(profile.valueTable)
    val issues: List<Record> get() = rows(profile.issues)
}
