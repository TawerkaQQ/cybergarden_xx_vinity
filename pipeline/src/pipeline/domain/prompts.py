import hashlib
import json
from typing import Any

from pipeline.domain.article import Article
from pipeline.domain.extraction_shape import IN_SCOPE, ExtractionShape
from pipeline.domain.profile import Table

ENGINE_VERSION = "5"
ROW_LABEL = "row_label"

SYSTEM = """\
Ты аннотатор научных статей. По статье собирается набор данных области «{title}». Его сверяют с экспертной разметкой, поэтому важны и точность, и полнота.

Что относится к области: {scope}

Статья дана текстом страниц, извлечённым из PDF, между маркерами «=== Страница N ===». N — номер страницы PDF, считая с 1, а не номер страницы журнала.

Общие правила:
- Только то, что написано в статье. Неизвестное — null. Пустое значение лучше выдуманного.
- Значения и единицы — как в статье, строками, без пересчёта и перевода единиц.
- Для поля со списком допустимых значений — только значение из списка или null. Если формулировка статьи не укладывается в список, исходную формулировку пиши в note.
- key — короткий ключ записи, уникальный внутри статьи: имя таблицы и номер, например {example_key}. В refs — key записей той таблицы, на которую указывает ссылка.
- Примечания (note, exclusion_reason) — по-русски, коротко.

Таблицы набора данных:

{guide}

Правила области:

{rules}"""

STRUCTURE_TASK = """\
Проход A. Заполни publication и records.

- publication: title — точное название статьи; year — год публикации; DOI — без префикса https://doi.org/; authors — все авторы в порядке статьи через «; »; in_scope — относится ли статья к области; exclusion_reason — почему статья вне области, только при in_scope=false.
- records: все записи каждой таблицы, кроме упоминаний значений. Собери всё, что сравнивается в таблицах, на графиках и в тексте, и все условия, при которых получены значения. От этого зависит привязка значений во втором проходе.
- Если in_scope=false — все списки в records пустые."""

TABLES_TASK = """\
Перечисли все таблицы статьи, в ячейках которых есть значения для mentions, включая таблицы сравнения с другими работами. Таблицы, где только составы, условия или параметры без таких значений, не включай.

- table — обозначение таблицы ровно как в тексте статьи («Table 1», «TABLE II»). Таблицы, которых нет в тексте страниц (только в supplementary), не включай.
- page — страница, на которой стоят строки таблицы.
- columns — заголовки всех столбцов слева направо, как в статье, включая столбец с подписями строк и столбцы без показателей. Порядок важен: по нему числа строки сопоставляются со столбцами.
- rows — подписи всех строк с данными сверху вниз, как в первой ячейке строки. Ни одной строки не пропускай."""

MENTIONS_TASK = """\
Проход B. Найди все упоминания значений во всей статье и привяжи их к записям прохода A (результат прохода A — в конце).

- Каждое упоминание — отдельный элемент mentions: аннотация, текст, каждая ячейка со значением в каждой таблице, подписи к рисункам, числа, подписанные на рисунке. Повторы одного значения в разных местах не схлопывай: каждое место — свой элемент.
- Таблицы выписывай целиком: каждую строку и каждый столбец с показателем. Не останавливайся на первых строках.
{rules}"""

TABLE_TASK = """\
Проход B, таблица {table} на странице {page}. Выпиши каждую ячейку этой таблицы со значением — только этой таблицы — и привяжи к записям прохода A (результат прохода A — в конце).

- Столбцы слева направо: {columns}.
- Текст страницы {page} повторён в конце. Найди в нём строку заголовка таблицы: порядок столбцов бери из неё, а если он расходится со списком выше — верь тексту страницы.
- В тексте страницы строка таблицы — это подпись и числа подряд в порядке этих столбцов. Сопоставляй число со столбцом по позиции в строке, а не по смыслу. Прочерк занимает свою позицию; пустая ячейка в тексте не видна — если чисел меньше, чем столбцов, найди пропуск по смыслу соседних строк.
- Строки сверху вниз ({row_count}): {rows}.
- В каждой строке — каждый столбец с показателем, в котором есть число. Не останавливайся, пока не пройдёшь все строки до последней.
- table — «{table}», page — {page}, figure — рисунок, если таблица к нему относится.
{rules}"""

CITED_TASK = """\
Проход B, значения из чужих работ. Найди во всей статье числа, которые авторы приводят из других работ: во введении, обсуждении и выводах, при сравнении с литературой, рекорды и теоретические пределы, строки таблиц со ссылками на другие работы. Собственные результаты авторов не выписывай.

- Каждое такое число — отдельный элемент mentions, origin — то значение из списка «{origins}», которое означает чужую работу; cited — ссылка как в статье («[12]», «[19–21]», «Bai et al., 2018»), а если ссылки рядом нет — null; refs — null.
- Пройди каждый абзац введения и обсуждения: такие числа легко пропустить.
{rules}"""

MENTION_RULES = """\
- Если в одной фразе несколько чисел («10% and 3.5%», «2.9% and 1.3%, respectively»), каждое число — отдельный элемент со своей привязкой и той же цитатой.
- page — страница, на которой это число напечатано в тексте страниц (номер из маркера «=== Страница N ===» над ним). Не страница, где на таблицу или рисунок ссылаются. Для таблицы — страница, где стоят её строки.
- section — заголовок раздела как в статье; table — обозначение таблицы как в статье («Table 1»), если значение из таблицы; figure — рисунок, к которому относится значение.
- quote — дословный кусок текста страницы page с самим числом, 4–15 слов, в пределах одной строки этого текста: те же символы, пробелы, лигатуры и знаки минуса. Если фраза переносится на следующую строку, обрывай цитату в конце строки. Не склеивай переносы, не исправляй слова, без многоточий.{row_label}
- metric — название показателя.{metrics}
- raw_value — число ровно как напечатано в статье, со всеми цифрами и знаками после запятой, без округления, без единицы и без ±; raw_unit — единица рядом с числом как в статье; unit — только если единицы рядом нет, но она ясна из шкалы или заголовка (доля без единицы — fraction), иначе null.
- origin — {origins}. Числа из чужих работ — введение, обсуждение, сравнение с литературой, рекорды, а также строки таблиц с результатами других работ (в строке или подписи стоит ссылка: «[26]», «Ref. 12») — origin=cited, cited — ссылка как в статье («[12]», «Bai et al., 2018»), refs — null.
- status — {statuses}. Значение есть только в supplementary или только кривой без подписанного числа — raw_value=null, quote — фраза статьи, которая на него ссылается.
- refs — key записей прохода A, к которым относится значение.
- Не выписывай производные числа, если правила области не говорят иначе: разности и приросты («improves Voc of 0.127 V», «increased by 3.6%», «enhancement of 25%»), отношения, проценты изменения."""

ROW_LABEL_RULE = " Для значения из таблицы quote пиши ровно так: «Table N, строка <подпись строки>» — обозначение таблицы как в статье, слово «строка» по-русски, подпись строки как в первой ячейке строки, без столбца."
METRICS_RULE = " Оцениваемые показатели называй ровно так: {names}. Имя оцениваемого показателя ставь, только если в статье именно эта величина; другие величины, даже похожие по смыслу, называй как в статье. У оцениваемого показателя своя целевая единица (список в разделе mentions): если единица значения в неё не переводится — например, время, когда показатель в процентах, — это другая величина."


class PromptBook:
    def __init__(self, shape: ExtractionShape):
        profile = shape.profile
        self._shape = shape
        self.system = SYSTEM.format(
            title=profile.title,
            scope=profile.scope,
            example_key=f"{shape.records[0].table.name}1, {shape.records[0].table.name}2" if shape.records else "item1",
            guide=self._guide(),
            rules=profile.rules.strip(),
        )
        self.structure_task = STRUCTURE_TASK
        self.tables_task = TABLES_TASK
        self.mention_rules = MENTION_RULES.format(
            row_label=ROW_LABEL_RULE if profile.table_fragment == ROW_LABEL else "",
            metrics=METRICS_RULE.format(names=", ".join(profile.values.metrics)) if profile.values.metrics else "",
            origins=self._choices("origin"),
            statuses=self._choices("status"),
        )
        self.version = f"{profile.name}-{ENGINE_VERSION}-{self._digest()}"

    def structure_request(self, article: Article) -> str:
        return f"{article_text(article)}\n\n{self.structure_task}"

    def tables_request(self, article: Article) -> str:
        return f"{article_text(article)}\n\n{self.tables_task}"

    def table_request(self, article: Article, structure: dict[str, Any], table: dict[str, Any]) -> str:
        task = TABLE_TASK.format(
            table=table["table"],
            page=table["page"],
            columns=" | ".join(table["columns"]),
            rows="; ".join(table["rows"]),
            row_count=len(table["rows"]),
            rules=self.mention_rules,
        )
        page = next((page.text for page in article.pages if page.number == table["page"]), "")
        return f"{self._with_structure(article, task, structure)}\n\nТекст страницы {table['page']}:\n{page}"

    def mentions_request(self, article: Article, structure: dict[str, Any]) -> str:
        task = MENTIONS_TASK.format(rules=self.mention_rules)
        return self._with_structure(article, task, structure)

    def cited_request(self, article: Article, structure: dict[str, Any]) -> str:
        task = CITED_TASK.format(origins=self._choices("origin"), rules=self.mention_rules)
        return self._with_structure(article, task, structure)

    @staticmethod
    def _with_structure(article: Article, task: str, structure: dict[str, Any]) -> str:
        passed = json.dumps(structure, ensure_ascii=False, indent=1)
        return f"{article_text(article)}\n\n{task}\n\nРезультат прохода A:\n{passed}"

    def _guide(self) -> str:
        shape = self._shape
        profile = shape.profile
        sections = [self._section(f"publication ({profile.publications.name})", profile.publications, shape.publication_columns, {})]
        sections += [self._section(f"records.{record.table.name}", record.table, record.fields, record.refs) for record in shape.records]
        mention = shape.mention
        header = f"mentions — упоминания значений ({mention.table.name})"
        renamed = [f"{key} — {column}" for key, column in mention.columns.items() if key != column]
        if renamed:
            header += f"\nКлючи упоминания и колонки {mention.table.name} в правилах области: {', '.join(renamed)}"
        if profile.values.metrics:
            units = ", ".join(f"{metric} ({unit})" for metric, unit in profile.values.metrics.items())
            header += f"\nОцениваемые показатели и целевые единицы: {units}"
        sections.append(self._section(header, mention.table, mention.fields, mention.refs))
        return "\n\n".join(sections)

    @staticmethod
    def _section(header: str, table: Table, fields: tuple[str, ...], refs: dict[str, str]) -> str:
        lines = [f"### {header}"]
        if fields:
            lines.append("fields:")
            lines += [f"- {describe_column(table, column)}" for column in fields if column != IN_SCOPE]
        if refs:
            lines.append("refs:")
            for column, target in refs.items():
                many = ", список key" if column in table.lists else ""
                hint = f" — {table.descriptions[column]}" if column in table.descriptions else ""
                lines.append(f"- {column} → {target}{many}{hint}")
        return "\n".join(lines)

    def _choices(self, key: str) -> str:
        values = self._shape.mention.vocabulary(key)
        return " | ".join(values) if values else "как в статье"

    def _digest(self) -> str:
        payload = json.dumps(
            [
                self.system, self.structure_task, self.tables_task, MENTIONS_TASK, TABLE_TASK, CITED_TASK, self.mention_rules,
                self._shape.structure_schema(), self._shape.tables_schema(), self._shape.mentions_schema(),
            ],
            ensure_ascii=False,
        )
        return hashlib.sha256(payload.encode()).hexdigest()[:8]


def describe_column(table: Table, column: str) -> str:
    parts = [column]
    if column in table.vocabularies and column not in table.unit_columns:
        parts.append(f": {' | '.join(table.vocabularies[column])}")
    if column in table.descriptions:
        parts.append(f" — {table.descriptions[column]}")
    return "".join(parts)


def article_text(article: Article) -> str:
    pages = "\n\n".join(f"=== Страница {page.number} ===\n{page.text}" for page in article.pages)
    return f"Статья {article.publication_id}. Текст страниц:\n\n{pages}"
