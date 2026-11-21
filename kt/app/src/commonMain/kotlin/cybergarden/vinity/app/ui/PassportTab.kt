package cybergarden.vinity.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.AccuracyDto
import cybergarden.vinity.api.PassportDto
import cybergarden.vinity.app.AppStore

private const val SHORT_HASH = 12

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.passportTab(store: AppStore, passport: PassportDto?) {
    if (passport == null) {
        centered("passport-loading") { ScreenLoading() }
        return
    }
    centered("passport-intro") {
        Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Explain("Что в наборе, откуда он взялся и как получить его заново.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Скачать паспорт (JSON)", store::downloadPassport, glyph = Glyph.DOWNLOAD)
                DefaultButton("Выгрузить таблицы CSV", store::exportCsv, glyph = Glyph.FILE)
            }
            Muted("Сформирован ${passport.generatedAt}", small = true)
        }
    }
    passport.sections.forEach { section ->
        centered("passport-${section.title}") {
            Block(Modifier.padding(bottom = 12.dp)) {
                SectionHeader(section.title)
                section.facts.forEachIndexed { index, fact ->
                    FieldRow(fact.label, first = index == 0, info = fact.hint) {
                        Text(fact.value, style = T.body, textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
    if (passport.accuracy.isNotEmpty()) {
        centered("passport-accuracy") {
            Block(Modifier.padding(bottom = 12.dp)) {
                SectionHeader("Точность и полнота против эталона", "по уникальным значениям; в скобках — по всем упоминаниям")
                passport.accuracy.forEachIndexed { index, accuracy -> AccuracyRow(accuracy, index == 0) }
            }
        }
    }
    if (passport.byCheck.isNotEmpty()) {
        centered("passport-checks") {
            Block(Modifier.padding(bottom = 12.dp)) {
                SectionHeader("Замечания по проверкам")
                passport.byCheck.forEachIndexed { index, count -> FieldRow(store.profile.checkTitle(count.name), count.count.toString(), first = index == 0) }
            }
        }
    }
    if (passport.overlaps.isNotEmpty()) {
        centered("passport-overlaps") {
            Block(Modifier.padding(bottom = 12.dp)) {
                SectionHeader("Одни и те же результаты в разных статьях", RepeatHint)
                passport.overlaps.forEachIndexed { index, overlap ->
                    FieldRow("${overlap.first} и ${overlap.second}", first = index == 0) {
                        Text(plural(overlap.values, Values), style = T.body, textAlign = TextAlign.End)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinkButton(overlap.first, { store.openArticle(overlap.first) }, small = true)
                            LinkButton(overlap.second, { store.openArticle(overlap.second) }, small = true)
                        }
                    }
                }
            }
        }
    }
    if (passport.outputs.isNotEmpty()) {
        centered("passport-outputs") {
            Block(Modifier.padding(bottom = 12.dp)) {
                SectionHeader("Выходные файлы", "первые ${SHORT_HASH} знаков sha256, полный хэш — в JSON")
                passport.outputs.forEachIndexed { index, file ->
                    FieldRow(file.name, first = index == 0) {
                        Text(listOfNotNull(file.rows?.let { "строк: $it" }, kilobytes(file.bytes), file.modified).joinToString(" · "), style = T.body, textAlign = TextAlign.End)
                        Text(file.sha256.take(SHORT_HASH), style = T.mono, color = C.fg2)
                    }
                }
            }
        }
    }
    if (passport.articles.isNotEmpty()) {
        centered("passport-articles") {
            Block(Modifier.padding(bottom = 12.dp)) {
                SectionHeader("Входные PDF")
                passport.articles.forEachIndexed { index, article ->
                    FieldRow(article.id, first = index == 0) {
                        Text(article.sha256.take(SHORT_HASH), style = T.mono)
                        val origin = listOfNotNull(article.promptVersion, article.models.joinToString().ifEmpty { null }).joinToString(" · ")
                        Text(origin.ifEmpty { "не обрабатывалась" }, style = T.small, color = C.fg2, textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
    centered("passport-reproduce") {
        Block(Modifier.padding(bottom = 24.dp)) {
            SectionHeader("Как воспроизвести")
            passport.reproduce.forEachIndexed { index, command ->
                Column(Modifier.fillMaxWidth().topDivider(index > 0, C.divider).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(command.title, Modifier.weight(1f), style = T.body, color = C.fg2)
                        GhostButton(Glyph.LINK, "Скопировать команду", { store.copyCommand(command.command) })
                    }
                    Text(command.command, style = T.mono)
                }
            }
        }
    }
}

@Composable
private fun AccuracyRow(accuracy: AccuracyDto, first: Boolean) {
    val primary = accuracy.primaryRecords
    val all = accuracy.allMentions
    val info = "Значение считается найденным, если совпадают поля: ${accuracy.matchedBy.joinToString()}. Сравнивались статьи, у которых есть и результат, и эталон: ${accuracy.publicationsScored}."
    FieldRow(if (accuracy.strict) "Строгое сравнение" else "Мягкое сравнение", first = first, info = info) {
        Text(
            "точность ${fixed2(primary.precision)} (${fixed2(all.precision)}) · полнота ${fixed2(primary.recall)} (${fixed2(all.recall)}) · F1 ${fixed2(primary.f1)} (${fixed2(all.f1)})",
            style = T.body,
            textAlign = TextAlign.End,
        )
    }
}

private fun kilobytes(bytes: Long): String = if (bytes < 1024) "$bytes Б" else "${bytes / 1024} КБ"
