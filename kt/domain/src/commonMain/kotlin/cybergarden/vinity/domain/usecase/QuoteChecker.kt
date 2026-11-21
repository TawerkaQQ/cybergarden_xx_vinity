package cybergarden.vinity.domain.usecase

import cybergarden.vinity.domain.model.Profile
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.RecordKey
import cybergarden.vinity.domain.model.TableFragment
import cybergarden.vinity.domain.model.TableReference
import cybergarden.vinity.domain.port.PageTextSource
import cybergarden.vinity.domain.port.TextNormalizer

enum class QuoteStatus { FOUND, ROW_LABEL, ELSEWHERE, MISSING, NO_PAGE_TEXT, NO_QUOTE }

data class QuoteCheck(val status: QuoteStatus, val variant: String? = null, val pages: List<Int> = emptyList()) {
    val found: Boolean get() = status == QuoteStatus.FOUND || status == QuoteStatus.ROW_LABEL
}

class QuoteChecker(
    private val profile: Profile,
    private val pages: PageTextSource,
    private val normalizer: TextNormalizer,
) {
    private class Variant(val name: String, val text: String)

    private val texts = mutableMapOf<String, Map<Int, List<Variant>>?>()
    private val checks = mutableMapOf<Triple<String, Int?, String>, QuoteCheck>()

    fun checkAll(rows: List<Record>): Map<RecordKey, QuoteCheck> {
        val spec = profile.values
        return rows.associate { row -> row.key() to check(row.publicationId, row.integer(spec.page), row[spec.fragment]) }
    }

    fun variants(publicationId: String): List<String> =
        texts(publicationId)?.values?.flatten()?.filter { it.text.isNotEmpty() }?.map { it.name }?.distinct().orEmpty()

    private fun check(publicationId: String, page: Int?, fragment: String?): QuoteCheck {
        val quote = fragment?.takeIf { it.isNotBlank() } ?: return QuoteCheck(QuoteStatus.NO_QUOTE)
        if (profile.sources.tableFragment == TableFragment.ROW_LABEL && TableReference.parse(quote) != null) {
            return QuoteCheck(QuoteStatus.ROW_LABEL, pages = listOfNotNull(page))
        }
        return checks.getOrPut(Triple(publicationId, page, quote)) { locate(publicationId, page, quote) }
    }

    private fun locate(publicationId: String, page: Int?, quote: String): QuoteCheck {
        val document = texts(publicationId) ?: return QuoteCheck(QuoteStatus.NO_PAGE_TEXT, pages = listOfNotNull(page))
        val needle = normalizer.normalize(quote)
        if (needle.isEmpty()) return QuoteCheck(QuoteStatus.NO_QUOTE)
        val own = page?.let { document[it] }
        own?.firstOrNull { needle in it.text }?.let { return QuoteCheck(QuoteStatus.FOUND, it.name, listOf(page)) }
        val elsewhere = document.filter { (number, variants) -> number != page && variants.any { needle in it.text } }.keys.sorted()
        return when {
            elsewhere.isNotEmpty() -> QuoteCheck(QuoteStatus.ELSEWHERE, pages = elsewhere)
            own != null && own.all { it.text.isEmpty() } -> QuoteCheck(QuoteStatus.NO_PAGE_TEXT, pages = listOf(page))
            else -> QuoteCheck(QuoteStatus.MISSING, pages = listOfNotNull(page))
        }
    }

    private fun texts(publicationId: String): Map<Int, List<Variant>>? = texts.getOrPut(publicationId) {
        pages.pages(publicationId)?.associate { page ->
            val preferred = profile.sources.textVariants.filter { it in page.variants }
            val names = preferred + (page.variants.keys - preferred.toSet())
            page.page to names.map { Variant(it, normalizer.normalize(page.variants.getValue(it))) }
        }
    }
}
