package cybergarden.vinity.api

import kotlinx.serialization.Serializable

@Serializable
enum class SourceDto { RESULTS, GOLD }

@Serializable
data class SetRef(val profile: String, val set: String) {
    val path: String get() = "$profile/$set"
}

@Serializable
data class ProfileDto(
    val name: String,
    val title: String,
    val scope: String,
    val recordTitle: String?,
    val recordsTab: String?,
    val groupByRecord: String?,
    val tables: List<TableInfoDto>,
    val metrics: List<MetricInfoDto>,
    val checks: List<CheckInfoDto>,
    val aggregations: Map<String, String>,
)

@Serializable
data class TableInfoDto(val name: String, val columns: Int)

@Serializable
data class MetricInfoDto(val code: String, val title: String?, val unit: String?, val explain: String?)

@Serializable
data class CheckInfoDto(val name: String, val title: String?, val rule: String?, val enabled: Boolean)

@Serializable
data class SetSummaryDto(
    val ref: SetRef,
    val uploads: Boolean,
    val profileTitle: String,
    val articleCount: Int,
    val sources: List<SourceDto>,
)

@Serializable
data class SetDto(
    val ref: SetRef,
    val uploads: Boolean,
    val profile: ProfileDto,
    val sources: List<SourceDto>,
    val source: SourceDto?,
    val canCompare: Boolean,
    val pipeline: PipelineDto,
    val articles: List<ArticleSummaryDto>,
)

@Serializable
data class ArticleSummaryDto(
    val id: String,
    val title: String?,
    val year: String?,
    val authors: String?,
    val doi: String?,
    val processed: Boolean,
    val inScope: Boolean?,
    val exclusionReason: String?,
    val pageCount: Int?,
    val scan: Boolean,
    val valueCount: Int,
    val issueCount: Int,
    val reviewed: Int,
    val processing: ProcessingDto? = null,
)

@Serializable
data class ArticleDto(
    val id: String,
    val title: String?,
    val authors: String?,
    val year: String?,
    val doi: String?,
    val processed: Boolean,
    val inScope: Boolean?,
    val exclusionReason: String?,
    val pageCount: Int?,
    val pagesWithoutText: List<Int>,
    val textVariants: List<String>,
    val values: List<ValueDto>,
    val issues: List<IssueDto>,
    val records: List<RecordTableDto>,
    val processing: ProcessingDto? = null,
    val overlaps: List<OverlapDto> = emptyList(),
)

@Serializable
data class OverlapDto(val publicationId: String, val values: Int)

@Serializable
data class RepeatDto(val publicationId: String, val valueId: String, val value: String?, val unit: String?, val page: Int?, val fragment: String?)

@Serializable
enum class ValueKindDto { TABLE, FIGURE, TEXT }

@Serializable
enum class QuoteStatusDto { FOUND, ROW_LABEL, ELSEWHERE, MISSING, NO_PAGE_TEXT, NO_QUOTE }

@Serializable
data class QuoteDto(val status: QuoteStatusDto, val variant: String?, val pages: List<Int>)

@Serializable
enum class ValueReviewStateDto { CONFIRMED, FIXED, REJECTED }

@Serializable
data class ValueReviewDto(val state: ValueReviewStateDto, val value: String?)

@Serializable
enum class IssueReviewStateDto { CONFIRMED, DISMISSED }

@Serializable
data class IssueReviewDto(val state: IssueReviewStateDto, val reason: String?)

@Serializable
data class ValueDto(
    val id: String,
    val publicationId: String,
    val metric: String?,
    val value: String?,
    val unit: String?,
    val rawValue: String?,
    val rawUnit: String?,
    val aggregation: String?,
    val origin: String?,
    val cited: String?,
    val status: String?,
    val page: Int?,
    val section: String?,
    val table: String?,
    val figure: String?,
    val kind: ValueKindDto,
    val fragment: String?,
    val duplicateOf: String?,
    val note: String?,
    val record: String?,
    val recordLabel: String?,
    val links: List<LinkDto>,
    val issues: List<String>,
    val quote: QuoteDto,
    val review: ValueReviewDto?,
    val repeats: List<RepeatDto> = emptyList(),
)

@Serializable
data class LinkDto(
    val table: String,
    val id: String,
    val label: String,
    val concise: Boolean,
    val fields: List<FieldDto>,
)

@Serializable
data class FieldDto(val name: String, val value: String)

@Serializable
data class RecordTableDto(val table: String, val records: List<RecordDto>)

@Serializable
data class RecordDto(val id: String, val label: String, val fields: List<FieldDto>)

@Serializable
data class IssueDto(
    val id: String,
    val publicationId: String,
    val check: String?,
    val description: String?,
    val page: Int?,
    val fragment: String?,
    val values: List<ValueRefDto>,
    val review: IssueReviewDto?,
)

@Serializable
data class ValueRefDto(
    val id: String,
    val metric: String?,
    val value: String?,
    val unit: String?,
    val page: Int?,
    val fragment: String?,
)

@Serializable
data class ComparisonDto(
    val strict: Boolean,
    val extra: List<String>,
    val missed: List<ValueDto>,
    val matchedIssues: List<String>,
    val missedIssues: List<IssueDto>,
)

@Serializable
enum class ValueSortDto { ARTICLE, METRIC, VALUE, RAW, RECORD, AGGREGATION, PLACE, STATUS }

@Serializable
enum class ReviewFilterDto { UNCHECKED, CHECKED, NO_QUOTE }

@Serializable
data class ValueTableDto(
    val total: Int,
    val setTotal: Int,
    val offset: Int,
    val values: List<ValueDto>,
    val metrics: List<CountDto>,
    val histogram: HistogramDto?,
)

@Serializable
data class HistogramDto(val metric: String, val unit: String?, val bins: List<BinDto>)

@Serializable
data class BinDto(val from: Double, val to: Double, val count: Int)

@Serializable
data class CountDto(val name: String, val count: Int)

@Serializable
data class ValueReviewRequestDto(
    val publicationId: String,
    val valueId: String,
    val state: ValueReviewStateDto?,
    val value: String?,
)

@Serializable
data class IssueReviewRequestDto(
    val publicationId: String,
    val issueId: String,
    val state: IssueReviewStateDto?,
    val reason: String?,
)

@Serializable
data class UploadDto(
    val ref: SetRef,
    val added: List<String>,
    val rejected: List<RejectedFileDto>,
    val queued: List<String>,
)

@Serializable
data class PipelineDto(val available: Boolean, val model: String?, val problem: String?)

@Serializable
enum class ProcessingStateDto { QUEUED, RUNNING, DONE, FAILED }

@Serializable
data class ProcessingDto(val state: ProcessingStateDto, val step: String?, val problem: String?, val seconds: Int?) {
    val active: Boolean get() = state == ProcessingStateDto.QUEUED || state == ProcessingStateDto.RUNNING
}

@Serializable
data class RejectedFileDto(val file: String, val reason: String)

@Serializable
data class CountsDto(
    val truePositive: Int,
    val falsePositive: Int,
    val falseNegative: Int,
    val precision: Double,
    val recall: Double,
    val f1: Double,
)

@Serializable
data class ScoreDto(
    val strict: Boolean,
    val matchedBy: List<String>,
    val publicationsScored: Int,
    val publicationsInGold: Int,
    val scopeAgreed: Int,
    val scopeTotal: Int,
    val allMentions: CountsDto,
    val primaryRecords: CountsDto,
    val byMetric: Map<String, CountsDto>,
)

@Serializable
data class QualityDto(
    val quotes: QuoteQualityDto,
    val normalization: NormalizationDto,
    val checks: ChecksQualityDto,
)

@Serializable
data class QuoteQualityDto(
    val values: Int,
    val found: Int,
    val textValues: Int,
    val textFound: Int,
    val withoutPageText: Int,
    val byVariant: List<CountDto>,
    val missing: List<ValueDto>,
)

@Serializable
data class NormalizationDto(
    val values: Int,
    val converted: Int,
    val conversions: List<ConversionDto>,
    val metrics: List<MetricUnitsDto>,
    val vocabularyChecked: Int,
    val vocabularyValid: Int,
    val aliases: List<AliasDto>,
)

@Serializable
data class ConversionDto(
    val from: String,
    val to: String,
    val count: Int,
    val publicationId: String,
    val example: ValueRefDto,
    val exampleRaw: String,
)

@Serializable
data class MetricUnitsDto(val metric: String, val unit: String?, val values: Int, val inUnit: Int)

@Serializable
data class AliasDto(val written: String, val canonical: String, val count: Int)

@Serializable
data class ChecksQualityDto(
    val issues: Int,
    val confirmed: Int,
    val dismissed: Int,
    val byCheck: List<CheckCountDto>,
    val gold: GoldIssuesDto?,
)

@Serializable
data class CheckCountDto(val check: String, val found: Int, val gold: Int?, val matched: Int?)

@Serializable
data class GoldIssuesDto(val matched: Int, val goldTotal: Int, val onlyOurs: List<IssueDto>, val missed: List<IssueDto>)

@Serializable
data class OverviewDto(val charts: List<ChartDto>)

@Serializable
enum class ChartKindDto { BARS, SCATTER }

@Serializable
data class ChartDto(
    val kind: ChartKindDto,
    val title: String,
    val hint: String?,
    val xLabel: String?,
    val yLabel: String?,
    val unit: String?,
    val bars: List<BarDto>,
    val points: List<PointDto>,
    val lines: List<LineDto>,
)

@Serializable
data class BarDto(
    val label: String,
    val value: Double,
    val text: String,
    val publicationId: String?,
    val valueId: String?,
    val flagged: Boolean,
    val detail: String?,
)

@Serializable
data class PointDto(
    val x: Double,
    val y: Double,
    val label: String,
    val publicationId: String,
    val valueId: String,
    val flagged: Boolean,
    val detail: String?,
)

@Serializable
data class LineDto(val slope: Double, val offset: Double, val title: String, val limit: Boolean)

@Serializable
data class SystemDto(
    val articles: Int,
    val pages: Int,
    val scans: Int,
    val values: Int,
    val issues: Int,
    val sets: Int,
    val profiles: Int,
)

@Serializable
data class DemoCaseDto(val title: String, val detail: String, val route: String)

@Serializable
data class ProfileUploadDto(val profile: ProfileDto?, val problem: String?)

@Serializable
data class PassportDto(
    val ref: SetRef,
    val profileTitle: String,
    val scope: String,
    val source: SourceDto?,
    val generatedAt: String,
    val sections: List<PassportSectionDto>,
    val accuracy: List<AccuracyDto>,
    val byCheck: List<CountDto>,
    val overlaps: List<OverlapPairDto>,
    val articles: List<ArticleFileDto>,
    val outputs: List<OutputFileDto>,
    val reproduce: List<CommandDto>,
)

@Serializable
data class PassportSectionDto(val title: String, val facts: List<FactDto>)

@Serializable
data class FactDto(val label: String, val value: String, val hint: String? = null)

@Serializable
data class AccuracyDto(val strict: Boolean, val matchedBy: List<String>, val primaryRecords: CountsDto, val allMentions: CountsDto, val publicationsScored: Int)

@Serializable
data class OverlapPairDto(val first: String, val second: String, val values: Int)

@Serializable
data class ArticleFileDto(val id: String, val bytes: Long, val sha256: String, val promptVersion: String?, val models: List<String>)

@Serializable
data class OutputFileDto(val name: String, val bytes: Long, val sha256: String, val modified: String, val rows: Int?)

@Serializable
data class CommandDto(val title: String, val command: String)

@Serializable
data class AskRequestDto(val question: String)

@Serializable
data class PageRequestDto(val fragments: List<String>)

@Serializable
data class AnswerDto(
    val question: String,
    val answer: String?,
    val engine: String,
    val sources: List<AnswerSourceDto>,
    val problem: String?,
)

@Serializable
data class AnswerSourceDto(
    val number: Int,
    val publicationId: String?,
    val page: Int?,
    val text: String,
    val reference: String?,
    val url: String?,
    val valueId: String?,
    val cited: Boolean,
)
