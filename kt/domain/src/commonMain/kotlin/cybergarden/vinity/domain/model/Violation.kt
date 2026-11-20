package cybergarden.vinity.domain.model

enum class Severity { ERROR, WARNING }

data class Violation(
    val severity: Severity,
    val table: String,
    val publicationId: String?,
    val recordId: String?,
    val message: String,
)
