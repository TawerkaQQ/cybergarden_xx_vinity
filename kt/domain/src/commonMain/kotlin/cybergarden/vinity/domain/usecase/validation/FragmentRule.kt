package cybergarden.vinity.domain.usecase.validation

import cybergarden.vinity.domain.model.Issues
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.TableFragment
import cybergarden.vinity.domain.model.Violation
import cybergarden.vinity.domain.port.PageTextSource
import cybergarden.vinity.domain.port.TextNormalizer

class FragmentRule(
    private val pages: PageTextSource,
    private val normalizer: TextNormalizer,
) : SubmissionRule {

    private class Quote(val table: String, val row: Record, val page: String?, val fragment: String)

    override fun check(submission: Submission): List<Violation> {
        val profile = submission.profile
        val values = profile.values
        val rowLabels = profile.sources.tableFragment == TableFragment.ROW_LABEL
        val quotes = submission.values.mapNotNull { row ->
            row[values.fragment]?.let { Quote(values.table, row, row[values.page], it) }
        } + submission.issues.mapNotNull { row ->
            row[Issues.FRAGMENT]?.let { Quote(profile.issues.name, row, row[Issues.PAGE], it) }
        }

        return quotes.groupBy { it.row.publicationId }.flatMap { (publication, own) ->
            val text = pages.pages(publication)
                ?: return@flatMap listOf(Violation.warning(values.table, publication, null, "нет текста страниц, цитаты не проверены"))
            val variants = text.associate { page ->
                page.page to page.variants.filterKeys { it in profile.sources.textVariants }.values.map(normalizer::normalize)
            }
            own.filterNot { rowLabels && TABLE_REFERENCE.containsMatchIn(it.fragment) }.mapNotNull { quote ->
                locate(quote, variants)
            }
        }
    }

    private fun locate(quote: Quote, variants: Map<Int, List<String>>): Violation? {
        val number = quote.page?.toIntOrNull()
            ?: return Violation.error(quote.table, quote.row, "цитата без номера страницы")
        val texts = variants[number]
            ?: return Violation.error(quote.table, quote.row, "в статье нет страницы $number")
        val needle = normalizer.normalize(quote.fragment)
        if (needle.isEmpty() || texts.any { needle in it }) return null
        val elsewhere = variants.filterValues { page -> page.any { needle in it } }.keys.sorted()
        val hint = if (elsewhere.isEmpty()) "" else ", есть на стр. ${elsewhere.joinToString()}"
        val empty = if (texts.all { it.isEmpty() }) ", у страницы нет текста" else ""
        return Violation.error(quote.table, quote.row, "цитата не найдена на стр. $number$hint$empty: «${quote.fragment.take(80)}»")
    }

    private companion object {
        val TABLE_REFERENCE = Regex("^(Table|Таблица)\\s+\\S+\\s*,\\s*(строка|столбец)", RegexOption.IGNORE_CASE)
    }
}
