package cybergarden.vinity.api

import cybergarden.vinity.domain.model.Issue
import cybergarden.vinity.domain.model.MetricResult
import cybergarden.vinity.domain.model.Publication
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.usecase.Counts
import cybergarden.vinity.domain.usecase.MatchMode
import cybergarden.vinity.domain.usecase.ScoreReport

fun Submission.toDatasetDto(name: String) = DatasetDto(
    name = name,
    deviceCount = devices.size,
    publications = publications.map { it.toDto() },
    results = results.map { it.toDto() },
    issues = issues.map { it.toDto() },
)

fun ScoreReport.toDto() = ScoreDto(
    strict = mode == MatchMode.STRICT,
    publicationsScored = publicationsScored,
    publicationsInGold = publicationsInGold,
    scopeAgreed = scope.agreed,
    scopeTotal = scope.total,
    allMentions = allMentions.toDto(),
    primaryRecords = primaryRecords.toDto(),
    byMetric = byMetric.mapValues { it.value.toDto() },
)

private fun Publication.toDto() = PublicationDto(
    id = publicationId,
    title = title,
    year = year,
    doi = doi,
    inScope = inScope,
    exclusionReason = exclusionReason,
)

private fun MetricResult.toDto() = ResultDto(
    id = resultId,
    publicationId = publicationId,
    deviceId = deviceId,
    measurementId = measurementId,
    metric = metricName,
    value = normalizedValue,
    unit = normalizedUnit,
    aggregation = aggregation?.code,
    spread = spreadValue,
    origin = origin?.code,
    citedReference = citedPublicationId,
    page = source.page,
    section = source.section,
    table = source.table,
    figure = source.figure,
    fragment = source.fragment,
    status = extractionStatus?.code,
    duplicateOf = duplicateOf,
    note = note,
)

private fun Issue.toDto() = IssueDto(
    id = issueId,
    publicationId = publicationId,
    recordIds = recordIds,
    checkName = checkName,
    description = description,
    page = sourcePage,
    fragment = sourceFragment,
)

private fun Counts.toDto() = CountsDto(
    truePositive = truePositive,
    falsePositive = falsePositive,
    falseNegative = falseNegative,
    precision = precision,
    recall = recall,
    f1 = f1,
)
