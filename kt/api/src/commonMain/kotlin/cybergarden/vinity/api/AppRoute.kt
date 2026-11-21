package cybergarden.vinity.api

enum class AppScreen(val slug: String) {
    ARTICLES(""),
    ARTICLE("a"),
    VALUES("values"),
    OVERVIEW("overview"),
    QUALITY("quality"),
    AREAS("areas"),
    ASK("ask"),
}

data class AppRoute(
    val screen: AppScreen,
    val ref: SetRef? = null,
    val source: SourceDto? = null,
    val article: String? = null,
    val value: String? = null,
    val issue: String? = null,
    val tab: String? = null,
) {
    fun encode(): String {
        if (screen == AppScreen.AREAS || ref == null) return "/${AppScreen.AREAS.slug}"
        val base = "/${ref.profile}/${ref.set}" + (source?.let { "$SOURCE_MARK${it.name.lowercase()}" } ?: "")
        return base + when (screen) {
            AppScreen.ARTICLES -> ""
            AppScreen.ARTICLE -> "/${AppScreen.ARTICLE.slug}/$article" + when {
                value != null -> "/$VALUE_MARK/$value"
                issue != null -> "/$ISSUE_MARK/$issue"
                tab != null -> "/$TAB_MARK/$tab"
                else -> ""
            }
            AppScreen.QUALITY -> "/${screen.slug}" + (tab?.let { "/$it" } ?: "")
            else -> "/${screen.slug}"
        }
    }

    companion object {
        private const val SOURCE_MARK = "@"
        private const val VALUE_MARK = "v"
        private const val ISSUE_MARK = "i"
        private const val TAB_MARK = "t"

        fun parse(hash: String): AppRoute? {
            val parts = hash.removePrefix("#").trim('/').split('/').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            if (parts == listOf(AppScreen.AREAS.slug)) return AppRoute(AppScreen.AREAS)
            if (parts.size < 2) return null
            val set = parts[1].substringBefore(SOURCE_MARK)
            val source = parts[1].substringAfter(SOURCE_MARK, "").takeIf { it.isNotEmpty() }
                ?.let { name -> SourceDto.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
            val route = AppRoute(AppScreen.ARTICLES, SetRef(parts[0], set), source)
            val rest = parts.drop(2)
            if (rest.isEmpty()) return route
            return when (rest[0]) {
                AppScreen.ARTICLE.slug -> rest.getOrNull(1)?.let { article ->
                    val mark = rest.getOrNull(2)
                    val target = rest.getOrNull(3)
                    route.copy(
                        screen = AppScreen.ARTICLE,
                        article = article,
                        value = target?.takeIf { mark == VALUE_MARK },
                        issue = target?.takeIf { mark == ISSUE_MARK },
                        tab = target?.takeIf { mark == TAB_MARK },
                    )
                }
                AppScreen.VALUES.slug -> route.copy(screen = AppScreen.VALUES)
                AppScreen.OVERVIEW.slug -> route.copy(screen = AppScreen.OVERVIEW)
                AppScreen.ASK.slug -> route.copy(screen = AppScreen.ASK)
                AppScreen.QUALITY.slug -> route.copy(screen = AppScreen.QUALITY, tab = rest.getOrNull(1))
                else -> null
            }
        }
    }
}
