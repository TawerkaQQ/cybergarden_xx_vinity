package cybergarden.vinity.cli

sealed interface Command {
    data class Score(
        val candidate: String,
        val gold: String,
        val profile: String?,
        val strict: Boolean,
        val byPublication: Boolean,
    ) : Command

    data class Validate(
        val submission: String,
        val profile: String?,
        val pages: String?,
        val articles: String?,
    ) : Command

    companion object {
        private val valued = setOf("--profile", "--pages", "--pdf")

        fun parse(args: Array<String>): Command? {
            val name = args.firstOrNull() ?: return null
            val arguments = Arguments.of(args.drop(1), valued) ?: return null
            val positional = arguments.positional
            return when {
                name == "score" && positional.size == 2 -> Score(
                    candidate = positional[0],
                    gold = positional[1],
                    profile = arguments.options["--profile"],
                    strict = "--strict" in arguments.flags,
                    byPublication = "--by-publication" in arguments.flags,
                )
                name == "validate" && positional.size == 1 -> Validate(
                    submission = positional[0],
                    profile = arguments.options["--profile"],
                    pages = arguments.options["--pages"],
                    articles = arguments.options["--pdf"],
                )
                else -> null
            }
        }
    }
}

private data class Arguments(
    val positional: List<String>,
    val flags: Set<String>,
    val options: Map<String, String>,
) {
    companion object {
        fun of(args: List<String>, valued: Set<String>): Arguments? {
            val positional = mutableListOf<String>()
            val flags = mutableSetOf<String>()
            val options = mutableMapOf<String, String>()
            val queue = ArrayDeque(args)
            while (queue.isNotEmpty()) {
                val arg = queue.removeFirst()
                when {
                    arg in valued -> options[arg] = queue.removeFirstOrNull() ?: return null
                    arg.startsWith("--") -> flags += arg
                    else -> positional += arg
                }
            }
            return Arguments(positional, flags, options)
        }
    }
}
