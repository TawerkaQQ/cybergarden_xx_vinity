package cybergarden.vinity.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.AnswerDto
import cybergarden.vinity.api.AnswerSourceDto
import cybergarden.vinity.app.AppStore

private const val EXAMPLE_METRICS = 2
private const val SOURCE_LINES = 4

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(store: AppStore) {
    val set = store.set
    val profile = store.profile
    val answer = store.answer
    val subtitle = listOfNotNull(profile?.title, set?.let { "набор ${setTitle(it.ref, it.uploads)}" }, set?.source?.title()).joinToString(" · ")
    ScreenList("ask-${set?.ref?.path}-${set?.source}") {
        centered("head") { PageHeader("Вопрос по статьям", subtitle) }
        centered("intro") {
            Explain("Система ищет фрагменты в тексте статей набора и в извлечённых значениях, а модель отвечает только по ним. У каждого утверждения есть номер источника: по клику открывается страница статьи или карточка значения.")
        }
        centered("input") {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InputBox(store.question, { store.question = it }, "Например: какой лучший результат и в какой статье?", Modifier.weight(1f), onDone = { store.ask() })
                    PrimaryButton(if (store.asking) "Ищу…" else "Спросить", { store.ask() }, enabled = !store.asking && store.question.isNotBlank())
                }
                val examples = examples(store)
                if (examples.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        examples.forEach { example -> Chip(example, pressed = false, onClick = { store.ask(example) }) }
                    }
                }
            }
        }
        if (store.asking) {
            centered("asking") {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spinner()
                    Muted("Ищу фрагменты и жду ответ модели — обычно 5–15 секунд")
                }
            }
        }
        if (answer != null && !store.asking) {
            centered("answer") { AnswerBlock(store, answer) }
            val (cited, others) = if (answer.answer == null) answer.sources to emptyList() else answer.sources.partition { it.cited }
            if (cited.isNotEmpty()) {
                val hint = if (answer.answer == null) "фрагменты, найденные поиском по статьям" else "на них ссылается ответ"
                centered("sources-head") { SectionHeader("Источники", hint) }
                cited.forEach { source -> centered("source-${source.number}") { SourceRow(store, source) } }
            }
            if (others.isNotEmpty()) {
                centered("others-toggle") {
                    LinkButton(
                        (if (store.otherSourcesOpen) "Скрыть" else "Показать") + " остальные найденные фрагменты (${others.size}) — ответ на них не опирается",
                        { store.otherSourcesOpen = !store.otherSourcesOpen },
                        Modifier.padding(top = 12.dp, bottom = 8.dp),
                        small = true,
                    )
                }
                if (store.otherSourcesOpen) others.forEach { source -> centered("other-${source.number}") { SourceRow(store, source) } }
            }
        }
    }
}

@Composable
private fun AnswerBlock(store: AppStore, answer: AnswerDto) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        answer.problem?.let { problem -> Alert(Tone.WARN, Glyph.WARN) { Text(problem, style = T.body) } }
        answer.answer?.let { text ->
            Block {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(answer.question, style = T.h3)
                    Text(text, style = T.body)
                    Muted("Ответил: ${answer.engine}", small = true)
                }
            }
        }
        if (answer.answer == null && answer.sources.isEmpty() && answer.problem == null) EmptyState("Ничего не нашлось")
        if (answer.answer == null && answer.sources.isNotEmpty()) Muted("Ответа модели нет — ниже найденные фрагменты. Искал: ${answer.engine}", small = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceRow(store: AppStore, source: AnswerSourceDto) {
    Block(Modifier.padding(bottom = 8.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Badge("[${source.number}]", if (source.cited) Tone.PRI else Tone.NEUTRAL)
                source.publicationId?.let { id ->
                    Badge(id, hint = store.set?.articles?.firstOrNull { it.id == id }?.title)
                }
                source.page?.let { Badge("стр. $it") }
                if (source.valueId != null) Badge("значение из таблиц", Tone.INFO)
                source.url?.let { Badge(it, Tone.INFO) }
            }
            Text(source.text.replace('\n', ' '), style = T.body, maxLines = SOURCE_LINES, overflow = TextOverflow.Ellipsis)
            val publication = source.publicationId
            if (publication != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val valueId = source.valueId
                    if (valueId != null) LinkButton("Открыть значение", { store.openValue(publication, valueId) }, small = true)
                    else LinkButton("Открыть страницу", { store.openSource(publication, source.page, source.text) }, small = true, glyph = Glyph.EXPAND)
                    LinkButton("Статья", { store.openArticle(publication) }, small = true)
                }
            }
        }
    }
}

private fun examples(store: AppStore): List<String> {
    val metrics = store.profile?.metrics.orEmpty().take(EXAMPLE_METRICS)
    return metrics.map { metric -> "Какое самое высокое значение ${metric.code} и в какой статье?" } +
        listOfNotNull(store.set?.articles?.firstOrNull { it.processed }?.id?.let { "Что измеряли в статье $it и какие получили результаты?" })
}
