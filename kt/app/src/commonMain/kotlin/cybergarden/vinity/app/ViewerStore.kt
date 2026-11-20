package cybergarden.vinity.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import cybergarden.vinity.api.DatasetDto
import cybergarden.vinity.api.IssueDto
import cybergarden.vinity.api.PublicationDto
import cybergarden.vinity.api.ResultDto
import cybergarden.vinity.api.ScoreDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ViewerStore(private val client: VinityClient, private val scope: CoroutineScope) {

    var datasets by mutableStateOf(emptyList<String>())
        private set
    var dataset by mutableStateOf<DatasetDto?>(null)
        private set
    var score by mutableStateOf<ScoreDto?>(null)
        private set
    var strict by mutableStateOf(false)
        private set
    var selectedPublication by mutableStateOf<PublicationDto?>(null)
        private set
    var selectedResult by mutableStateOf<ResultDto?>(null)
        private set
    var selectedIssue by mutableStateOf<IssueDto?>(null)
        private set
    var page by mutableStateOf<ImageBitmap?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val results: List<ResultDto>
        get() = dataset?.results?.filter { it.publicationId == selectedPublication?.id }.orEmpty()

    val issues: List<IssueDto>
        get() = dataset?.issues?.filter { it.publicationId == selectedPublication?.id }.orEmpty()

    fun start() = request {
        datasets = client.datasets()
        datasets.firstOrNull()?.let(::open)
    }

    fun open(name: String) = request {
        val loaded = client.dataset(name)
        dataset = loaded
        selectPublication(loaded.publications.firstOrNull())
        score = client.score(name, strict)
    }

    fun toggleStrict() {
        strict = !strict
        val name = dataset?.name ?: return
        request { score = client.score(name, strict) }
    }

    fun selectPublication(publication: PublicationDto?) {
        selectedPublication = publication
        selectedResult = null
        selectedIssue = null
        page = null
    }

    fun selectResult(result: ResultDto) {
        selectedResult = result
        selectedIssue = null
        showPage(result.publicationId, result.page, result.fragment)
    }

    fun selectIssue(issue: IssueDto) {
        selectedIssue = issue
        selectedResult = null
        showPage(issue.publicationId, issue.page, issue.fragment)
    }

    private fun showPage(publicationId: String, number: Int?, fragment: String?) {
        page = null
        val name = dataset?.name ?: return
        if (number == null) return
        request { page = client.page(name, publicationId, number, fragment)?.toImageBitmap() }
    }

    private fun request(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { error = null }
                .onFailure { error = "Нет связи с сервером ${client.baseUrl}: ${it.message}" }
        }
    }
}
