package cybergarden.vinity.data.pdf

import cybergarden.vinity.data.Workspace
import org.apache.pdfbox.Loader
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

class UploadedFile(val name: String, val content: ByteArray)

data class UploadOutcome(val added: List<String>, val rejected: List<Pair<String, String>>)

class PdfUploads(private val workspace: Workspace) {

    @Synchronized
    fun save(profile: String, files: List<UploadedFile>): UploadOutcome {
        val added = mutableListOf<String>()
        val rejected = mutableListOf<Pair<String, String>>()
        files.forEach { file ->
            val problem = problem(file)
            if (problem != null) {
                rejected += file.name to problem
                return@forEach
            }
            val id = freeId(profile, baseId(file.name))
            val target = workspace.pdf(profile, Workspace.UPLOADS, id)
            target.parent.createDirectories()
            target.writeBytes(file.content)
            added += id
        }
        return UploadOutcome(added, rejected)
    }

    private fun problem(file: UploadedFile): String? = when {
        file.content.isEmpty() -> "пустой файл"
        !file.content.startsWith(PDF_SIGNATURE) -> "не PDF"
        !readable(file.content) -> "PDF повреждён или зашифрован"
        else -> null
    }

    private fun readable(content: ByteArray): Boolean =
        runCatching { Loader.loadPDF(content).use { it.numberOfPages > 0 } }.getOrDefault(false)

    private fun baseId(fileName: String): String {
        val stem = fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        val safe = stem.replace(UNSAFE, "_").trim('_', '-').take(MAX_ID)
        return safe.takeIf { it.isNotEmpty() && it.first().isLetterOrDigit() } ?: FALLBACK_ID
    }

    private fun freeId(profile: String, base: String): String =
        generateSequence(1) { it + 1 }
            .map { if (it == 1) base else "$base-$it" }
            .first { id -> (workspace.sets(profile) + Workspace.UPLOADS).none { workspace.pdf(profile, it, id).exists() } }

    private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private companion object {
        val PDF_SIGNATURE = "%PDF".toByteArray()
        val UNSAFE = Regex("[^A-Za-z0-9_-]+")
        const val MAX_ID = 60
        const val FALLBACK_ID = "article"
    }
}
