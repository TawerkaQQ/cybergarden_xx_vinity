package cybergarden.vinity.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cybergarden.vinity.api.AppRoute
import cybergarden.vinity.api.AppScreen
import cybergarden.vinity.api.ArticleDto
import cybergarden.vinity.api.ArticleSummaryDto
import cybergarden.vinity.api.ComparisonDto
import cybergarden.vinity.api.DemoCaseDto
import cybergarden.vinity.api.IssueDto
import cybergarden.vinity.api.IssueReviewDto
import cybergarden.vinity.api.IssueReviewRequestDto
import cybergarden.vinity.api.IssueReviewStateDto
import cybergarden.vinity.api.OverviewDto
import cybergarden.vinity.api.ProfileDto
import cybergarden.vinity.api.ProfileUploadDto
import cybergarden.vinity.api.QualityDto
import cybergarden.vinity.api.ScoreDto
import cybergarden.vinity.api.SetDto
import cybergarden.vinity.api.SetRef
import cybergarden.vinity.api.SetSummaryDto
import cybergarden.vinity.api.SourceDto
import cybergarden.vinity.api.SystemDto
import cybergarden.vinity.api.UploadDto
import cybergarden.vinity.api.ValueDto
import cybergarden.vinity.api.ValueReviewDto
import cybergarden.vinity.api.ValueReviewRequestDto
import cybergarden.vinity.api.ValueReviewStateDto
import cybergarden.vinity.api.ValueSortDto
import cybergarden.vinity.api.ValueTableDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ArticleTab(val slug: String) { VALUES("values"), ISSUES("issues"), RECORDS("records"), INFO("info") }

enum class ListFilter { ALL, ISSUES, OUT }

enum class GroupBy { RECORD, METRIC }

enum class QualityTab(val slug: String) { EXTRACT("extract"), QUOTES("quotes"), NORMALIZATION("norm"), CHECKS("checks") }

sealed interface Evidence {
    val article: String
    val id: String

    data class OnValue(override val article: String, override val id: String) : Evidence
    data class OnIssue(override val article: String, override val id: String) : Evidence
}

data class Mark(val fragment: String, val error: Boolean, val focus: Boolean)

data class ViewerTarget(val key: String, val article: String, val home: Int, val pageCount: Int?, val marks: Map<Int, List<Mark>>)

enum class UploadStage { UPLOADING, DONE, FAILED }

class UploadItem(val id: Int, val file: LocalFile, val profile: String) {
    var stage by mutableStateOf(UploadStage.UPLOADING)
    var sent by mutableLongStateOf(0L)
    var total by mutableLongStateOf(file.size.coerceAtLeast(1L))
    var result by mutableStateOf<UploadDto?>(null)
    var problem by mutableStateOf<String?>(null)
}

class AppStore(val client: VinityClient, private val scope: CoroutineScope) {

    var profiles by mutableStateOf(emptyList<ProfileDto>())
        private set
    var sets by mutableStateOf(emptyList<SetSummaryDto>())
        private set
    var set by mutableStateOf<SetDto?>(null)
        private set
    var screen by mutableStateOf(AppScreen.ARTICLES)
        private set
    var articleId by mutableStateOf<String?>(null)
        private set
    val articles = mutableStateMapOf<String, ArticleDto>()
    var articleTab by mutableStateOf(ArticleTab.VALUES)
    var listQuery by mutableStateOf("")
    var listFilter by mutableStateOf(ListFilter.ALL)
    var groupBy by mutableStateOf(GroupBy.RECORD)
    var showDuplicates by mutableStateOf(true)
    var onlyIssues by mutableStateOf(false)
    var compare by mutableStateOf(false)
        private set
    val comparisons = mutableStateMapOf<String, ComparisonDto>()
    var authorsOpen by mutableStateOf(false)

    var evidence by mutableStateOf<Evidence?>(null)
        private set
    var detailsOpen by mutableStateOf(false)
    var fixing by mutableStateOf(false)
        private set
    var dismissing by mutableStateOf(false)
        private set
    val viewerPages = mutableStateMapOf<String, Int>()
    var zoom by mutableStateOf(1f)
        private set
    var fullZoom by mutableStateOf(1.2f)
        private set
    var dim by mutableStateOf(true)
        private set
    var fullscreen by mutableStateOf<ViewerTarget?>(null)
        private set

    var valueFilter by mutableStateOf(ValueFilter())
        private set
    var valueRows by mutableStateOf(emptyList<ValueDto>())
        private set
    var valueTotal by mutableIntStateOf(0)
        private set
    var valueTable by mutableStateOf<ValueTableDto?>(null)
        private set
    var valuesLoading by mutableStateOf(false)
        private set

    var strict by mutableStateOf(true)
        private set
    var primaryOnly by mutableStateOf(false)
    var score by mutableStateOf<ScoreDto?>(null)
        private set
    var quality by mutableStateOf<QualityDto?>(null)
        private set
    var qualityTab by mutableStateOf(QualityTab.EXTRACT)
        private set
    var overview by mutableStateOf<OverviewDto?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    var system by mutableStateOf<SystemDto?>(null)
        private set
    var demo by mutableStateOf(emptyList<DemoCaseDto>())
        private set
    var profileUpload by mutableStateOf<ProfileUploadDto?>(null)
        private set

    val uploads = mutableStateListOf<UploadItem>()
    var uploadProfile by mutableStateOf<String?>(null)
    var dragging by mutableStateOf(false)
        private set
    var unseen by mutableStateOf(emptySet<String>())
        private set

    var toast by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var focusRequests by mutableIntStateOf(0)
        private set

    private val pageCache = LinkedHashMap<String, PageImage>()
    private var valuesJob: Job? = null
    private var toastJob: Job? = null
    private var uploadSequence = 0
    private var writtenHash: String? = null

    val ref: SetRef? get() = set?.ref
    val profile: ProfileDto? get() = set?.profile
    val article: ArticleDto? get() = articleId?.let { articles[it] }
    val canCompare: Boolean get() = set?.canCompare == true && set?.source == SourceDto.RESULTS
    val comparison: ComparisonDto? get() = if (compare && canCompare) articleId?.let { comparisons[it] } else null
    val totalValues: Int get() = set?.articles?.sumOf { it.valueCount } ?: 0
    val activeUploads: List<UploadItem> get() = uploads.filter { it.stage == UploadStage.UPLOADING }

    fun start() = request {
        profiles = client.profiles()
        sets = client.sets()
        listenForLocationHash { hash -> if (hash != writtenHash) AppRoute.parse(hash)?.let { route -> request { apply(route) } } }
        listenForDroppedFiles(onDrag = { dragging = it }, onFiles = ::uploadFiles)
        val route = AppRoute.parse(readLocationHash())
        if (route != null) apply(route) else defaultSet()?.let { loadSet(it.ref, null) }
        syncHash()
    }

    fun openSet(ref: SetRef, source: SourceDto?) = navigate {
        loadSet(ref, source)
        screen = if (screen == AppScreen.ARTICLE || screen == AppScreen.AREAS) AppScreen.ARTICLES else screen
        loadScreenData()
    }

    fun openScreen(target: AppScreen) = navigate {
        if (target != AppScreen.VALUES) evidence = null
        if (target != AppScreen.AREAS && set == null) defaultSet()?.let { loadSet(it.ref, null) }
        screen = target
        loadScreenData()
    }

    fun goHome() = openScreen(AppScreen.ARTICLES)

    fun openArticle(id: String, ref: SetRef? = null) = navigate {
        if (ref != null && ref != this.ref) loadSet(ref, null)
        ensureArticle(id)
        articleId = id
        screen = AppScreen.ARTICLE
        articleTab = ArticleTab.VALUES
        authorsOpen = false
        evidence = null
        markSeen(id)
        refreshComparison()
    }

    fun openArticleTab(tab: ArticleTab) {
        articleTab = tab
        syncHash()
    }

    fun openValue(article: String, id: String) = openEvidence(Evidence.OnValue(article, id))

    fun openIssue(article: String, id: String) = openEvidence(Evidence.OnIssue(article, id))

    fun closeEvidence() {
        evidence = null
        fixing = false
        dismissing = false
        syncHash()
    }

    fun openRoute(hash: String) {
        AppRoute.parse(hash)?.let { route -> navigate { apply(route) } }
    }

    fun toggleCompare(on: Boolean) {
        compare = on
        refreshComparison()
    }

    fun turnViewer(key: String, page: Int) {
        viewerPages[key] = page
    }

    fun changeZoom(delta: Int, full: Boolean) {
        if (full) fullZoom = (fullZoom + 0.2f * delta).coerceIn(0.6f, 2.4f) else zoom = (zoom + 0.2f * delta).coerceIn(0.6f, 2.4f)
    }

    fun toggleDim() {
        dim = !dim
    }

    fun showWholePage() {
        dim = false
    }

    fun openFullscreen(target: ViewerTarget) {
        fullscreen = target
    }

    fun closeFullscreen() {
        fullscreen = null
    }

    fun evidenceValue(): ValueDto? = (evidence as? Evidence.OnValue)?.let { e -> articles[e.article]?.values?.firstOrNull { it.id == e.id } }

    fun evidenceIssue(): IssueDto? = (evidence as? Evidence.OnIssue)?.let { e -> articles[e.article]?.issues?.firstOrNull { it.id == e.id } }

    fun confirmValue() = reviewValue(ValueReviewStateDto.CONFIRMED, null)

    fun rejectValue() = reviewValue(ValueReviewStateDto.REJECTED, null)

    fun startFix() {
        if (evidenceValue() != null) fixing = true
    }

    fun cancelFix() {
        fixing = false
        focusRoot()
    }

    fun saveFix(text: String): Boolean {
        val number = text.trim().replace(',', '.')
        if (number.toDoubleOrNull() == null) {
            showToast("Введите число")
            return false
        }
        reviewValue(ValueReviewStateDto.FIXED, number)
        return true
    }

    fun undoValueReview() {
        val value = evidenceValue() ?: return
        saveValueReview(value, null, null, advance = false)
    }

    fun stepValue(delta: Int, onlyTodo: Boolean = false) {
        val value = evidenceValue() ?: return
        val list = articles[value.publicationId]?.values.orEmpty()
        val index = list.indexOfFirst { it.id == value.id }
        if (index < 0) return
        val next = (1 until list.size).asSequence()
            .map { list[((index + delta * it) % list.size + list.size) % list.size] }
            .firstOrNull { !onlyTodo || it.review == null }
        if (next == null) {
            if (onlyTodo) showToast("Все значения статьи проверены")
            return
        }
        openValue(next.publicationId, next.id)
    }

    fun confirmIssue() = reviewIssue(IssueReviewStateDto.CONFIRMED, null)

    fun startDismiss() {
        dismissing = true
    }

    fun cancelDismiss() {
        dismissing = false
    }

    fun dismissIssue(reason: String) {
        reviewIssue(IssueReviewStateDto.DISMISSED, reason)
        showToast("Замечание снято: в счётчиках его больше нет")
    }

    fun undoIssueReview() = reviewIssue(null, null)

    fun updateFilter(filter: ValueFilter, typed: Boolean = false) {
        val single = filter.metrics.size == 1
        valueFilter = if (single) filter else filter.copy(min = "", max = "")
        reloadValues(if (typed) TYPING_DELAY else 0L)
    }

    fun sortValues(sort: ValueSortDto) {
        val current = valueFilter
        updateFilter(current.copy(sort = sort, descending = if (current.sort == sort) !current.descending else false))
    }

    fun resetFilter() = updateFilter(ValueFilter())

    fun loadMoreValues() {
        if (valuesLoading || valueRows.size >= valueTotal) return
        valuesJob = scope.launch { fetchValues(valueRows.size) }
    }

    fun filterByQuery(query: String) = navigate {
        screen = AppScreen.VALUES
        evidence = null
        updateFilter(ValueFilter(query = query))
    }

    fun selectQualityTab(tab: QualityTab) {
        qualityTab = tab
        syncHash()
    }

    fun selectStrict(value: Boolean) {
        strict = value
        val current = ref ?: return
        request { score = client.score(current, strict) }
        refreshComparison()
    }

    fun pickArticles() {
        scope.launch {
            if (screen != AppScreen.ARTICLES) openScreen(AppScreen.ARTICLES)
            uploadFiles(pickFiles(listOf("pdf"), multiple = true))
        }
    }

    fun uploadFiles(files: List<LocalFile>) {
        val pdfs = files.filter { it.name.endsWith(".pdf", ignoreCase = true) }
        if (pdfs.isEmpty()) {
            if (files.isNotEmpty()) showToast("Нужны файлы PDF")
            return
        }
        val target = uploadProfile ?: profile?.name ?: profiles.firstOrNull()?.name ?: return
        if (screen != AppScreen.ARTICLES) openScreen(AppScreen.ARTICLES)
        pdfs.forEach { file ->
            val item = UploadItem(++uploadSequence, file, target)
            uploads += item
            scope.launch { upload(item) }
        }
    }

    fun removeUploads(ids: Set<Int>) {
        uploads.removeAll { it.id in ids }
    }

    fun pickProfile() {
        scope.launch {
            val files = pickFiles(listOf("json", "md"), multiple = true)
            if (files.isEmpty()) return@launch
            try {
                val result = client.uploadProfile(files)
                profileUpload = result
                if (result.profile != null) {
                    profiles = client.profiles()
                    sets = client.sets()
                    system = client.system()
                    result.profile?.let { showToast("Область «${it.title}» подключена") }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = describe(failure)
            }
        }
    }

    fun exportCsv() {
        val current = ref ?: return
        download(client.exportUrl(current, set?.source))
    }

    fun copyLink(route: AppRoute = currentRoute()) {
        copyToClipboard(shareableLink(route.encode()))
        showToast("Ссылка скопирована")
    }

    fun dismissError() {
        error = null
    }

    fun focusRoot() {
        focusRequests++
    }

    fun isUnseen(id: String): Boolean = ref?.let { "${it.path}/$id" in unseen } == true

    fun hasUnseenElsewhere(): Boolean = unseen.any { key -> ref?.let { !key.startsWith(it.path + "/") } != false }

    fun setHasUnseen(ref: SetRef): Boolean = unseen.any { it.startsWith(ref.path + "/") }

    fun activeIssues(article: ArticleDto): List<IssueDto> = article.issues.filter { it.review?.state != IssueReviewStateDto.DISMISSED }

    fun activeIssueCount(value: ValueDto): Int {
        val issues = articles[value.publicationId]?.issues ?: return value.issues.size
        return issues.count { it.id in value.issues && it.review?.state != IssueReviewStateDto.DISMISSED }
    }

    suspend fun pageImage(article: String, page: Int, fragments: List<String>): PageImage? {
        val current = ref ?: return null
        val key = "${current.path}/$article/$page|" + fragments.joinToString("\u0001")
        pageCache[key]?.let { return it }
        val loaded = runCatching { client.page(current, article, page, fragments) }.getOrNull() ?: return null
        pageCache[key] = loaded
        if (pageCache.size > PAGE_CACHE) pageCache.remove(pageCache.keys.first())
        return loaded
    }

    fun showToast(message: String) {
        toast = message
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(TOAST_MILLIS)
            toast = null
        }
    }

    fun currentRoute(): AppRoute {
        val current = ref ?: return AppRoute(AppScreen.AREAS)
        val source = set?.source?.takeIf { set?.sources?.size ?: 0 > 1 }
        val shown = evidence
        return when {
            screen == AppScreen.AREAS -> AppRoute(AppScreen.AREAS)
            shown != null -> AppRoute(
                AppScreen.ARTICLE, current, source, shown.article,
                value = (shown as? Evidence.OnValue)?.id,
                issue = (shown as? Evidence.OnIssue)?.id,
            )
            screen == AppScreen.ARTICLE -> AppRoute(AppScreen.ARTICLE, current, source, articleId, tab = articleTab.slug.takeIf { articleTab != ArticleTab.VALUES })
            screen == AppScreen.QUALITY -> AppRoute(AppScreen.QUALITY, current, source, tab = qualityTab.slug)
            else -> AppRoute(screen, current, source)
        }
    }

    private suspend fun apply(route: AppRoute) {
        val target = route.ref
        if (route.screen == AppScreen.AREAS || target == null) {
            evidence = null
            screen = AppScreen.AREAS
            loadScreenData()
            return
        }
        if (target != ref || (route.source != null && route.source != set?.source)) loadSet(target, route.source)
        evidence = null
        screen = route.screen
        when (route.screen) {
            AppScreen.ARTICLE -> {
                val id = route.article ?: return
                ensureArticle(id)
                articleId = id
                markSeen(id)
                articleTab = when {
                    route.issue != null -> ArticleTab.ISSUES
                    else -> ArticleTab.entries.firstOrNull { it.slug == route.tab } ?: ArticleTab.VALUES
                }
                evidence = route.value?.let { Evidence.OnValue(id, it) } ?: route.issue?.let { Evidence.OnIssue(id, it) }
                refreshComparison()
            }
            AppScreen.QUALITY -> {
                qualityTab = QualityTab.entries.firstOrNull { it.slug == route.tab } ?: QualityTab.EXTRACT
                loadScreenData()
            }
            else -> loadScreenData()
        }
    }

    private fun openEvidence(target: Evidence) {
        if (target != evidence) {
            fixing = false
            dismissing = false
        }
        evidence = target
        focusRoot()
        syncHash()
        if (articles[target.article] == null) request { ensureArticle(target.article) }
    }

    private suspend fun loadSet(ref: SetRef, source: SourceDto?) {
        val loaded = client.set(ref, source)
        set = loaded
        articles.clear()
        comparisons.clear()
        viewerPages.clear()
        pageCache.clear()
        evidence = null
        articleId = null
        listQuery = ""
        score = null
        quality = null
        overview = null
        valueTable = null
        valueRows = emptyList()
        valueFilter = ValueFilter()
        uploadProfile = uploadProfile ?: ref.profile
    }

    private suspend fun loadScreenData() {
        val current = ref
        when (screen) {
            AppScreen.VALUES -> if (valueTable == null) {
                valuesJob?.cancel()
                fetchValues(0)
            }
            AppScreen.OVERVIEW -> if (overview == null && current != null) {
                loading = true
                try {
                    overview = client.overview(current, set?.source)
                } finally {
                    loading = false
                }
            }
            AppScreen.QUALITY -> if (current != null && (quality == null || (score == null && set?.canCompare == true))) {
                loading = true
                try {
                    quality = client.quality(current, set?.source)
                    score = if (set?.canCompare == true) client.score(current, strict) else null
                } finally {
                    loading = false
                }
            }
            AppScreen.AREAS -> {
                system = client.system()
                demo = client.demo()
            }
            else -> Unit
        }
    }

    private suspend fun ensureArticle(id: String) {
        if (articles[id] != null) return
        val current = set ?: return
        articles[id] = client.article(current.ref, id, current.source)
    }

    private fun refreshComparison() {
        val id = articleId ?: return
        val current = ref ?: return
        if (!compare || !canCompare) return
        scope.launch {
            runCatching { client.comparison(current, id, strict) }.getOrNull()?.let { comparisons[id] = it }
        }
    }

    private fun reloadValues(delayMillis: Long) {
        valuesJob?.cancel()
        valuesJob = scope.launch {
            delay(delayMillis)
            fetchValues(0)
        }
    }

    private suspend fun fetchValues(offset: Int) {
        val current = set ?: return
        valuesLoading = true
        try {
            val page = client.values(current.ref, current.source, valueFilter, offset, VALUES_PAGE)
            valueTable = page
            valueTotal = page.total
            valueRows = if (offset == 0) page.values else valueRows + page.values
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = describe(failure)
        } finally {
            valuesLoading = false
        }
    }

    private fun reviewValue(state: ValueReviewStateDto, value: String?) {
        val target = evidenceValue() ?: return
        saveValueReview(target, state, value, advance = true)
    }

    private fun saveValueReview(target: ValueDto, state: ValueReviewStateDto?, value: String?, advance: Boolean) {
        val current = set ?: return
        val review = state?.let { ValueReviewDto(it, value) }
        applyValueReview(target.publicationId, target.id, review)
        fixing = false
        if (advance) stepValue(1, onlyTodo = true)
        request { client.reviewValue(current.ref, current.source, ValueReviewRequestDto(target.publicationId, target.id, state, value)) }
    }

    private fun applyValueReview(article: String, id: String, review: ValueReviewDto?) {
        articles[article]?.let { loaded ->
            val updated = loaded.copy(values = loaded.values.map { if (it.id == id) it.copy(review = review) else it })
            articles[article] = updated
            updateSummary(article) { it.copy(reviewed = updated.values.count { value -> value.review != null }) }
        }
        valueRows = valueRows.map { if (it.publicationId == article && it.id == id) it.copy(review = review) else it }
    }

    private fun reviewIssue(state: IssueReviewStateDto?, reason: String?) {
        val target = evidenceIssue() ?: return
        val current = set ?: return
        val review = state?.let { IssueReviewDto(it, reason) }
        articles[target.publicationId]?.let { loaded ->
            val updated = loaded.copy(issues = loaded.issues.map { if (it.id == target.id) it.copy(review = review) else it })
            articles[target.publicationId] = updated
            updateSummary(target.publicationId) { it.copy(issueCount = activeIssues(updated).size) }
        }
        dismissing = false
        request { client.reviewIssue(current.ref, current.source, IssueReviewRequestDto(target.publicationId, target.id, state, reason)) }
    }

    private fun updateSummary(article: String, change: (ArticleSummaryDto) -> ArticleSummaryDto) {
        val current = set ?: return
        set = current.copy(articles = current.articles.map { if (it.id == article) change(it) else it })
    }

    private suspend fun upload(item: UploadItem) {
        try {
            val result = client.uploadArticle(item.profile, item.file) { sent, total ->
                item.sent = sent
                item.total = total.coerceAtLeast(1L)
            }
            item.result = result
            item.sent = item.total
            if (result.added.isEmpty()) {
                item.problem = result.rejected.firstOrNull()?.reason ?: "файл не принят"
                item.stage = UploadStage.FAILED
                return
            }
            item.stage = UploadStage.DONE
            unseen = unseen + result.added.map { "${result.ref.path}/$it" }
            sets = client.sets()
            if (ref == result.ref) {
                val reloaded = client.set(result.ref, set?.source)
                set = reloaded
            }
            showToast("«${item.file.name}» загружена")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            item.problem = failure.message ?: "не удалось загрузить"
            item.stage = UploadStage.FAILED
        }
    }

    private fun markSeen(id: String) {
        val current = ref ?: return
        unseen = unseen - "${current.path}/$id"
    }

    private fun defaultSet(): SetSummaryDto? =
        sets.firstOrNull { it.sources.isNotEmpty() && !it.uploads } ?: sets.firstOrNull { it.sources.isNotEmpty() } ?: sets.firstOrNull()

    private fun syncHash() {
        val hash = currentRoute().encode()
        writtenHash = hash
        writeLocationHash(hash)
    }

    private fun navigate(block: suspend () -> Unit) = request {
        block()
        syncHash()
    }

    private fun request(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = describe(failure)
            }
        }
    }

    private fun describe(failure: Exception) = "Нет связи с сервером ${client.baseUrl}: ${failure.message ?: failure::class.simpleName}"

    private companion object {
        const val VALUES_PAGE = 200
        const val TYPING_DELAY = 300L
        const val TOAST_MILLIS = 3200L
        const val PAGE_CACHE = 48
    }
}
