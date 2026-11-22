package cybergarden.vinity.domain.model

enum class Severity { ERROR, WARNING }

data class Violation(
    val severity: Severity,
    val table: String,
    val publicationId: String?,
    val recordId: String?,
    val message: String,
) {
    companion object {
        fun error(table: String, publicationId: String?, recordId: String?, message: String) =
            Violation(Severity.ERROR, table, publicationId, recordId, message)

        fun warning(table: String, publicationId: String?, recordId: String?, message: String) =
            Violation(Severity.WARNING, table, publicationId, recordId, message)

        fun error(table: String, record: Record, message: String) = error(table, record.publicationId, record.id, message)
    }
}
