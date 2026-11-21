package cybergarden.vinity.domain.model

enum class MatchMode { LOOSE, STRICT }

data class ValuesSpec(
    val table: String,
    val roles: Map<String, String>,
    val mirrors: Map<String, String>,
    val metrics: Map<String, String>,
    val duplicateKey: List<String>,
    val match: Map<MatchMode, List<String>>,
) {
    fun column(key: String): String = roles[key] ?: key

    val metric = column(Role.METRIC)
    val rawValue = column(Role.RAW_VALUE)
    val rawUnit = column(Role.RAW_UNIT)
    val value = column(Role.VALUE)
    val unit = column(Role.UNIT)
    val aggregation = column(Role.AGGREGATION)
    val origin = column(Role.ORIGIN)
    val cited = column(Role.CITED)
    val page = column(Role.PAGE)
    val section = column(Role.SECTION)
    val sourceTable = column(Role.TABLE)
    val figure = column(Role.FIGURE)
    val fragment = column(Role.FRAGMENT)
    val status = column(Role.STATUS)
    val duplicateOf = column(Role.DUPLICATE_OF)
    val note = column(Role.NOTE)

    fun matchColumns(mode: MatchMode): List<String> = match[mode].orEmpty().map(::column)

    fun isScored(row: Record): Boolean =
        row[status] == Status.EXTRACTED &&
            row.number(value) != null &&
            (metrics.isEmpty() || row[metric] in metrics)
}

object Role {
    const val METRIC = "metric"
    const val RAW_VALUE = "raw_value"
    const val RAW_UNIT = "raw_unit"
    const val VALUE = "value"
    const val UNIT = "unit"
    const val AGGREGATION = "aggregation"
    const val ORIGIN = "origin"
    const val CITED = "cited"
    const val PAGE = "page"
    const val SECTION = "section"
    const val TABLE = "table"
    const val FIGURE = "figure"
    const val FRAGMENT = "fragment"
    const val STATUS = "status"
    const val DUPLICATE_OF = "duplicate_of"
    const val NOTE = "note"
}

object Origin {
    const val PRIMARY = "primary"
    const val CITED = "cited"
}

object Status {
    const val EXTRACTED = "extracted"
    const val PRESENT_NOT_EXTRACTABLE = "present_not_extractable"
    const val ABSENT = "absent"
}
