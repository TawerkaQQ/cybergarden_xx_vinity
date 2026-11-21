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
        return FactDto(
            "Нашли сверх эталона",
            if (extra.isEmpty()) "нет" else "${extra.size}, из них с цитатой $quoted",
            "Значения, которых нет у экспертов. Если число дословно стоит на странице, это скорее пропуск в эталоне, чем ошибка системы.",
        )
    }

    private fun accuracy(ref: SetRef): List<AccuracyDto> = listOf(false, true).mapNotNull { strict ->
        service.score(ref, strict)?.let { AccuracyDto(strict, it.matchedBy, it.primaryRecords, it.allMentions, it.publicationsScored) }
    }

    private fun dataset(inputs: Inputs, articles: Int) = PassportSectionDto(
        "Набор",
        listOfNotNull(
            FactDto("Область", inputs.profile.title, inputs.profile.scope.ifBlank { null }),
            FactDto("Набор", inputs.ref.set),
            FactDto("Источник значений", inputs.source?.let(::sourceTitle) ?: "значений нет"),
            FactDto("Статей", articles.toString()),
            inputs.files.profile?.let {
                FactDto(
                    "Профиль области",
                    "${inputs.profile.name}, версия ${it.sha256.take(SHORT_HASH)}",
                    "Короткий хэш файла profile.json. По нему видно, по каким правилам собран набор: другой хэш — другие правила.",
                )
            },
        ),
    )

    private fun composition(inputs: Inputs, ids: List<String>): PassportSectionDto {
        val index = inputs.index
        val publications = ids.mapNotNull { index?.publication(it) }
        val processed = ids.count { id -> index?.publication(id)?.get(Publications.IN_SCOPE) != null || index?.values(id)?.isNotEmpty() == true }
        val tables = inputs.profile.tables.filter { it.name != inputs.profile.values.table && it.name != Publications.TABLE }.mapNotNull { table ->
            index?.submission?.rows(table)?.size?.takeIf { it > 0 }?.let { FactDto("Записей в таблице ${table.name}", it.toString()) }
        }
        return PassportSectionDto(
            "Состав набора",
            listOf(
                FactDto("Обработано статей", "$processed из ${ids.size}", "Статьи, по которым есть результат: определена область или найдены значения."),
                FactDto(
                    "В области / вне области",
                    "${publications.count { it[Publications.IN_SCOPE] == TRUE }} / ${publications.count { it[Publications.IN_SCOPE] == FALSE }}",
                    "Статьи вне области — не по теме профиля, значения из них не извлекаются.",
                ),
                FactDto("Страниц PDF", inputs.documents.sumOf { it.pageCount }.toString()),
                FactDto(
                    "Сканов",
                    inputs.documents.count { it.pagesWithoutText.isNotEmpty() }.toString(),
                    "PDF без текстового слоя. Текст таких страниц распознаётся, и цитаты ищутся в распознанном тексте.",
                ),
            ) + tables,
        )
    }

    private fun values(inputs: Inputs): PassportSectionDto {
        val index = inputs.index
        val spec = inputs.profile.values
        val rows = index?.allValues.orEmpty()
        val quotes = inputs.quality?.quotes
        val normalization = inputs.quality?.normalization
        val kinds = listOf(ValueKind.TABLE to "таблицы", ValueKind.TEXT to "текст", ValueKind.FIGURE to "рисунки")
        return PassportSectionDto(
            "Значения",
            listOfNotNull(
                FactDto("Найдено значений", rows.size.toString(), "Каждое место в статье, где встречается число, включая повторы одного и того же числа."),
                FactDto(
                    "Уникальных значений",
                    rows.count { it[spec.duplicateOf] == null }.toString(),
                    "Без повторов: одно и то же число одной записи, найденное в нескольких местах статьи, считается один раз.",
                ),
                FactDto("Откуда взяты", kinds.joinToString(" · ") { (kind, title) -> "$title ${rows.count { index?.kind(it) == kind }}" }),
                FactDto("Разных показателей", rows.mapNotNull { it[spec.metric] }.distinct().size.toString()),
                inputs.source?.takeIf { it == SourceDto.RESULTS }?.let { beyondGold(inputs.ref) },
                quotes?.let {
                    FactDto(
                        "Цитата найдена",
                        "${it.found} из ${it.values} (${percent(it.found, it.values)})",
                        "Цитату ищем дословно в тексте страницы так же, как организаторы: текст из pypdf, PyMuPDF или распознавания скана.",
                    )
                },
                quotes?.let {
                    FactDto(
                        "Цитата найдена, без рисунков",
                        "${it.textFound} из ${it.textValues} (${percent(it.textFound, it.textValues)})",
                        "Подписей на графиках часто нет в текстовом слое, поэтому значения с рисунков считаем отдельно. Цель — не меньше 95 %.",
                    )
                },
                normalization?.let {
                    FactDto(
                        "Пересчитано в единицы области",
                        "${it.converted} из ${it.values}",
                        "Значения, у которых система поменяла единицу или запись числа, например .204 → 0.204. У остальных менять было нечего.",
                    )
                },
                normalization?.takeIf { it.vocabularyChecked > 0 }?.let {
                    FactDto(
                        "Поля со списком вариантов",
                        "${it.vocabularyValid} из ${it.vocabularyChecked} допустимы (${percent(it.vocabularyValid, it.vocabularyChecked)})",
                        "Поля, для которых профиль задаёт допустимые значения, например «в области / вне области».",
                    )
                },
            ),
        )
    }

    private fun checks(inputs: Inputs): PassportSectionDto {
        val checks = inputs.quality?.checks
        val audit = inputs.audit
        val repeats = inputs.index?.repeats
        val titles = inputs.profile.display.checks
        return PassportSectionDto(
            "Проверки",
            listOfNotNull(
                FactDto("Замечаний", (checks?.issues ?: 0).toString()),
                checks?.let { FactDto("Разобрано референтом", "подтверждено ${it.confirmed}, снято ${it.dismissed}") },
                audit?.let { current ->
                    val byCheck = current.issues.groupingBy { it[Issues.CHECK].orEmpty() }.eachCount().entries
                        .joinToString("; ") { (check, count) -> "${titles[check]?.title ?: check} — $count" }
                    FactDto(
                        "Нашёл сервер",
                        if (current.issues.isEmpty()) "ничего нового" else "${current.issues.size}: $byCheck",
                        "Сервер сам проверяет таблицы по правилам профиля и добавляет замечания, которых не было в issues.csv.",
                    )
                },
                audit?.let {
                    FactDto(
                        "Повторов отметил сервер",
                        it.duplicates.toString(),
                        "Повторы, не отмеченные в таблицах: сервер нашёл их по ключу повтора из профиля.",
                    )
                },
                repeats?.let {
                    FactDto(
                        "Одни и те же результаты в разных статьях",
                        if (it.overlaps.isEmpty()) "нет" else "пар статей ${it.overlaps.size}, значений ${it.values}",
                        "У двух статей совпадают показатель, число и единица хотя бы у трёх значений. Обычно одна статья приводит результаты другой.",
                    )
                },
            ),
        )
    }

    private fun origin(inputs: Inputs): PassportSectionDto {
        val articles = inputs.files.articles
        val models = articles.flatMap { it.models }.groupingBy { it }.eachCount()
        val prompts = articles.mapNotNull { it.promptVersion }.groupingBy { it }.eachCount()
        return PassportSectionDto(
            "Откуда данные",
            listOfNotNull(
                FactDto("Входные PDF", "${articles.size} шт., ${megabytes(articles.sumOf { it.bytes })}"),
                FactDto(
                    "Отпечаток входных PDF",
                    inputs.files.manifest.take(SHORT_HASH),
                    "Хэш списка «статья — хэш её PDF». Совпал — значит, набор собран из тех же файлов.",
                ),
                FactDto(
                    "Модель извлечения",
                    models.entries.joinToString { "${it.key} (статей: ${it.value})" }.ifEmpty { "неизвестна" },
                    "Какой моделью извлекались значения — по сохранённым ответам модели.",
                ),
                FactDto(
                    "Версия промпта",
                    prompts.entries.joinToString { "${it.key} (статей: ${it.value})" }.ifEmpty { "неизвестна" },
                    "Версия инструкции для модели. Меняется вместе с правилами извлечения; при той же версии и модели ответы берутся из кэша.",
                ),
                processing.info().model?.let { FactDto("Модель для новых загрузок", it) },
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
