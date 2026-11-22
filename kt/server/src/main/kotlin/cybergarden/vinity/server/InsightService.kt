package cybergarden.vinity.server

import cybergarden.vinity.api.AppRoute
import cybergarden.vinity.api.AppScreen
import cybergarden.vinity.api.DemoCaseDto
import cybergarden.vinity.api.OverviewDto
import cybergarden.vinity.api.QualityDto
import cybergarden.vinity.api.SetRef
import cybergarden.vinity.api.SourceDto
import cybergarden.vinity.api.SystemDto
import cybergarden.vinity.domain.model.Issues
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.port.DocumentInspector
import cybergarden.vinity.domain.port.TextNormalizer
import cybergarden.vinity.domain.usecase.ArticleIndex
import cybergarden.vinity.domain.usecase.AssessQuality
import cybergarden.vinity.domain.usecase.BuildOverview
import cybergarden.vinity.domain.usecase.ValueCatalog
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class InsightService(
    private val library: Library,
    private val inspector: DocumentInspector,
    normalizer: TextNormalizer,
) {
    private class Cached<T>(val catalog: ValueCatalog, val gold: ValueCatalog?, val value: T)

    private val assess = AssessQuality(normalizer)
    private val buildOverview = BuildOverview()
    private val qualities = ConcurrentHashMap<Pair<SetRef, SourceDto>, Cached<QualityDto>>()
    private val overviews = ConcurrentHashMap<Pair<SetRef, SourceDto>, Cached<OverviewDto>>()

    fun quality(ref: SetRef, requested: SourceDto?): QualityDto? {
        if (!library.known(ref)) return null
        val source = library.source(ref, requested) ?: return null
        val catalog = library.catalog(ref, source, complete = true) ?: return null
        val gold = if (source == SourceDto.RESULTS && library.canCompare(ref)) library.catalog(ref, SourceDto.GOLD, complete = true) else null
        qualities[ref to source]?.takeIf { it.catalog === catalog && it.gold === gold }?.let { return it.value }
        val quality = assess(catalog, gold?.index?.submission)
        val spec = catalog.index.profile.values
        val goldSide = gold ?: catalog
        val dto = QualityDto(
            quotes = quality.quotes.toDto(spec),
            normalization = quality.normalization.toDto(spec),
            checks = quality.checks.toDto(
                { it.toIssueDto(catalog.index, catalog.reviews) },
                { it.toIssueDto(goldSide.index, goldSide.reviews) },
            ),
        )
        qualities[ref to source] = Cached(catalog, gold, dto)
        return dto
    }

    fun overview(ref: SetRef, requested: SourceDto?): OverviewDto? {
        if (!library.known(ref)) return null
        val source = library.source(ref, requested) ?: return OverviewDto(emptyList())
        val catalog = library.catalog(ref, source, complete = true) ?: return OverviewDto(emptyList())
        overviews[ref to source]?.takeIf { it.catalog === catalog }?.let { return it.value }
        val dto = OverviewDto(buildOverview(catalog).map { it.toDto() })
        overviews[ref to source] = Cached(catalog, null, dto)
        return dto
    }

    fun system(): SystemDto {
        val sets = library.sets()
        var articles = 0
        var pages = 0
        var scans = 0
        var values = 0
        var issues = 0
        sets.forEach { ref ->
            val index = library.index(ref, library.source(ref, null))
            val ids = library.articleIds(ref, index)
            articles += ids.size
            ids.mapNotNull { inspector.cached(library.pdf(ref, it).toString()) }.forEach { document ->
                pages += document.pageCount
                if (document.pagesWithoutText.isNotEmpty()) scans++
            }
            values += index?.allValues?.size ?: 0
            issues += index?.allIssues?.size ?: 0
        }
        return SystemDto(articles, pages, scans, values, issues, sets.size, library.profileNames().size)
    }

    fun demo(): List<DemoCaseDto> {
        val sets = library.sets().filterNot { library.uploads(it) }.sortedBy { if (it.set == PREFERRED_SET) 0 else 1 }
        return issueCases(sets) + listOfNotNull(conversionCase(sets), scanCase(sets))
    }

    fun warmUp() {
        thread(isDaemon = true, name = "warm-up") {
            val (withData, withoutData) = library.sets().partition { library.sources(it).isNotEmpty() }
            withData.forEach { ref ->
                runCatching { library.catalog(ref, library.source(ref, null), complete = true)?.facts }
            }
            (withData + withoutData).forEach { ref ->
                library.articleIds(ref, library.index(ref, library.source(ref, null))).forEach { id ->
                    runCatching { inspector.inspect(library.pdf(ref, id).toString()) }
                }
            }
        }
    }

    private class IssueSource(val ref: SetRef, val source: SourceDto, val index: ArticleIndex, val issues: List<Record>)

    private fun issueCases(sets: List<SetRef>): List<DemoCaseDto> {
        val sources = sets.mapNotNull { ref ->
            val source = library.source(ref, null) ?: return@mapNotNull null
            val index = library.index(ref, source) ?: return@mapNotNull null
            val issues = index.allIssues
                .filter { it[Issues.CHECK] != null && index.issueValues(it).isNotEmpty() }
                .sortedBy { if (it[Issues.CHECK] in index.profile.checks) 0 else 1 }
                .distinctBy { it[Issues.CHECK] }
            IssueSource(ref, source, index, issues)
        }
        val checks = mutableSetOf<String>()
        val taken = mutableMapOf<SetRef, Int>()
        val cases = mutableListOf<DemoCaseDto>()
        repeat(ISSUES_PER_SET) { round ->
            sources.forEach { candidate ->
                val issue = candidate.issues.firstOrNull { it[Issues.CHECK] !in checks }
                if (cases.size >= ISSUE_CASES || issue == null || (taken[candidate.ref] ?: 0) > round) return@forEach
                val check = issue[Issues.CHECK].orEmpty()
                checks += check
                taken[candidate.ref] = (taken[candidate.ref] ?: 0) + 1
                cases += DemoCaseDto(
                    title = candidate.index.profile.display.checks[check]?.title ?: check,
                    detail = "${candidate.index.profile.title} · ${issue.publicationId}: ${issue[Issues.DESCRIPTION].orEmpty().take(DETAIL_LENGTH)}",
                    route = AppRoute(AppScreen.ARTICLE, candidate.ref, candidate.source, article = issue.publicationId, issue = issue.id).encode(),
                )
            }
        }
        return cases
    }

    private fun conversionCase(sets: List<SetRef>): DemoCaseDto? = sets.firstNotNullOfOrNull { ref ->
        val source = library.source(ref, null) ?: return@firstNotNullOfOrNull null
        val index = library.index(ref, source) ?: return@firstNotNullOfOrNull null
        val spec = index.profile.values
        val conversions = index.profile.units.conversions.map { it.from to it.to }.toSet()
        index.allValues.firstOrNull { row ->
            row[spec.rawValue] != null && row[spec.value] != null && (row[spec.rawUnit] to row[spec.unit]) in conversions
        }?.let { row ->
            DemoCaseDto(
                title = "Нормализация «в статье → у нас»",
                detail = "${index.profile.title} · ${row.publicationId}: «${written(row, spec)}» → ${row[spec.value]} ${row[spec.unit]}",
                route = AppRoute(AppScreen.ARTICLE, ref, source, article = row.publicationId, value = row.id).encode(),
            )
        }
    }

    private fun scanCase(sets: List<SetRef>): DemoCaseDto? = sets.firstNotNullOfOrNull { ref ->
        val source = library.source(ref, null)
        library.articleIds(ref, library.index(ref, source)).firstNotNullOfOrNull { id ->
            inspector.cached(library.pdf(ref, id).toString())?.pagesWithoutText?.takeIf { it.isNotEmpty() }?.let { pages ->
                DemoCaseDto(
                    title = "Скан без текстового слоя",
                    detail = "${library.profile(ref.profile).title} · $id: страницы без текста ${pages.take(SCAN_PAGES).joinToString(", ")}",
                    route = AppRoute(AppScreen.ARTICLE, ref, source, article = id).encode(),
                )
            }
        }
    }

    private companion object {
        const val PREFERRED_SET = "dev"
        const val ISSUE_CASES = 3
        const val ISSUES_PER_SET = 2
        const val DETAIL_LENGTH = 160
        const val SCAN_PAGES = 5
    }
}
