package cybergarden.vinity.data.profile

import cybergarden.vinity.domain.model.Issues
import cybergarden.vinity.domain.model.MatchMode
import cybergarden.vinity.domain.model.Profile
import cybergarden.vinity.domain.model.SourcesSpec
import cybergarden.vinity.domain.model.TableFragment
import cybergarden.vinity.domain.model.TableSpec
import cybergarden.vinity.domain.model.ValuesSpec
import cybergarden.vinity.domain.port.ProfileRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

class JsonProfileRepository(private val directory: Path) : ProfileRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, Profile>()

    fun names(): List<String> =
        directory.listDirectoryEntries().filter { it.resolve(FILE).exists() }.map { it.name }.sorted()

    override fun load(name: String): Profile = cache.getOrPut(name) {
        val folder = directory.resolve(name)
        require(folder.isDirectory() && folder.resolve(FILE).exists()) { "Нет профиля $name в $directory" }
        json.decodeFromString<ProfileDocument>(folder.resolve(FILE).readText(Charsets.UTF_8)).toProfile()
    }

    private fun ProfileDocument.toProfile() = Profile(
        name = name,
        title = title,
        tables = tables.map { it.toSpec() },
        values = values.toSpec(),
        issues = Issues.table(issues.table, issues.file, values.table),
        sources = SourcesSpec(TableFragment.valueOf(sources.tableFragment.uppercase()), sources.textVariants),
    )

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

    @Serializable
    private data class ProfileDocument(
        val name: String,
        val title: String,
        val tables: List<TableDocument>,
        val values: ValuesDocument,
        val issues: IssuesDocument,
        val sources: SourcesDocument,
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

    private companion object {
        const val FILE = "profile.json"
    }
}
