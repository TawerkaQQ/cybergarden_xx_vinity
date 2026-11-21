package cybergarden.vinity.domain.usecase

import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.RecordKey
import cybergarden.vinity.domain.model.Reviews
import cybergarden.vinity.domain.model.ValueReview

data class ValueFacts(
    val row: Record,
    val kind: ValueKind,
    val record: LinkedRecord?,
    val links: List<LinkedRecord>,
    val issues: List<Record>,
    val activeIssues: Int,
    val quote: QuoteCheck,
    val review: ValueReview?,
)

class ValueCatalog(
    val index: ArticleIndex,
    private val quotes: (String) -> Map<RecordKey, QuoteCheck>,
    val reviews: Reviews,
) {
    private val spec = index.profile.values
    private val recordColumn = index.profile.display.record?.column
    private val publications: Map<String, Lazy<List<ValueFacts>>> = index.allValues.map { it.publicationId }.distinct()
        .associateWith { publication -> lazy { build(publication) } }

    val facts: List<ValueFacts> by lazy { publications.values.flatMap { it.value } }

    val metrics: List<Tally> by lazy {
        facts.groupingBy { it.row[spec.metric].orEmpty() }.eachCount()
            .map { Tally(it.key, it.value) }
            .sortedWith(compareByDescending<Tally> { it.count }.thenBy { it.name })
    }

    val searchText: Map<RecordKey, String> by lazy {
        facts.associate { fact ->
            val row = fact.row
            row.key() to listOfNotNull(
                row.publicationId,
                index.title(row.publicationId),
                row[spec.metric],
                fact.record?.label,
                row[spec.rawValue],
                row[spec.value],
                row[spec.fragment],
                row[spec.note],
            ).joinToString("\n").lowercase()
        }
    }

    fun of(publicationId: String): List<ValueFacts> = publications[publicationId]?.value.orEmpty()

    fun get(key: RecordKey): ValueFacts? = of(key.publicationId).firstOrNull { it.row.id == key.id }

    private fun build(publicationId: String): List<ValueFacts> {
        val checks = quotes(publicationId)
        return index.values(publicationId).map { row ->
            val issues = index.issuesOf(row)
            ValueFacts(
                row = row,
                kind = index.kind(row),
                record = recordColumn?.let { index.linkedRecord(row, it) },
                links = index.links(row),
                issues = issues,
                activeIssues = issues.count { !reviews.dismissed(it) },
                quote = checks[row.key()] ?: QuoteCheck(QuoteStatus.NO_QUOTE),
                review = reviews.value(row),
            )
        }
    }
}
