package cybergarden.vinity.data

import cybergarden.vinity.data.profile.JsonProfileRepository
import java.nio.file.Path
import kotlin.io.path.isDirectory

class Workspace(val root: Path) {

    val profiles = JsonProfileRepository(root.resolve(PROFILES))

    fun articles(profile: String, set: String): Path = root.resolve("data").resolve(profile).resolve(set)

    fun gold(profile: String, set: String): Path = articles(profile, "${set}_gold")

    fun pages(profile: String): Path = root.resolve("work").resolve(profile).resolve("pages")

    fun output(): Path = root.resolve("out")

    fun output(profile: String, set: String): Path = output().resolve(profile).resolve(set)

    companion object {
        private const val PROFILES = "profiles"

        fun locate(start: Path): Workspace {
            val absolute = start.toAbsolutePath().normalize()
            val root = generateSequence(absolute) { it.parent }.firstOrNull { it.resolve(PROFILES).isDirectory() }
            requireNotNull(root) { "Не найдена папка $PROFILES над $absolute" }
            return Workspace(root)
        }
    }
}
