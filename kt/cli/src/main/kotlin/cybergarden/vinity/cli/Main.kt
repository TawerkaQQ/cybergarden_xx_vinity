package cybergarden.vinity.cli

import cybergarden.vinity.data.Workspace
import cybergarden.vinity.domain.model.Profile
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val command = Command.parse(args) ?: run {
        System.err.println(USAGE)
        exitProcess(2)
    }
    val workspace = Workspace.locate(Path.of(""))
    val exitCode = when (command) {
        is Command.Score -> ScoreCommand(command, workspace.profile(command.profile, command.candidate)).run()
        is Command.Validate -> ValidateCommand(command, workspace.profile(command.profile, command.submission)).run()
    }
    exitProcess(exitCode)
}

private fun Workspace.profile(name: String?, submission: String): Profile =
    profiles.load(name ?: Path.of(submission).toAbsolutePath().normalize().parent.name)

private val USAGE = """
    vinity score <папка-сдачи> <папка-эталона> [--profile <профиль>] [--strict] [--by-publication]
    vinity validate <папка-сдачи> [--profile <профиль>] [--pages <папка work/<профиль>/pages>] [--pdf <папка со статьями>]

    Профиль по умолчанию — имя папки над сдачей: out/<профиль>/<набор>, data/<профиль>/<набор>_gold.
""".trimIndent()
