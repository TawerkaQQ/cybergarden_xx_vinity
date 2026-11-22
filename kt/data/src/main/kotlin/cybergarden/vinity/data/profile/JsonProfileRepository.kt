package cybergarden.vinity.data.profile

import cybergarden.vinity.domain.model.ChartLine
import cybergarden.vinity.domain.model.ChartSpec
import cybergarden.vinity.domain.model.CheckDisplay
import cybergarden.vinity.domain.model.CheckRule
import cybergarden.vinity.domain.model.DisplaySpec
import cybergarden.vinity.domain.model.Issues
import cybergarden.vinity.domain.model.LinkedField
import cybergarden.vinity.domain.model.MatchMode
import cybergarden.vinity.domain.model.MetricDisplay
import cybergarden.vinity.domain.model.Profile
import cybergarden.vinity.domain.model.RecordDisplay
import cybergarden.vinity.domain.model.SourcesSpec
import cybergarden.vinity.domain.model.TableFragment
import cybergarden.vinity.domain.model.TableSpec
import cybergarden.vinity.domain.model.UnitConversion
import cybergarden.vinity.domain.model.UnitsSpec
import cybergarden.vinity.domain.model.ValuesSpec
import cybergarden.vinity.domain.port.ProfileRepository
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class JsonProfileRepository(private val directory: Path) : ProfileRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, Profile>()

    fun names(): List<String> =
        directory.listDirectoryEntries().filter { it.resolve(FILE).exists() }.map { it.name }.sorted()

    override fun load(name: String): Profile = cache.computeIfAbsent(name) {
        val folder = directory.resolve(name)
        require(folder.isDirectory() && folder.resolve(FILE).exists()) { "Нет профиля $name в $directory" }
        parse(folder.resolve(FILE).readText(Charsets.UTF_8))
    }

    fun parse(text: String): Profile = try {
        json.decodeFromString<ProfileDocument>(text.removePrefix(BOM)).toProfile()
    } catch (invalid: SerializationException) {
        throw IllegalArgumentException("profile.json не читается: ${invalid.message}", invalid)
    }

    @Synchronized
    fun install(profileText: String, rules: String?): Profile {
        val profile = parse(profileText)
        require(NAME.matches(profile.name)) { "Имя профиля «${profile.name}»: только строчные латинские буквы, цифры, - и _" }
        val folder = directory.resolve(profile.name)
        require(!folder.exists()) { "Профиль ${profile.name} уже есть" }
        folder.createDirectories()
        folder.resolve(FILE).writeText(profileText, Charsets.UTF_8)
        rules?.let { folder.resolve(RULES).writeText(it, Charsets.UTF_8) }
        cache[profile.name] = profile
        return profile
    }

    private fun ProfileDocument.toProfile() = Profile(
        name = name,
        title = title,
        scope = scope,
        tables = tables.map { it.toSpec() },
        values = values.toSpec(),
        issues = Issues.table(issues.table, issues.file, values.table),
        sources = SourcesSpec(TableFragment.valueOf(sources.tableFragment.uppercase()), sources.textVariants),
        units = UnitsSpec(units.aliases, units.conversions.map { UnitConversion(it.from, it.to, it.factor) }),
        ranges = ranges.mapValues { (metric, bounds) ->
            require(bounds.size == 2 && bounds[0] <= bounds[1]) { "Профиль $name: диапазон $metric должен быть [от, до]" }
            bounds[0]..bounds[1]
        },
        checks = checks.map { checkName(name, it) },
        display = display?.toSpec(name) ?: DisplaySpec(),
        rules = checks.map { checkRule(name, it) },
    )

    private fun checkRule(profile: String, check: JsonElement): CheckRule {
        val name = checkName(profile, check)
        if (check !is JsonObject) return CheckRule(name, name, emptyMap())
        val rule = check["rule"]?.jsonPrimitive?.content ?: name
        val params = check.filterKeys { it != "rule" && it != "name" }.mapNotNull { (key, value) ->
            when (value) {
                is JsonPrimitive -> key to listOf(value.content)
                is JsonArray -> value.filterIsInstance<JsonPrimitive>().map { it.content }.takeIf { it.size == value.size }?.let { key to it }
                else -> null
            }
        }.toMap()
        return CheckRule(name, rule, params)
    }

    private fun checkName(profile: String, check: JsonElement): String = when (check) {
        is JsonPrimitive -> check.content
        is JsonObject -> (check["name"] ?: check["rule"])?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Профиль $profile: у проверки нет rule")
        else -> throw IllegalArgumentException("Профиль $profile: проверка должна быть строкой или объектом с rule")
    }

    private fun TableDocument.toSpec() = TableSpec(
        name = name,
        file = file,
        id = id,
        columns = columns,
        references = references,
        lists = lists.toSet(),
        vocabularies = vocabularies.mapValues { it.value.toSet() },
    )

    private fun ValuesDocument.toSpec() = ValuesSpec(
        table = table,
        roles = columns,
        mirrors = mirrors,
        metrics = metrics,
        duplicateKey = duplicateKey,
        match = match.mapKeys { MatchMode.valueOf(it.key.uppercase()) },
    )

    private fun DisplayDocument.toSpec(profile: String) = DisplaySpec(
        record = record?.let { RecordDisplay(it.column, it.title, it.group) },
        recordsTab = recordsTab,
        metrics = metrics.mapValues { MetricDisplay(it.value.title, it.value.explain) },
        checks = checks.mapValues { CheckDisplay(it.value.title, it.value.rule) },
        aggregations = aggregations,
        overview = overview.map { it.toSpec(profile) },
    )

    private fun ChartDocument.toSpec(profile: String): ChartSpec {
        fun missing(field: String): Nothing = throw IllegalArgumentException("Профиль $profile: у графика «$title» нет $field")
        return when (kind) {
            "scatter" -> ChartSpec.Scatter(
                title = title,
                hint = hint,
                y = y ?: missing("y"),
                x = x?.let { LinkedField(it.column, it.field) } ?: missing("x"),
                xLabel = xLabel,
                yLabel = yLabel,
                lines = lines.map { ChartLine(it.slope, it.offset, it.title, it.limit) },
            )
            "best" -> ChartSpec.Best(title, hint, metric ?: missing("metric"), aggregation)
            "leaders" -> ChartSpec.Leaders(title, hint, group ?: missing("group"), metrics.ifEmpty { missing("metrics") })
            "counts" -> ChartSpec.Counts(title, hint)
            else -> throw IllegalArgumentException("Профиль $profile: неизвестный вид графика «$kind»")
        }
    }

    @Serializable
    private data class ProfileDocument(
        val name: String,
        val title: String,
        val scope: String = "",
        val tables: List<TableDocument>,
        val values: ValuesDocument,
        val issues: IssuesDocument,
        val sources: SourcesDocument,
        val units: UnitsDocument = UnitsDocument(),
        val ranges: Map<String, List<Double>> = emptyMap(),
        val checks: List<JsonElement> = emptyList(),
        val display: DisplayDocument? = null,
    )

    @Serializable
    private data class TableDocument(
        val name: String,
        val file: String,
        val id: String,
        val columns: List<String>,
        val references: Map<String, String> = emptyMap(),
        val lists: List<String> = emptyList(),
        val vocabularies: Map<String, List<String>> = emptyMap(),
    )

    @Serializable
    private data class ValuesDocument(
        val table: String,
        val columns: Map<String, String>,
        val mirrors: Map<String, String> = emptyMap(),
        val metrics: Map<String, String> = emptyMap(),
        @SerialName("duplicate_key") val duplicateKey: List<String> = emptyList(),
        val match: Map<String, List<String>>,
    )

    @Serializable
    private data class IssuesDocument(val table: String, val file: String)

    @Serializable
    private data class SourcesDocument(
        @SerialName("table_fragment") val tableFragment: String,
        @SerialName("text_variants") val textVariants: List<String>,
    )

    @Serializable
    private data class UnitsDocument(
        val aliases: Map<String, String> = emptyMap(),
        val conversions: List<ConversionDocument> = emptyList(),
    )

    @Serializable
    private data class ConversionDocument(val from: String, val to: String, val factor: Double)

    @Serializable
    private data class DisplayDocument(
        val record: RecordDocument? = null,
        @SerialName("records_tab") val recordsTab: String? = null,
        val metrics: Map<String, MetricDocument> = emptyMap(),
        val checks: Map<String, CheckDocument> = emptyMap(),
        val aggregations: Map<String, String> = emptyMap(),
        val overview: List<ChartDocument> = emptyList(),
    )

    @Serializable
    private data class RecordDocument(val column: String, val title: String? = null, val group: String? = null)

    @Serializable
    private data class MetricDocument(val title: String? = null, val explain: String? = null)

    @Serializable
    private data class CheckDocument(val title: String? = null, val rule: String? = null)

    @Serializable
    private data class ChartDocument(
        val kind: String,
        val title: String,
        val hint: String? = null,
        val metric: String? = null,
        val aggregation: String? = null,
        val y: String? = null,
        val x: LinkedFieldDocument? = null,
        @SerialName("x_label") val xLabel: String? = null,
        @SerialName("y_label") val yLabel: String? = null,
        val lines: List<LineDocument> = emptyList(),
        val group: String? = null,
        val metrics: List<String> = emptyList(),
    )

    @Serializable
    private data class LinkedFieldDocument(val column: String, val field: String)

    @Serializable
    private data class LineDocument(val slope: Double, val offset: Double = 0.0, val title: String, val limit: Boolean = false)

    private companion object {
        const val FILE = "profile.json"
        const val RULES = "rules.md"
        const val BOM = "﻿"
        val NAME = Regex("^[a-z][a-z0-9_-]*$")
    }
}
