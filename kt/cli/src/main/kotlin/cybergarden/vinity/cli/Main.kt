package cybergarden.vinity.cli

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val command = Command.parse(args) ?: run {
        System.err.println(USAGE)
        exitProcess(2)
    }
    val exitCode = when (command) {
        is Command.Score -> ScoreCommand(command).run()
        is Command.Validate -> ValidateCommand(command).run()
    }
    exitProcess(exitCode)
}

private val USAGE = """
    vinity score <папка-сдачи> <папка-эталона> [--strict] [--by-publication]
    vinity validate <папка-сдачи> [--pages <папка work/pages>]
""".trimIndent()
