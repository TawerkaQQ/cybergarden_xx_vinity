package cybergarden.vinity.domain.model

data class RecordKey(val publicationId: String, val id: String)

enum class ValueVerdict { CONFIRMED, FIXED, REJECTED }

data class ValueReview(val verdict: ValueVerdict, val value: String?)

enum class IssueVerdict { CONFIRMED, DISMISSED }

data class IssueReview(val verdict: IssueVerdict, val reason: String?)

data class Reviews(
    val values: Map<RecordKey, ValueReview> = emptyMap(),
    val issues: Map<RecordKey, IssueReview> = emptyMap(),
) {
    fun value(row: Record): ValueReview? = values[RecordKey(row.publicationId, row.id)]

    fun issue(row: Record): IssueReview? = issues[RecordKey(row.publicationId, row.id)]

    fun dismissed(row: Record): Boolean = issue(row)?.verdict == IssueVerdict.DISMISSED
}

data class ReviewScope(val profile: String, val set: String, val source: String)
