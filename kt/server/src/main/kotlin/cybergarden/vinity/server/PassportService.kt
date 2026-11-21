package cybergarden.vinity.server

import cybergarden.vinity.api.AccuracyDto
import cybergarden.vinity.api.ArticleFileDto
import cybergarden.vinity.api.CommandDto
import cybergarden.vinity.api.CountDto
import cybergarden.vinity.api.FactDto
import cybergarden.vinity.api.OutputFileDto
import cybergarden.vinity.api.OverlapPairDto
import cybergarden.vinity.api.PassportDto
import cybergarden.vinity.api.PassportSectionDto
import cybergarden.vinity.api.QualityDto
import cybergarden.vinity.api.SetRef
import cybergarden.vinity.api.SourceDto
import cybergarden.vinity.data.provenance.Provenance
import cybergarden.vinity.data.provenance.ProvenanceReader
import cybergarden.vinity.domain.model.DocumentInfo
import cybergarden.vinity.domain.model.Issues
import cybergarden.vinity.domain.model.Profile
import cybergarden.vinity.domain.model.Publications
import cybergarden.vinity.domain.model.RecordKey
import cybergarden.vinity.domain.port.DocumentInspector
import cybergarden.vinity.domain.usecase.ArticleIndex
import cybergarden.vinity.domain.usecase.Audit
import cybergarden.vinity.domain.usecase.ValueKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.io.path.exists

class PassportService(
    private val library: Library,
    private val service: LibraryService,
    private val insights: InsightService,
    private val inspector: DocumentInspector,
    private val provenance: ProvenanceReader,
    private val processing: ProcessingService,
) {
    private class Inputs(
        val ref: SetRef,
        val profile: Profile,
        val source: SourceDto?,
        val index: ArticleIndex?,
        val documents: List<DocumentInfo>,
        val quality: QualityDto?,
        val audit: Audit?,
        val files: Provenance,
        val accuracy: List<AccuracyDto>,
    )

    fun passport(ref: SetRef, requested: SourceDto?): PassportDto? {
        if (!library.known(ref)) return null
        val source = library.source(ref, requested)
        val index = library.index(ref, source)
        val ids = library.articleIds(ref, index)
        val inputs = Inputs(
            ref = ref,
            profile = library.profile(ref.profile),
            source = source,
            index = index,
            documents = ids.mapNotNull { inspector.inspect(library.pdf(ref, it).toString()) },
            quality = source?.let { insights.quality(ref, it) },
            audit = library.audit(ref, source),
            files = provenance.read(ref.profile, ref.set, source?.let { library.location(ref, it) }),
            accuracy = if (source == SourceDto.RESULTS) accuracy(ref) else emptyList(),
        )
        return PassportDto(
            ref = ref,
            profileTitle = inputs.profile.title,
            scope = inputs.profile.scope,
            source = source,
            generatedAt = TIME.format(Instant.now()),
            sections = listOf(dataset(inputs, ids.size), composition(inputs, ids), values(inputs), checks(inputs), origin(inputs)),
            accuracy = inputs.accuracy,
            byCheck = inputs.quality?.checks?.byCheck?.filter { it.found > 0 }?.map { CountDto(it.check, it.found) }.orEmpty(),
            overlaps = index?.repeats?.overlaps?.map { OverlapPairDto(it.first, it.second, it.values) }.orEmpty(),
            articles = inputs.files.articles.map { ArticleFileDto(it.id, it.bytes, it.sha256, it.promptVersion, it.models) },
            outputs = inputs.files.outputs.map { OutputFileDto(it.name, it.bytes, it.sha256, TIME.format(it.modified), it.rows) },
            reproduce = commands(inputs),
        )
    }

    private fun beyondGold(ref: SetRef): FactDto? {
        if (!library.canCompare(ref)) return null
        val ids = library.index(ref, SourceDto.RESULTS)?.articleIds.orEmpty()
        val catalog = library.catalog(ref, SourceDto.RESULTS, complete = true) ?: return null
        val extra = ids.flatMap { id -> service.comparison(ref, id, strict = false)?.extra.orEmpty().map { RecordKey(id, it) } }
        val quoted = extra.count { catalog.get(it)?.quote?.found == true }
        return FactDto("Сверх эталона с дословной цитатой", "$quoted из ${extra.size} лишних", "значений нет у экспертов, но цитата найдена на странице: кандидаты в пропуски эталона")
    }

    private fun accuracy(ref: SetRef): List<AccuracyDto> = listOf(false, true).mapNotNull { strict ->
        service.score(ref, strict)?.let { AccuracyDto(strict, it.matchedBy, it.primaryRecords, it.allMentions, it.publicationsScored) }
    }

    private fun dataset(inputs: Inputs, articles: Int) = PassportSectionDto(
        "Набор",
        listOfNotNull(
            FactDto("Область", inputs.profile.title, inputs.profile.scope),
            FactDto("Набор", inputs.ref.set),
            FactDto("Источник значений", inputs.source?.let(::sourceTitle) ?: "значений нет", "результат системы или эталон экспертов"),
            FactDto("Статей", articles.toString()),
            inputs.files.profile?.let { FactDto("Профиль области", "${inputs.profile.name} · sha256 ${it.sha256.take(SHORT_HASH)}", "все предметные правила взяты из profile.json") },
        ),
    )

    private fun composition(inputs: Inputs, ids: List<String>): PassportSectionDto {
        val index = inputs.index
        val publications = ids.mapNotNull { index?.publication(it) }
        val processed = ids.count { id -> index?.publication(id)?.get(Publications.IN_SCOPE) != null || index?.values(id)?.isNotEmpty() == true }
        val tables = inputs.profile.tables.filter { it.name != inputs.profile.values.table }.mapNotNull { table ->
            index?.submission?.rows(table)?.size?.takeIf { it > 0 }?.let { FactDto("Записей в «${table.name}»", it.toString()) }
        }
        return PassportSectionDto(
            "Состав",
            listOf(
                FactDto("Обработано статей", "$processed из ${ids.size}"),
                FactDto("В области", publications.count { it[Publications.IN_SCOPE] == TRUE }.toString()),
                FactDto("Вне области", publications.count { it[Publications.IN_SCOPE] == FALSE }.toString()),
                FactDto("Страниц PDF", inputs.documents.sumOf { it.pageCount }.toString()),
                FactDto("Сканов без текстового слоя", inputs.documents.count { it.pagesWithoutText.isNotEmpty() }.toString(), "текст таких страниц распознаётся"),
            ) + tables,
        )
    }

    private fun values(inputs: Inputs): PassportSectionDto {
        val index = inputs.index
        val spec = inputs.profile.values
        val rows = index?.allValues.orEmpty()
        val quotes = inputs.quality?.quotes
        val normalization = inputs.quality?.normalization
        return PassportSectionDto(
            "Значения",
            listOfNotNull(
                FactDto("Всего упоминаний", rows.size.toString()),
                FactDto("Основных значений", rows.count { it[spec.duplicateOf] == null }.toString(), "без повторов одного числа в разных местах статьи"),
                FactDto("Повторов", rows.count { it[spec.duplicateOf] != null }.toString()),
                FactDto("Из таблиц / текста / рисунков", listOf(ValueKind.TABLE, ValueKind.TEXT, ValueKind.FIGURE).joinToString(" / ") { kind -> rows.count { index?.kind(it) == kind }.toString() }),
                FactDto("Показателей", rows.mapNotNull { it[spec.metric] }.distinct().size.toString()),
                inputs.source?.takeIf { it == SourceDto.RESULTS }?.let { beyondGold(inputs.ref) },
                quotes?.let { FactDto("Цитата найдена дословно", "${it.found} из ${it.values} (${percent(it.found, it.values)})", "по тексту страницы: pypdf, PyMuPDF или распознавание") },
                quotes?.let { FactDto("Цитата для значений из текста и таблиц", "${it.textFound} из ${it.textValues} (${percent(it.textFound, it.textValues)})") },
                normalization?.let { FactDto("Приведено к единицам профиля", "${it.converted} из ${it.values}") },
                normalization?.takeIf { it.vocabularyChecked > 0 }?.let {
                    FactDto("Поля словарей допустимы", "${it.vocabularyValid} из ${it.vocabularyChecked} (${percent(it.vocabularyValid, it.vocabularyChecked)})")
                },
            ),
        )
    }

    private fun checks(inputs: Inputs): PassportSectionDto {
        val checks = inputs.quality?.checks
        val audit = inputs.audit
        val repeats = inputs.index?.repeats
        return PassportSectionDto(
            "Проверки",
            listOfNotNull(
                FactDto("Замечаний", (checks?.issues ?: 0).toString()),
                checks?.let { FactDto("Подтверждено / отклонено референтом", "${it.confirmed} / ${it.dismissed}") },
                audit?.let { current ->
                    val byCheck = current.issues.groupingBy { it[Issues.CHECK].orEmpty() }.eachCount().entries.joinToString { "${it.key} ${it.value}" }
                    FactDto("Найдено сервером", current.issues.size.toString() + byCheck.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty(), "противоречия и несогласованные повторы, которых не было в issues.csv")
                },
                audit?.let { FactDto("Повторов отмечено сервером", it.duplicates.toString(), "по ключу повтора из профиля, где duplicate_of был пуст") },
                repeats?.let { FactDto("Совпадений между статьями", "пар статей: ${it.overlaps.size}, значений: ${it.values}", "одна метрика, число и единица у нескольких значений двух статей") },
            ),
        )
    }

    private fun origin(inputs: Inputs): PassportSectionDto {
        val articles = inputs.files.articles
        val models = articles.flatMap { it.models }.groupingBy { it }.eachCount()
        val prompts = articles.mapNotNull { it.promptVersion }.groupingBy { it }.eachCount()
        return PassportSectionDto(
            "Происхождение",
            listOfNotNull(
                FactDto("PDF", "файлов: ${articles.size}, ${megabytes(articles.sumOf { it.bytes })}"),
                FactDto("Манифест PDF", "sha256 ${inputs.files.manifest.take(SHORT_HASH)}", "хэш списка «статья — sha256 PDF»: совпадает, значит вход тот же"),
                FactDto("Модель по кэшу ответов", models.entries.joinToString { "${it.key} (${it.value})" }.ifEmpty { "в кэше нет" }),
                FactDto("Версия промпта", prompts.entries.joinToString { "${it.key} (${it.value})" }.ifEmpty { "нет файлов извлечения" }),
                processing.info().model?.let { FactDto("Модель сервера для новых PDF", it) },
                inputs.files.outputs.maxByOrNull { it.modified }?.let { FactDto("Таблицы обновлены", TIME.format(it.modified)) },
            ),
        )
    }

    private fun commands(inputs: Inputs): List<CommandDto> {
        val profile = inputs.ref.profile
        val set = inputs.ref.set
        val root = library.workspace.root
        val model = processing.info().model?.let { " --model $it" }.orEmpty()
        return listOfNotNull(
            CommandDto("Извлечь и собрать таблицы (из корня, PYTHONPATH=pipeline/src)", "python -m pipeline run --profile $profile --set $set --base-url <адрес модели>$model"),
            CommandDto("Проверить формат и цитаты", "cd kt && ./gradlew :cli:run --args=\"validate ../out/$profile/$set --pages ../work/$profile/pages\""),
            CommandDto("Оценить против эталона", "cd kt && ./gradlew :cli:run --args=\"score ../out/$profile/$set ../data/$profile/${set}_gold\"")
                .takeIf { library.canCompare(inputs.ref) },
            CommandDto("Проверка сдачи организаторов", "python data/$profile/tools/check_submission.py out/$profile/$set data/$profile/$set")
                .takeIf { root.resolve("data/$profile/tools/check_submission.py").exists() },
            CommandDto("Сайт и сервер", "kt\\scripts\\start.ps1"),
        )
    }

    private fun sourceTitle(source: SourceDto): String = when (source) {
        SourceDto.RESULTS -> "результат системы"
        SourceDto.GOLD -> "эталон экспертов"
    }

    private fun percent(part: Int, whole: Int): String = if (whole == 0) "—" else String.format(Locale.ROOT, "%.1f %%", 100.0 * part / whole)

    private fun megabytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f МБ", bytes / (1024.0 * 1024.0))

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
        const val SHORT_HASH = 12
        const val TRUE = "true"
        const val FALSE = "false"
    }
}
