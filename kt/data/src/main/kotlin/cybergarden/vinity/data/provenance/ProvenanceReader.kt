package cybergarden.vinity.data.provenance

import cybergarden.vinity.data.Workspace
import cybergarden.vinity.data.csv.CsvFiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

class FileFact(val name: String, val bytes: Long, val sha256: String, val modified: Instant, val rows: Int?)

class ArticleFact(val id: String, val bytes: Long, val sha256: String, val promptVersion: String?, val models: List<String>)

class Provenance(val articles: List<ArticleFact>, val outputs: List<FileFact>, val profile: FileFact?) {
    val manifest: String
        get() = sha256(articles.sortedBy { it.id }.joinToString("\n") { "${it.id} ${it.sha256}" }.toByteArray())
}

class ProvenanceReader(private val workspace: Workspace) {

    private class Hashed(val stamp: Pair<Long, Long>, val sha256: String)

    private val hashes = ConcurrentHashMap<Path, Hashed>()

    fun read(profile: String, set: String, output: Path?): Provenance {
        val articles = workspace.articleIds(profile, set).map { id ->
            val pdf = workspace.pdf(profile, set, id)
            val sha = hash(pdf)
            val cached = cached(sha, profile)
            val promptVersion = promptVersion(profile, id) ?: cached.firstOrNull()?.first
            val models = cached.filter { it.first == promptVersion }.map { it.second }.distinct()
            ArticleFact(id, pdf.fileSize(), sha, promptVersion, models)
        }
        val outputs = output?.takeIf { it.isDirectory() }?.listDirectoryEntries()?.filter { it.isRegularFile() }?.sortedBy { it.name }
            ?.map { fact(it) }.orEmpty()
        val profileFile = workspace.root.resolve(PROFILES).resolve(profile).resolve(PROFILE_FILE).takeIf { it.exists() }?.let { fact(it) }
        return Provenance(articles, outputs, profileFile)
    }

    private fun fact(file: Path): FileFact = FileFact(
        name = file.name,
        bytes = file.fileSize(),
        sha256 = hash(file),
        modified = file.getLastModifiedTime().toInstant(),
        rows = if (file.extension.equals(CSV, ignoreCase = true)) CsvFiles.read(file).rows.size else null,
    )

    private fun promptVersion(profile: String, id: String): String? {
        val file = workspace.root.resolve(WORK).resolve(profile).resolve(EXTRACT).resolve("$id.json").takeIf { it.isRegularFile() } ?: return null
        return runCatching { Json.parseToJsonElement(file.readText()).jsonObject[PROMPT_VERSION]?.jsonPrimitive?.content }.getOrNull()
    }

    private fun cached(sha: String, profile: String): List<Pair<String, String>> {
        val directory = workspace.root.resolve(MODEL_CACHE).resolve(sha).takeIf { it.isDirectory() } ?: return emptyList()
        val entry = Regex("(${Regex.escape(profile)}-[0-9]+-[0-9a-f]+)-(.+)")
        return directory.listDirectoryEntries().mapNotNull { entry.matchEntire(it.name) }.map { it.groupValues[1] to it.groupValues[2] }.sortedBy { it.second }
    }

    private fun hash(file: Path): String {
        val stamp = file.getLastModifiedTime().toMillis() to file.fileSize()
        hashes[file]?.takeIf { it.stamp == stamp }?.let { return it.sha256 }
        val digest = MessageDigest.getInstance(SHA_256)
        file.inputStream().use { stream ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val sha = digest.digest().toHex()
        hashes[file] = Hashed(stamp, sha)
        return sha
    }

    private companion object {
        const val PROFILES = "profiles"
        const val PROFILE_FILE = "profile.json"
        const val WORK = "work"
        const val EXTRACT = "extract"
        const val PROMPT_VERSION = "prompt_version"
        const val MODEL_CACHE = "pipeline/.cache"
        const val CSV = "csv"
        const val SHA_256 = "SHA-256"
        const val BUFFER = 1 shl 16
    }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
