package cybergarden.vinity.api

object ApiRoutes {
    const val PROFILE_PARAMETER = "profile"
    const val SET_PARAMETER = "set"
    const val ARTICLE_PARAMETER = "article"
    const val PAGE_PARAMETER = "page"
    const val SOURCE_PARAMETER = "source"
    const val FRAGMENT_PARAMETER = "fragment"
    const val STRICT_PARAMETER = "strict"
    const val METRIC_PARAMETER = "metric"
    const val KIND_PARAMETER = "kind"
    const val REVIEW_PARAMETER = "review"
    const val ISSUES_PARAMETER = "issues"
    const val HIDE_DUPLICATES_PARAMETER = "hideDuplicates"
    const val MIN_PARAMETER = "min"
    const val MAX_PARAMETER = "max"
    const val SORT_PARAMETER = "sort"
    const val DESCENDING_PARAMETER = "desc"
    const val QUERY_PARAMETER = "query"
    const val DOWNLOAD_PARAMETER = "download"
    const val OFFSET_PARAMETER = "offset"
    const val LIMIT_PARAMETER = "limit"
    const val FILE_PART = "file"
    const val HIGHLIGHT_HEADER = "X-Highlight"

    const val PROFILES = "/api/profiles"
    const val UPLOADS = "$PROFILES/{$PROFILE_PARAMETER}/uploads"
    const val SYSTEM = "/api/system"
    const val DEMO = "/api/demo"
    const val SETS = "/api/sets"
    const val SET = "$SETS/{$PROFILE_PARAMETER}/{$SET_PARAMETER}"
    const val SCORE = "$SET/score"
    const val QUALITY = "$SET/quality"
    const val OVERVIEW = "$SET/overview"
    const val EXPORT = "$SET/export"
    const val PASSPORT = "$SET/passport"
    const val ASK = "$SET/ask"
    const val SET_VALUES = "$SET/values"
    const val REVIEW_VALUES = "$SET/review/values"
    const val REVIEW_ISSUES = "$SET/review/issues"
    const val ARTICLE = "$SET/articles/{$ARTICLE_PARAMETER}"
    const val COMPARISON = "$ARTICLE/comparison"
    const val PAGE = "$ARTICLE/pages/{$PAGE_PARAMETER}"
    const val PROCESS = "$ARTICLE/process"

    fun uploads(profile: String) = "$PROFILES/$profile/uploads"
    fun set(ref: SetRef) = "$SETS/${ref.path}"
    fun score(ref: SetRef) = "${set(ref)}/score"
    fun quality(ref: SetRef) = "${set(ref)}/quality"
    fun overview(ref: SetRef) = "${set(ref)}/overview"
    fun export(ref: SetRef) = "${set(ref)}/export"
    fun passport(ref: SetRef) = "${set(ref)}/passport"
    fun ask(ref: SetRef) = "${set(ref)}/ask"
    fun setValues(ref: SetRef) = "${set(ref)}/values"
    fun reviewValues(ref: SetRef) = "${set(ref)}/review/values"
    fun reviewIssues(ref: SetRef) = "${set(ref)}/review/issues"
    fun article(ref: SetRef, id: String) = "${set(ref)}/articles/$id"
    fun comparison(ref: SetRef, id: String) = "${article(ref, id)}/comparison"
    fun page(ref: SetRef, id: String, page: Int) = "${article(ref, id)}/pages/$page"
    fun process(ref: SetRef, id: String) = "${article(ref, id)}/process"
}

data class HighlightBox(
    val index: Int,
    val top: Double,
    val bottom: Double,
    val left: Double,
    val right: Double,
    val rowLabel: Boolean,
) {
    companion object {
        fun encode(boxes: List<HighlightBox>): String = boxes.joinToString("|") {
            listOf(it.index, it.top, it.bottom, it.left, it.right, if (it.rowLabel) 1 else 0).joinToString(";")
        }

        fun decode(header: String?): List<HighlightBox> = header.orEmpty().split('|').mapNotNull { part ->
            val cells = part.split(';')
            if (cells.size != 6) return@mapNotNull null
            val numbers = cells.map { it.toDoubleOrNull() ?: return@mapNotNull null }
            HighlightBox(numbers[0].toInt(), numbers[1], numbers[2], numbers[3], numbers[4], numbers[5] > 0)
        }
    }
}
