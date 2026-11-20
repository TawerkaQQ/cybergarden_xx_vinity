package cybergarden.vinity.api

import kotlinx.serialization.Serializable

@Serializable
data class DatasetDto(
    val name: String,
    val deviceCount: Int,
    val publications: List<PublicationDto>,
    val results: List<ResultDto>,
    val issues: List<IssueDto>,
)

@Serializable
data class PublicationDto(
    val id: String,
    val title: String?,
    val year: Int?,
    val doi: String?,
    val inScope: Boolean?,
    val exclusionReason: String?,
)

@Serializable
data class ResultDto(
    val id: String,
    val publicationId: String,
    val deviceId: String?,
    val measurementId: String?,
    val metric: String,
    val value: Double?,
    val unit: String?,
    val aggregation: String?,
    val spread: Double?,
    val origin: String?,
    val citedReference: String?,
    val page: Int?,
    val section: String?,
    val table: String?,
    val figure: String?,
    val fragment: String?,
    val status: String?,
    val duplicateOf: String?,
    val note: String?,
)

@Serializable
data class IssueDto(
    val id: String,
    val publicationId: String,
    val recordIds: List<String>,
    val checkName: String,
    val description: String?,
    val page: Int?,
    val fragment: String?,
)

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
    val publicationsScored: Int,
    val publicationsInGold: Int,
    val scopeAgreed: Int,
    val scopeTotal: Int,
    val allMentions: CountsDto,
    val primaryRecords: CountsDto,
    val byMetric: Map<String, CountsDto>,
)
