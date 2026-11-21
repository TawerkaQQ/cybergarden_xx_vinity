package cybergarden.vinity.data

import cybergarden.vinity.data.profile.JsonProfileRepository
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

class Workspace(val root: Path) {

    val profiles = JsonProfileRepository(root.resolve(PROFILES))

    fun articles(profile: String, set: String): Path = data(profile).resolve(set)

    fun pdf(profile: String, set: String, id: String): Path = articles(profile, set).resolve("$id.$PDF")

    fun articleIds(profile: String, set: String): List<String> =
        articles(profile, set).children().filter { it.extension.equals(PDF, ignoreCase = true) }.map { it.nameWithoutExtension }

    fun gold(profile: String, set: String): Path = data(profile).resolve("$set$GOLD_SUFFIX")

    fun pages(profile: String): Path = root.resolve("work").resolve(profile).resolve("pages")

    fun output(profile: String, set: String): Path = outputs(profile).resolve(set)

    fun reviews(profile: String, set: String): Path = outputs(profile).resolve("$set$REVIEW_SUFFIX")

    fun sets(profile: String): List<String> {
        val withArticles = data(profile).children().filter { it.isDirectory() && articleIds(profile, it.name).isNotEmpty() }
        return (withArticles + outputs(profile).children().filter { it.isDirectory() }).map { it.name }.distinct().sorted()
    }

    private fun outputs(profile: String): Path = root.resolve("out").resolve(profile)

    private fun data(profile: String): Path = root.resolve("data").resolve(profile)

    private fun Path.children(): List<Path> = if (isDirectory()) listDirectoryEntries().sortedBy { it.name } else emptyList()

    companion object {
        const val UPLOADS = "uploads"
        private const val PROFILES = "profiles"
        private const val GOLD_SUFFIX = "_gold"
        private const val REVIEW_SUFFIX = ".review.json"
        private const val PDF = "pdf"

        fun locate(start: Path): Workspace {
            val absolute = start.toAbsolutePath().normalize()
            val root = generateSequence(absolute) { it.parent }.firstOrNull { it.resolve(PROFILES).isDirectory() }
            requireNotNull(root) { "Не найдена папка $PROFILES над $absolute" }
            return Workspace(root)
        }
    }
}
