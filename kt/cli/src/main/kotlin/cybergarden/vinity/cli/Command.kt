package cybergarden.vinity.cli

sealed interface Command {
    data class Score(
        val candidate: String,
        val gold: String,
        val strict: Boolean,
        val byPublication: Boolean,
    ) : Command

    data class Validate(val submission: String, val pages: String?) : Command

    companion object {
        fun parse(args: Array<String>): Command? {
            val name = args.firstOrNull() ?: return null
            val flags = args.drop(1).filter { it.startsWith("--") }.toSet()
            val positional = positional(args.drop(1))
            return when (name) {
                "score" -> if (positional.size == 2) {
                    Score(positional[0], positional[1], "--strict" in flags, "--by-publication" in flags)
                } else null
                "validate" -> if (positional.size == 1) {
                    Validate(positional[0], option(args, "--pages"))
                } else null
                else -> null
            }
        }

        private fun positional(args: List<String>): List<String> {
            val valued = setOf("--pages")
            val result = mutableListOf<String>()
            var skipNext = false
            args.forEach { arg ->
                when {
                    skipNext -> skipNext = false
                    arg in valued -> skipNext = true
                    arg.startsWith("--") -> Unit
                    else -> result += arg
                }
            }
            return result
        }

        private fun option(args: Array<String>, name: String): String? =
            args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    }
}
