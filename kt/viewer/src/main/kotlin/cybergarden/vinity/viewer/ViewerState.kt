package cybergarden.vinity.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import cybergarden.vinity.domain.model.Issue
import cybergarden.vinity.domain.model.MetricResult
import cybergarden.vinity.domain.model.Publication
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.port.SubmissionRepository
import cybergarden.vinity.viewer.pdf.PdfPageRenderer
import java.nio.file.Path

class ViewerState(
    private val root: Path,
    private val repository: SubmissionRepository,
    private val renderer: PdfPageRenderer,
) {
    var dataset by mutableStateOf(DATASETS.first())
        private set
    var submission by mutableStateOf(Submission())
        private set
    var selectedPublication by mutableStateOf<Publication?>(null)
        private set
    var selectedResult by mutableStateOf<MetricResult?>(null)
        private set
    var page by mutableStateOf<ImageBitmap?>(null)
        private set

    val results: List<MetricResult>
        get() = submission.results.filter { it.publicationId == selectedPublication?.publicationId }

    val issues: List<Issue>
        get() = submission.issues.filter { it.publicationId == selectedPublication?.publicationId }

    init {
        open(dataset)
    }

    fun open(name: String) {
        dataset = name
        submission = repository.load(root.resolve("out").resolve(name).toString()).submission
        selectPublication(submission.publications.firstOrNull())
    }

    fun selectPublication(publication: Publication?) {
        selectedPublication = publication
        selectResult(null)
    }

    fun selectResult(result: MetricResult?) {
        selectedResult = result
        page = result?.source?.page?.let { pageNumber ->
            renderer.render(pdfOf(result.publicationId), pageNumber, result.source.fragment)
        }
    }

    fun selectIssue(issue: Issue) {
        selectedResult = null
        page = issue.sourcePage?.let { renderer.render(pdfOf(issue.publicationId), it, issue.sourceFragment) }
    }

    private fun pdfOf(publicationId: String) = root.resolve("data").resolve(dataset).resolve("$publicationId.pdf")

    companion object {
        val DATASETS = listOf("dev", "test")
    }
}
