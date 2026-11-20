package cybergarden.vinity.domain.usecase

import cybergarden.vinity.domain.model.ExtractionStatus
import cybergarden.vinity.domain.model.ResultOrigin
import cybergarden.vinity.domain.model.Severity
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.Violation
import cybergarden.vinity.domain.port.PageTextSource

class ValidateSubmission(private val pageTexts: PageTextSource?) {

    operator fun invoke(submission: Submission): List<Violation> = buildList {
        addAll(uniqueness(submission))
        addAll(scope(submission))
        addAll(references(submission))
        addAll(results(submission))
        if (pageTexts != null) addAll(fragments(submission, pageTexts))
    }

    private fun uniqueness(s: Submission): List<Violation> = listOf(
        duplicates("publications", s.publications.map { it.publicationId to it.publicationId }),
        duplicates("materials", s.materials.map { it.publicationId to it.materialId }),
        duplicates("devices", s.devices.map { it.publicationId to it.deviceId }),
        duplicates("layers", s.layers.map { it.publicationId to it.layerId }),
        duplicates("measurements", s.measurements.map { it.publicationId to it.measurementId }),
        duplicates("results", s.results.map { it.publicationId to it.resultId }),
        duplicates("issues", s.issues.map { it.publicationId to it.issueId }),
    ).flatten()

    private fun duplicates(table: String, keys: List<Pair<String, String>>) =
        keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.map { (publication, id) ->
            error(table, publication, id, "идентификатор повторяется")
        }

    private fun scope(s: Submission): List<Violation> {
        val excluded = s.publications.filter { it.inScope == false }.map { it.publicationId }.toSet()
        val rowsOfExcluded = listOf(
            "materials" to s.materials.map { it.publicationId },
            "devices" to s.devices.map { it.publicationId },
            "layers" to s.layers.map { it.publicationId },
            "measurements" to s.measurements.map { it.publicationId },
            "results" to s.results.map { it.publicationId },
        ).flatMap { (table, ids) ->
            ids.filter { it in excluded }.distinct().map { error(table, it, null, "строки у статьи с in_scope=false") }
        }
        val reasons = s.publications.mapNotNull { publication ->
            when {
                publication.inScope == null ->
                    error("publications", publication.publicationId, null, "in_scope не заполнен")
                publication.inScope == false && publication.exclusionReason == null ->
                    error("publications", publication.publicationId, null, "нет exclusion_reason")
                publication.inScope == true && publication.exclusionReason != null ->
                    error("publications", publication.publicationId, null, "exclusion_reason у статьи в предметной области")
                else -> null
            }
        }
        return rowsOfExcluded + reasons
    }

    private fun references(s: Submission): List<Violation> {
        val publications = s.publications.map { it.publicationId }.toSet()
        val materials = s.materials.map { it.publicationId to it.materialId }.toSet()
        val devices = s.devices.map { it.publicationId to it.deviceId }.toSet()
        val measurements = s.measurements.map { it.publicationId to it.measurementId }.toSet()
        val results = s.results.map { it.publicationId to it.resultId }.toSet()

        fun missing(table: String, publication: String, record: String, kind: String, ref: String?, known: Set<Pair<String, String>>) =
            if (ref != null && (publication to ref) !in known) error(table, publication, record, "нет $kind $ref") else null

        return buildList<Violation?> {
            val orphanTables = listOf(
                "materials" to s.materials.map { it.publicationId },
                "devices" to s.devices.map { it.publicationId },
                "results" to s.results.map { it.publicationId },
                "issues" to s.issues.map { it.publicationId },
            )
            orphanTables.forEach { (table, ids) ->
                ids.filter { it !in publications }.distinct().forEach { add(error(table, it, null, "статьи нет в publications")) }
            }
            s.devices.forEach { add(missing("devices", it.publicationId, it.deviceId, "материала", it.absorberMaterialId, materials)) }
            s.layers.forEach { add(missing("layers", it.publicationId, it.layerId, "прибора", it.deviceId, devices)) }
            s.measurements.forEach { m ->
                m.deviceIds.forEach { add(missing("measurements", m.publicationId, m.measurementId, "прибора", it, devices)) }
            }
            s.results.forEach { r ->
                add(missing("results", r.publicationId, r.resultId, "прибора", r.deviceId, devices))
                add(missing("results", r.publicationId, r.resultId, "измерения", r.measurementId, measurements))
                add(missing("results", r.publicationId, r.resultId, "материала", r.materialId, materials))
                add(missing("results", r.publicationId, r.resultId, "основной записи", r.duplicateOf, results))
            }
            s.issues.forEach { i ->
                i.recordIds.forEach { add(missing("issues", i.publicationId, i.issueId, "записи", it, results)) }
            }
        }.filterNotNull()
    }

    private fun results(s: Submission): List<Violation> = s.results.mapNotNull { r ->
        val metric = r.scoredMetric
        when {
            r.origin == ResultOrigin.CITED && (r.deviceId != null || r.measurementId != null) ->
                error("results", r.publicationId, r.resultId, "у cited-строки заполнены прибор или измерение")
            r.origin == ResultOrigin.CITED && r.citedPublicationId == null ->
                warning("results", r.publicationId, r.resultId, "у cited-строки нет cited_publication_id")
            r.extractionStatus == ExtractionStatus.EXTRACTED && r.normalizedValue == null ->
                error("results", r.publicationId, r.resultId, "extracted без значения")
            metric != null && r.normalizedValue != null && r.normalizedUnit != metric.unit ->
                error("results", r.publicationId, r.resultId, "${metric.code} в ${r.normalizedUnit}, нужно ${metric.unit}")
            r.metricValue != r.normalizedValue || r.metricUnit != r.normalizedUnit ->
                error("results", r.publicationId, r.resultId, "metric_* не совпадает с normalized_*")
            r.source.page == null ->
                error("results", r.publicationId, r.resultId, "нет source_page")
            else -> null
        }
    }

    private fun fragments(s: Submission, source: PageTextSource): List<Violation> {
        val located = s.results.map { Triple("results", it.publicationId, it.resultId) to (it.source.page to it.source.fragment) } +
            s.issues.map { Triple("issues", it.publicationId, it.issueId) to (it.sourcePage to it.sourceFragment) }
        val pagesByPublication = located.map { it.first.second }.distinct().associateWith { source.pages(it) }

        return located.mapNotNull { (where, location) ->
            val (table, publication, record) = where
            val (page, fragment) = location
            if (page == null || fragment == null || fragment.isTableReference()) return@mapNotNull null
            val pages = pagesByPublication[publication]
                ?: return@mapNotNull warning(table, publication, record, "нет текста страниц статьи")
            val text = pages.firstOrNull { it.page == page }?.text
                ?: return@mapNotNull error(table, publication, record, "нет страницы $page")
            if (fragment.squashed() in text.squashed()) null
            else error(table, publication, record, "цитата не найдена на странице $page: «$fragment»")
        }
    }

    private fun String.isTableReference() = startsWith("Table", ignoreCase = true) || contains("строка")

    private fun String.squashed() = replace(WHITESPACE, " ").trim()

    private fun error(table: String, publication: String?, record: String?, message: String) =
        Violation(Severity.ERROR, table, publication, record, message)

    private fun warning(table: String, publication: String?, record: String?, message: String) =
        Violation(Severity.WARNING, table, publication, record, message)

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
