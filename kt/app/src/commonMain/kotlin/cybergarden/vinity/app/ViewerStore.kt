package cybergarden.vinity.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import cybergarden.vinity.api.DatasetDto
import cybergarden.vinity.api.DatasetRef
import cybergarden.vinity.api.IssueDto
import cybergarden.vinity.api.PublicationDto
import cybergarden.vinity.api.ScoreDto
import cybergarden.vinity.api.ValueDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ViewerStore(private val client: VinityClient, private val scope: CoroutineScope) {

    var datasets by mutableStateOf(emptyList<DatasetRef>())
        private set
    var dataset by mutableStateOf<DatasetDto?>(null)
        private set
    var score by mutableStateOf<ScoreDto?>(null)
        private set
    var strict by mutableStateOf(false)
        private set
    var selectedPublication by mutableStateOf<PublicationDto?>(null)
        private set
    var selectedValue by mutableStateOf<ValueDto?>(null)
        private set
    var selectedIssue by mutableStateOf<IssueDto?>(null)
        private set
    var page by mutableStateOf<ImageBitmap?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val values: List<ValueDto>
        get() = dataset?.values?.filter { it.publicationId == selectedPublication?.id }.orEmpty()

    val issues: List<IssueDto>
        get() = dataset?.issues?.filter { it.publicationId == selectedPublication?.id }.orEmpty()

    fun start() = request {
        datasets = client.datasets()
        datasets.firstOrNull()?.let(::open)
    }

    fun open(ref: DatasetRef) = request {
        val loaded = client.dataset(ref)
        dataset = loaded
        selectPublication(loaded.publications.firstOrNull())
        score = client.score(ref, strict)
    }

    fun toggleStrict() {
        strict = !strict
        val ref = dataset?.ref ?: return
        request { score = client.score(ref, strict) }
    }

    fun selectPublication(publication: PublicationDto?) {
        selectedPublication = publication
        selectedValue = null
        selectedIssue = null
        page = null
    }

    fun selectValue(value: ValueDto) {
        selectedValue = value
        selectedIssue = null
        showPage(value.publicationId, value.page, value.fragment)
    }

    fun selectIssue(issue: IssueDto) {
        selectedIssue = issue
        selectedValue = null
        showPage(issue.publicationId, issue.page, issue.fragment)
    }

    private fun showPage(publicationId: String, number: Int?, fragment: String?) {
        page = null
        val ref = dataset?.ref ?: return
        if (number == null) return
        request { page = client.page(ref, publicationId, number, fragment)?.toImageBitmap() }
    }

    private fun request(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { error = null }
                .onFailure { error = "Нет связи с сервером ${client.baseUrl}: ${it.message}" }
        }
    }
}
