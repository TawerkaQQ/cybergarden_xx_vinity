package cybergarden.vinity.domain.model

data class TableReference(val table: String, val label: String) {
    companion object {
        private val PATTERN = Regex("""^(?:Table|Таблица)\s+(\S+)\s*,\s*(?:строка|столбец)\s*(.*)$""", RegexOption.IGNORE_CASE)

        fun parse(fragment: String): TableReference? =
            PATTERN.find(fragment.trim())?.let { TableReference(it.groupValues[1], it.groupValues[2].trim()) }
    }
}
