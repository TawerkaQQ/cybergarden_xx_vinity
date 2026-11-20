package cybergarden.vinity.api

import cybergarden.vinity.domain.model.Issues
import cybergarden.vinity.domain.model.MatchMode
import cybergarden.vinity.domain.model.Publications
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.ValuesSpec
import cybergarden.vinity.domain.usecase.Counts
import cybergarden.vinity.domain.usecase.ScoreReport

fun Submission.toDatasetDto(ref: DatasetRef): DatasetDto {
    val spec = profile.values
    val links = profile.valueTable.references.keys - spec.duplicateOf
    return DatasetDto(
        ref = ref,
        title = profile.title,
        rowCounts = profile.allTables.associate { it.name to rows(it).size },
        publications = publications.map { it.toPublicationDto() },
        values = values.map { it.toValueDto(spec, links) },
        issues = issues.map { it.toIssueDto() },
    )
}

fun ScoreReport.toDto() = ScoreDto(
    strict = mode == MatchMode.STRICT,
    matchedBy = matchedBy,
    publicationsScored = publicationsScored,
    publicationsInGold = publicationsInGold,
    scopeAgreed = scope.agreed,
    scopeTotal = scope.total,
    allMentions = allMentions.toDto(),
    primaryRecords = primaryRecords.toDto(),
    byMetric = byMetric.mapValues { it.value.toDto() },
)

private fun Record.toPublicationDto() = PublicationDto(
    id = publicationId,
    title = get(Publications.TITLE),
    year = get(Publications.YEAR),
    doi = get(Publications.DOI),
    inScope = get(Publications.IN_SCOPE)?.toBooleanStrictOrNull(),
    exclusionReason = get(Publications.EXCLUSION_REASON),
)

private fun Record.toValueDto(spec: ValuesSpec, links: Set<String>) = ValueDto(
    id = id,
    publicationId = publicationId,
    metric = get(spec.metric),
    rawValue = get(spec.rawValue),
    value = number(spec.value),
    unit = get(spec.unit),
    aggregation = get(spec.aggregation),
    origin = get(spec.origin),
    cited = get(spec.cited),
    page = integer(spec.page),
    table = get(spec.sourceTable),
    figure = get(spec.figure),
    fragment = get(spec.fragment),
    status = get(spec.status),
    duplicateOf = get(spec.duplicateOf),
    note = get(spec.note),
    links = links.mapNotNull { column -> get(column)?.let { column to it } }.toMap(),
)

private fun Record.toIssueDto() = IssueDto(
    id = id,
    publicationId = publicationId,
    recordIds = list(Issues.RECORDS),
    checkName = get(Issues.CHECK),
    description = get(Issues.DESCRIPTION),
    page = integer(Issues.PAGE),
    fragment = get(Issues.FRAGMENT),
)

private fun Counts.toDto() = CountsDto(
    truePositive = truePositive,
    falsePositive = falsePositive,
    falseNegative = falseNegative,
    precision = precision,
    recall = recall,
    f1 = f1,
)
