package cybergarden.vinity.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cybergarden.vinity.api.AnswerDto
import cybergarden.vinity.api.RagAskDto
import cybergarden.vinity.api.RagExchangeDto
import cybergarden.vinity.api.RagStatusDto
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
import cybergarden.vinity.api.PassportDto
import cybergarden.vinity.api.ProcessingStateDto
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
import cybergarden.vinity.app.ui.Values
import cybergarden.vinity.app.ui.checkTitle
import cybergarden.vinity.app.ui.fixedAmount
import cybergarden.vinity.app.ui.metricTitle
import cybergarden.vinity.app.ui.pageAndPlace
import cybergarden.vinity.app.ui.plural
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ArticleTab(val slug: String) { VALUES("values"), ISSUES("issues"), RECORDS("records"), INFO("info") }

enum class ListFilter { ALL, ISSUES, OUT }

enum class GroupBy { RECORD, METRIC }

class TableSort(val column: Int, val descending: Boolean)

enum class QualityTab(val slug: String) { EXTRACT("extract"), QUOTES("quotes"), NORMALIZATION("norm"), CHECKS("checks"), PASSPORT("passport") }

sealed interface Evidence {
    val article: String
    val id: String

    data class OnValue(override val article: String, override val id: String) : Evidence
    data class OnIssue(override val article: String, override val id: String) : Evidence
}

data class Mark(val fragment: String, val error: Boolean, val focus: Boolean, val items: List<MarkItem> = emptyList())

enum class MarkKind { VALUE, ISSUE }

data class MarkItem(val kind: MarkKind, val title: String, val detail: String?, val error: Boolean, val evidence: Evidence)

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
    var allMarks by mutableStateOf(true)
        private set
    var tableSorts by mutableStateOf(emptyMap<String, TableSort>())
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
    var passport by mutableStateOf<PassportDto?>(null)
        private set
    var darkTheme by mutableStateOf(loadSetting(THEME_SETTING)?.toBooleanStrictOrNull())
        private set
    var question by mutableStateOf("")
    var answer by mutableStateOf<AnswerDto?>(null)
        private set
    var asking by mutableStateOf(false)
        private set
    var otherSourcesOpen by mutableStateOf(false)
    private var askJob: Job? = null

    var ragStatus by mutableStateOf<RagStatusDto?>(null)
        private set
    val ragHistory = mutableStateListOf<RagExchangeDto>()
    var ragQuestion by mutableStateOf("")
    var ragCsv by mutableStateOf(false)
    var ragWeb by mutableStateOf(true)
    var ragTopK by mutableStateOf<Int?>(null)
    var ragAsking by mutableStateOf<String?>(null)
        private set
    val ragWebLoading = mutableStateListOf<String>()
    var ragFocus by mutableStateOf<String?>(null)
        private set
    var ragFocusRequests by mutableIntStateOf(0)
        private set
    var ragHistoryOpen by mutableStateOf(false)
    var ragConfirmClear by mutableStateOf(false)
    var ragStatusLoading by mutableStateOf(false)
        private set
    private var ragLoaded = false
    private var articlesScreen = AppScreen.ARTICLES
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
    private val watched = mutableSetOf<SetRef>()
    private val processingSeen = mutableMapOf<String, ProcessingStateDto>()
    private var watchJob: Job? = null
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

    var scrollTop by mutableIntStateOf(0)
        private set

    fun openScreen(target: AppScreen) = navigate {
        if (target == screen) scrollTop++
        if (target != AppScreen.VALUES) evidence = null
        if (target != AppScreen.AREAS && set == null) defaultSet()?.let { loadSet(it.ref, null) }
        screen = target
        loadScreenData()
    }

    fun goHome() = openScreen(AppScreen.ARTICLES)

    fun openRag() = navigate {
        if (screen != AppScreen.RAG) articlesScreen = screen
        evidence = null
        screen = AppScreen.RAG
        loadScreenData()
    }

    fun openArticlesMode() = openScreen(if (articlesScreen == AppScreen.ARTICLE && articleId == null) AppScreen.ARTICLES else articlesScreen)

    fun refreshRagStatus() = request {
        ragStatusLoading = true
        try {
            ragStatus = client.ragStatus(refresh = true)
        } finally {
            ragStatusLoading = false
        }
    }

    fun askRag(text: String = ragQuestion) {
        val asked = text.trim()
        if (asked.isEmpty() || ragAsking != null) return
        ragQuestion = ""
        ragAsking = asked
        scope.launch {
            try {
                val exchange = client.ragAsk(RagAskDto(asked, ragCsv, ragTopK, ragWeb))
                ragHistory += exchange
                focusRag(exchange.id)
                error = null
                if (exchange.base?.problem != null) ragStatus = client.ragStatus(refresh = true)
                if (exchange.webPending) scope.launch { loadRagWeb(exchange.id) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = describe(failure)
                ragQuestion = asked
            } finally {
                ragAsking = null
            }
        }
    }

    fun repeatRag(exchange: RagExchangeDto) {
        ragCsv = exchange.csv
        ragTopK = exchange.topK
        ragWeb = exchange.web
        ragHistoryOpen = false
        askRag(exchange.question)
    }

    fun searchWeb(id: String) {
        scope.launch { loadRagWeb(id) }
    }

    fun focusRag(id: String) {
        ragFocus = id
        ragFocusRequests++
        ragHistoryOpen = false
    }

    fun clearRag() = request {
        client.ragClear()
        ragHistory.clear()
        ragConfirmClear = false
    }

    private suspend fun loadRag() {
        if (!ragLoaded) {
            val stored = client.ragHistory()
            ragHistory.clear()
            ragHistory.addAll(stored)
            ragLoaded = true
            stored.lastOrNull()?.let { focusRag(it.id) }
        }
        ragStatus = client.ragStatus(refresh = false)
    }

    private suspend fun loadRagWeb(id: String) {
        if (id in ragWebLoading) return
        ragWebLoading += id
        try {
            val updated = client.ragWeb(id) ?: return
            val index = ragHistory.indexOfFirst { it.id == id }
            if (index >= 0) ragHistory[index] = updated
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = describe(failure)
        } finally {
            ragWebLoading -= id
        }
    }

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
        if (articles[target.article] == null) request { ensureArticle(target.article) }
    }

    fun sortTable(table: String, column: Int) {
        val current = tableSorts[table]
        val descending = if (current?.column == column) !current.descending else column > 0
        tableSorts = tableSorts + (table to TableSort(column, descending))
    }

    fun toggleAllMarks() {
        allMarks = !allMarks
    }

    fun fullscreenMarks(target: ViewerTarget, page: Int): List<Mark> {
        val own = target.marks[page].orEmpty()
        val article = articles[target.article]
        if (!allMarks || article == null) return own
        val values = article.values.filter { it.page == page }.mapNotNull { value ->
            val flagged = activeIssueCount(value) > 0
            val item = MarkItem(
                kind = MarkKind.VALUE,
                title = "${profile.metricTitle(value.metric)} = ${value.fixedAmount()}",
                detail = listOfNotNull(value.recordLabel, value.pageAndPlace()).joinToString(" · ").ifEmpty { null },
                error = flagged,
                evidence = Evidence.OnValue(value.publicationId, value.id),
            )
            value.fragment?.let { Mark(it, error = flagged, focus = false, items = listOf(item)) }
        }
        val issues = activeIssues(article).filter { it.page == page }.mapNotNull { issue ->
            val item = MarkItem(MarkKind.ISSUE, profile.checkTitle(issue.check), issue.description?.take(MARK_DETAIL), true, Evidence.OnIssue(issue.publicationId, issue.id))
            issue.fragment?.let { Mark(it, error = true, focus = false, items = listOf(item)) }
        }
        return (own + values + issues).groupBy { it.fragment }.map { (fragment, group) ->
            Mark(fragment, group.any { it.error }, group.any { it.focus }, group.flatMap { it.items })
        }
    }

    fun openMarkItem(item: MarkItem) {
        fullscreen = null
        openEvidence(item.evidence)
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
        if (tab == QualityTab.PASSPORT && passport == null) request { loadPassport() }
    }

    fun ask(text: String = question) {
        val current = ref ?: return
        val asked = text.trim()
        if (asked.isEmpty()) return
        question = asked
        askJob?.cancel()
        askJob = scope.launch {
            asking = true
            try {
                answer = client.ask(current, set?.source, asked)
                otherSourcesOpen = false
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = describe(failure)
            } finally {
                asking = false
            }
        }
    }

    fun openSource(publicationId: String, page: Int?, text: String) {
        val home = page ?: 1
        val pageCount = set?.articles?.firstOrNull { it.id == publicationId }?.pageCount
        val marks = page?.let { mapOf(it to listOf(Mark(text.take(MARK_LENGTH), error = false, focus = true))) }.orEmpty()
        openFullscreen(ViewerTarget("ask-$publicationId-$home", publicationId, home, pageCount, marks))
    }

    fun downloadPassport() {
        val current = ref ?: return
        download(client.passportUrl(current, set?.source))
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

    fun processArticle(id: String) {
        val current = ref ?: return
        request {
            val status = client.processArticle(current, id)
            if (status == null) {
                showToast("Конвейер не принял статью")
                return@request
            }
            processingSeen["${current.path}/$id"] = status.state
            updateSummary(id) { it.copy(processing = status) }
            articles[id]?.let { articles[id] = it.copy(processing = status) }
            watch(current)
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

    fun toggleTheme(systemDark: Boolean) {
        val next = !(darkTheme ?: systemDark)
        darkTheme = next
        saveSetting(THEME_SETTING, next.toString())
    }

    fun copyCommand(command: String) {
        copyToClipboard(command)
        showToast("Команда скопирована")
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
        if (screen == AppScreen.RAG) return AppRoute(AppScreen.RAG)
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
        if (route.screen == AppScreen.RAG) {
            if (screen != AppScreen.RAG) articlesScreen = screen
            evidence = null
            screen = AppScreen.RAG
            loadScreenData()
            return
        }
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
        passport = null
        answer = null
        overview = null
        valueTable = null
        valueRows = emptyList()
        valueFilter = ValueFilter()
        uploadProfile = uploadProfile ?: ref.profile
        if (loaded.articles.any { it.processing?.active == true }) watch(ref)
    }

    private fun watch(target: SetRef) {
        watched += target
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            while (watched.isNotEmpty()) {
                delay(PROCESSING_POLL)
                watched.toList().forEach { polled ->
                    try {
                        poll(polled)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = describe(failure)
                    }
                }
            }
        }
    }

    private suspend fun poll(target: SetRef) {
        val shown = set?.takeIf { it.ref == target }
        val loaded = client.set(target, shown?.source)
        val finished = loaded.articles.filter { summary ->
            val state = summary.processing?.state ?: return@filter false
            val key = "${target.path}/${summary.id}"
            val before = processingSeen.put(key, state)
            before != null && before != state && summary.processing?.active == false
        }
        if (set?.ref == target) {
            set = loaded
            if (finished.isNotEmpty()) {
                finished.forEach {
                    articles.remove(it.id)
                    comparisons.remove(it.id)
                }
                score = null
                quality = null
                passport = null
                overview = null
                valueTable = null
                valueRows = emptyList()
                articleId?.takeIf { id -> finished.any { it.id == id } }?.let { ensureArticle(it) }
                loadScreenData()
            }
        }
        finished.forEach { summary ->
            val status = summary.processing ?: return@forEach
            showToast(
                if (status.state == ProcessingStateDto.DONE) "«${summary.title ?: summary.id}» обработана: ${plural(summary.valueCount, Values)}"
                else "Не удалось обработать ${summary.id}: ${status.problem.orEmpty()}",
            )
        }
        if (finished.isNotEmpty()) sets = client.sets()
        if (loaded.articles.none { it.processing?.active == true }) watched -= target
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
                if (qualityTab == QualityTab.PASSPORT && passport == null) loadPassport()
            } else if (current != null && qualityTab == QualityTab.PASSPORT && passport == null) {
                loadPassport()
            }
            AppScreen.AREAS -> {
                system = client.system()
                demo = client.demo()
            }
            AppScreen.RAG -> loadRag()
            else -> Unit
        }
    }

    private suspend fun loadPassport() {
        val current = ref ?: return
        passport = client.passport(current, set?.source)
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
            result.queued.forEach { processingSeen["${result.ref.path}/$it"] = ProcessingStateDto.QUEUED }
            if (result.queued.isNotEmpty()) watch(result.ref)
            showToast(if (result.queued.isNotEmpty()) "«${item.file.name}» загружена и отправлена на обработку" else "«${item.file.name}» загружена")
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
        const val PROCESSING_POLL = 3000L
        const val MARK_LENGTH = 160
        const val MARK_DETAIL = 220
        const val THEME_SETTING = "vinity.theme.dark"
    }
}
