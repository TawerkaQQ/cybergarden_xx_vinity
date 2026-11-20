package cybergarden.vinity.api

import kotlinx.serialization.Serializable

@Serializable
data class DatasetRef(val profile: String, val set: String) {
    val path: String get() = "$profile/$set"
}

@Serializable
data class DatasetDto(
    val ref: DatasetRef,
    val title: String,
    val rowCounts: Map<String, Int>,
    val publications: List<PublicationDto>,
    val values: List<ValueDto>,
    val issues: List<IssueDto>,
)

@Serializable
data class PublicationDto(
    val id: String,
    val title: String?,
    val year: String?,
    val doi: String?,
    val inScope: Boolean?,
    val exclusionReason: String?,
)

@Serializable
data class ValueDto(
    val id: String,
    val publicationId: String,
    val metric: String?,
    val rawValue: String?,
    val value: Double?,
    val unit: String?,
    val aggregation: String?,
    val origin: String?,
    val cited: String?,
    val page: Int?,
    val table: String?,
    val figure: String?,
    val fragment: String?,
    val status: String?,
    val duplicateOf: String?,
    val note: String?,
    val links: Map<String, String>,
)

@Serializable
data class IssueDto(
    val id: String,
    val publicationId: String,
    val recordIds: List<String>,
    val checkName: String?,
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
    val matchedBy: List<String>,
    val publicationsScored: Int,
    val publicationsInGold: Int,
    val scopeAgreed: Int,
    val scopeTotal: Int,
    val allMentions: CountsDto,
    val primaryRecords: CountsDto,
    val byMetric: Map<String, CountsDto>,
)
