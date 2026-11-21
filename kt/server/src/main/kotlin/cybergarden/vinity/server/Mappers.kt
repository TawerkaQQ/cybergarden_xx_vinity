package cybergarden.vinity.server

import cybergarden.vinity.api.AliasDto
import cybergarden.vinity.api.BarDto
import cybergarden.vinity.api.BinDto
import cybergarden.vinity.api.ChartDto
import cybergarden.vinity.api.ChartKindDto
import cybergarden.vinity.api.CheckCountDto
import cybergarden.vinity.api.CheckInfoDto
import cybergarden.vinity.api.ChecksQualityDto
import cybergarden.vinity.api.ConversionDto
import cybergarden.vinity.api.CountDto
import cybergarden.vinity.api.CountsDto
import cybergarden.vinity.api.FieldDto
import cybergarden.vinity.api.GoldIssuesDto
import cybergarden.vinity.api.IssueDto
import cybergarden.vinity.api.IssueReviewDto
import cybergarden.vinity.api.IssueReviewStateDto
import cybergarden.vinity.api.LineDto
import cybergarden.vinity.api.LinkDto
import cybergarden.vinity.api.MetricInfoDto
import cybergarden.vinity.api.MetricUnitsDto
import cybergarden.vinity.api.NormalizationDto
import cybergarden.vinity.api.PointDto
import cybergarden.vinity.api.ProfileDto
import cybergarden.vinity.api.QuoteDto
import cybergarden.vinity.api.QuoteQualityDto
import cybergarden.vinity.api.QuoteStatusDto
import cybergarden.vinity.api.RecordDto
import cybergarden.vinity.api.RecordTableDto
import cybergarden.vinity.api.ScoreDto
import cybergarden.vinity.api.TableInfoDto
import cybergarden.vinity.api.ValueDto
import cybergarden.vinity.api.ValueKindDto
import cybergarden.vinity.api.ValueRefDto
import cybergarden.vinity.api.ValueReviewDto
import cybergarden.vinity.api.ValueReviewStateDto
import cybergarden.vinity.domain.model.ChartLine
import cybergarden.vinity.domain.model.IssueReview
import cybergarden.vinity.domain.model.IssueVerdict
import cybergarden.vinity.domain.model.Issues
import cybergarden.vinity.domain.model.MatchMode
import cybergarden.vinity.domain.model.Profile
import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.Reviews
import cybergarden.vinity.domain.model.ValueReview
import cybergarden.vinity.domain.model.ValueVerdict
import cybergarden.vinity.domain.model.ValuesSpec
import cybergarden.vinity.domain.usecase.ArticleIndex
import cybergarden.vinity.domain.usecase.Bar
import cybergarden.vinity.domain.usecase.Bin
import cybergarden.vinity.domain.usecase.Chart
import cybergarden.vinity.domain.usecase.ChartKind
import cybergarden.vinity.domain.usecase.ChecksQuality
import cybergarden.vinity.domain.usecase.Counts
import cybergarden.vinity.domain.usecase.LinkedRecord
import cybergarden.vinity.domain.usecase.Normalization
import cybergarden.vinity.domain.usecase.Point
import cybergarden.vinity.domain.usecase.QuoteCheck
import cybergarden.vinity.domain.usecase.QuoteQuality
import cybergarden.vinity.domain.usecase.QuoteStatus
import cybergarden.vinity.domain.usecase.RecordTable
import cybergarden.vinity.domain.usecase.ScoreReport
import cybergarden.vinity.domain.usecase.Tally
import cybergarden.vinity.domain.usecase.ValueFacts
import cybergarden.vinity.domain.usecase.ValueKind

fun Profile.toDto(): ProfileDto {
    val record = display.record
    val metricCodes = (values.metrics.keys + display.metrics.keys).distinct()
    return ProfileDto(
        name = name,
        title = title,
        scope = scope,
        recordTitle = record?.title,
        recordsTab = display.recordsTab,
        groupByRecord = record?.group,
        tables = tables.map { TableInfoDto(it.name, it.columns.size) },
        metrics = metricCodes.map { code ->
            MetricInfoDto(code, display.metrics[code]?.title, values.metrics[code], display.metrics[code]?.explain)
        },
        checks = checks.map { CheckInfoDto(it, display.checks[it]?.title, display.checks[it]?.rule, true) } +
            display.checks.filterKeys { it !in checks }.map { (name, check) -> CheckInfoDto(name, check.title, check.rule, false) },
        aggregations = display.aggregations,
    )
}

fun ValueFacts.toDto(spec: ValuesSpec) = ValueDto(
    id = row.id,
    publicationId = row.publicationId,
    metric = row[spec.metric],
    value = row[spec.value],
    unit = row[spec.unit],
    rawValue = row[spec.rawValue],
    rawUnit = row[spec.rawUnit],
    aggregation = row[spec.aggregation],
    origin = row[spec.origin],
    cited = row[spec.cited],
    status = row[spec.status],
    page = row.integer(spec.page),
    section = row[spec.section],
    table = row[spec.sourceTable],
    figure = row[spec.figure],
    kind = kind.toDto(),
    fragment = row[spec.fragment],
    duplicateOf = row[spec.duplicateOf],
    note = row[spec.note],
    record = record?.id,
    recordLabel = record?.label,
    links = links.map { it.toDto() },
    issues = issues.map { it.id },
    quote = quote.toDto(),
    review = review?.toDto(),
)

fun Record.toRefDto(spec: ValuesSpec) = ValueRefDto(
    id = id,
    metric = get(spec.metric),
    value = get(spec.value) ?: get(spec.rawValue),
    unit = get(spec.unit),
    page = integer(spec.page),
    fragment = get(spec.fragment),
)

fun Record.toIssueDto(index: ArticleIndex, reviews: Reviews) = IssueDto(
    id = id,
    publicationId = publicationId,
    check = get(Issues.CHECK),
    description = get(Issues.DESCRIPTION),
    page = integer(Issues.PAGE),
    fragment = get(Issues.FRAGMENT),
    values = index.issueValues(this).map { it.toRefDto(index.profile.values) },
    review = reviews.issue(this)?.toDto(),
)

fun RecordTable.toDto() = RecordTableDto(table, records.map { RecordDto(it.id, it.label, it.fields.map { (name, value) -> FieldDto(name, value) }) })

fun ValueReview.toDto() = ValueReviewDto(
    state = when (verdict) {
        ValueVerdict.CONFIRMED -> ValueReviewStateDto.CONFIRMED
        ValueVerdict.FIXED -> ValueReviewStateDto.FIXED
        ValueVerdict.REJECTED -> ValueReviewStateDto.REJECTED
    },
    value = value,
)

fun IssueReview.toDto() = IssueReviewDto(
    state = when (verdict) {
        IssueVerdict.CONFIRMED -> IssueReviewStateDto.CONFIRMED
        IssueVerdict.DISMISSED -> IssueReviewStateDto.DISMISSED
    },
    reason = reason,
)

fun ValueReviewStateDto.toVerdict(): ValueVerdict = when (this) {
    ValueReviewStateDto.CONFIRMED -> ValueVerdict.CONFIRMED
    ValueReviewStateDto.FIXED -> ValueVerdict.FIXED
    ValueReviewStateDto.REJECTED -> ValueVerdict.REJECTED
}

fun IssueReviewStateDto.toVerdict(): IssueVerdict = when (this) {
    IssueReviewStateDto.CONFIRMED -> IssueVerdict.CONFIRMED
    IssueReviewStateDto.DISMISSED -> IssueVerdict.DISMISSED
}

fun ValueKindDto.toKind(): ValueKind = ValueKind.valueOf(name)

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

fun QuoteQuality.toDto(spec: ValuesSpec) = QuoteQualityDto(
    values = values,
    found = found,
    textValues = textValues,
    textFound = textFound,
    withoutPageText = withoutPageText,
    byVariant = byVariant.map { it.toDto() },
    missing = missing.map { it.toDto(spec) },
)

fun Normalization.toDto(spec: ValuesSpec) = NormalizationDto(
    values = values,
    converted = converted,
    conversions = conversions.map { conversion ->
        ConversionDto(
            from = conversion.from,
            to = conversion.to,
            count = conversion.count,
            publicationId = conversion.example.publicationId,
            example = conversion.example.toRefDto(spec),
            exampleRaw = written(conversion.example, spec),
        )
    },
    metrics = metrics.map { MetricUnitsDto(it.metric, it.unit, it.values, it.inUnit) },
    vocabularyChecked = vocabularyChecked,
    vocabularyValid = vocabularyValid,
    aliases = aliases.map { (written, canonical, count) -> AliasDto(written, canonical, count) },
)

fun ChecksQuality.toDto(issueDto: (Record) -> IssueDto, goldIssueDto: (Record) -> IssueDto) = ChecksQualityDto(
    issues = issues,
    confirmed = confirmed,
    dismissed = dismissed,
    byCheck = byCheck.map { CheckCountDto(it.check, it.found, it.gold, it.matched) },
    gold = gold?.let { gold ->
        GoldIssuesDto(
            matched = gold.matched,
            goldTotal = gold.goldTotal,
            onlyOurs = gold.onlyOurs.map(issueDto),
            missed = gold.missed.map(goldIssueDto),
        )
    },
)

fun written(row: Record, spec: ValuesSpec): String {
    val raw = row[spec.rawValue].orEmpty()
    val unit = row[spec.rawUnit]?.takeUnless { raw.endsWith(it) }
    return listOfNotNull(raw.ifEmpty { null }, unit).joinToString(" ")
}

fun Chart.toDto() = ChartDto(
    kind = when (kind) {
        ChartKind.BARS -> ChartKindDto.BARS
        ChartKind.SCATTER -> ChartKindDto.SCATTER
    },
    title = title,
    hint = hint,
    xLabel = xLabel,
    yLabel = yLabel,
    unit = unit,
    bars = bars.map { it.toDto() },
    points = points.map { it.toDto() },
    lines = lines.map { it.toDto() },
)

fun Bin.toDto() = BinDto(from, to, count)

fun Tally.toDto() = CountDto(name, count)

private fun Bar.toDto() = BarDto(label, value, text, publicationId, valueId, flagged, detail)

private fun Point.toDto() = PointDto(x, y, label, publicationId, valueId, flagged, detail)

private fun ChartLine.toDto() = LineDto(slope, offset, title, limit)

private fun ValueKind.toDto(): ValueKindDto = ValueKindDto.valueOf(name)

private fun QuoteCheck.toDto() = QuoteDto(
    status = when (status) {
        QuoteStatus.FOUND -> QuoteStatusDto.FOUND
        QuoteStatus.ROW_LABEL -> QuoteStatusDto.ROW_LABEL
        QuoteStatus.ELSEWHERE -> QuoteStatusDto.ELSEWHERE
        QuoteStatus.MISSING -> QuoteStatusDto.MISSING
        QuoteStatus.NO_PAGE_TEXT -> QuoteStatusDto.NO_PAGE_TEXT
        QuoteStatus.NO_QUOTE -> QuoteStatusDto.NO_QUOTE
    },
    variant = variant,
    pages = pages,
)

private fun LinkedRecord.toDto() = LinkDto(
    table = table,
    id = id,
    label = label,
    concise = concise,
    fields = fields.map { FieldDto(it.first, it.second) },
)

private fun Counts.toDto() = CountsDto(
    truePositive = truePositive,
    falsePositive = falsePositive,
    falseNegative = falseNegative,
    precision = precision,
    recall = recall,
    f1 = f1,
)
