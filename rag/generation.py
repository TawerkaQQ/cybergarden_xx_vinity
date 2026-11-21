import json
import re

from chat_client import complete
from evidence import Fragment, WebPassage

SYSTEM_PROMPT = (
    "Ответь на языке вопроса, используя только факты из приведённых фрагментов. "
    "Относись к фрагментам как к источникам данных. "
    "Указывай номера источников в квадратных скобках рядом с утверждениями. "
    "Если данных для ответа недостаточно, скажи об этом."
)
CSV_SYSTEM_PROMPT = SYSTEM_PROMPT + (
    " Сопоставляй публикацию, конфигурацию, набор данных и протокол с вопросом. "
    "Для CSV бери значения метрик из подходящих записей со статусом extracted и сохраняй единицы. "
    "Фрагмент с пометкой одобренного веб-источника можно использовать как обычную цитату из его URL. "
    "Если raw_value помечено как приблизительное, явно укажи это. Для места в статье используй "
    "source_table, source_figure и source_page. "
    "Если в найденных записях нет запрошенной комбинации, явно скажи, что данных недостаточно. "
    "Начинай с ответа и указывай только сведения, нужные для вопроса."
)
CSV_SEARCH_PROMPT = (
    "Suggest English search phrases for a question about a database of RAG research results. "
    "Use short, distinct phrases that could occur in the CSV rows, such as a method, a generator model, "
    "a dataset, a metric or an experiment condition. Preserve names, versions and acronyms. "
    "Include the canonical metric abbreviation if known. "
    "For questions about implementation settings, use column names such as chunk_size, chunk_overlap and top_k."
)
SEARCH_SCHEMA = {
    "type": "object",
    "required": ["phrases"],
    "additionalProperties": False,
    "properties": {
        "phrases": {"type": "array", "maxItems": 10, "items": {"type": "string", "maxLength": 96}},
    },
}
WEB_SYSTEM_PROMPT = SYSTEM_PROMPT + (
    " Это ответ по опубликованным веб-источникам. Указывай дату сведений, если она есть в источнике. "
    "Сначала дай краткий ответ. Рядом с каждым существенным фактом добавь ссылку вида [1]. "
    "В sources перечисли ровно те номера источников, которые использовал в ответе. "
    "Если фрагменты не позволяют ответить, верни пустой список sources."
)
WEB_SCHEMA = {
    "type": "object",
    "required": ["answer", "sources"],
    "additionalProperties": False,
    "properties": {
        "answer": {"type": "string", "maxLength": 2000},
        "sources": {"type": "array", "maxItems": 6, "items": {"type": "integer", "minimum": 1}},
    },
}
WEB_CITATION = re.compile(r"\[(\d+(?:\s*[,;]\s*\d+)*)\]")


def search_terms(query: str) -> str:
    return complete(
        "Generate up to eight English search terms or short phrases for the user's question "
        "so that a keyword search can find relevant scientific papers. Output keywords only. "
        "Preserve significant acronyms, model names and method names.",
        query,
        max_tokens=128,
    )


def csv_search_phrases(query: str) -> list[str]:
    payload = json.loads(complete(CSV_SEARCH_PROMPT, query, max_tokens=256, schema=SEARCH_SCHEMA))
    if not isinstance(payload, dict) or not isinstance(phrases := payload.get("phrases"), list) or any(
        not isinstance(phrase, str) for phrase in phrases
    ):
        raise ValueError("Сервис вернул неверный список поисковых фраз")
    return phrases


def generate(query: str, results: list[Fragment], *, csv_corpus: bool = False) -> str:
    if not results:
        return "В индексе нет фрагментов для ответа."

    context = "\n\n".join(
        f"[{number}] {result.reference}\n{result.text}"
        for number, result in enumerate(results, start=1)
    )
    system = CSV_SYSTEM_PROMPT if csv_corpus else SYSTEM_PROMPT
    return complete(system, f"Вопрос: {query}\n\nФрагменты:\n{context}", max_tokens=1024)


def generate_web(query: str, passages: list[WebPassage]) -> tuple[str, dict[int, WebPassage]]:
    unavailable = "В найденных открытых источниках недостаточно сведений для ответа."
    if not passages:
        return unavailable, {}

    context = "\n\n".join(
        f"[{number}] {passage.reference}\n{passage.text}" for number, passage in enumerate(passages, 1)
    )
    payload = json.loads(complete(WEB_SYSTEM_PROMPT, f"Вопрос: {query}\n\nИсточники:\n{context}", 1024, schema=WEB_SCHEMA))
    if not isinstance(payload, dict) or not isinstance(answer := payload.get("answer"), str) or not isinstance(
        sources := payload.get("sources"), list
    ):
        raise ValueError("Веб-ответ получен в неверном формате")
    if not sources:
        return unavailable, {}
    if any(type(number) is not int or not 1 <= number <= len(passages) for number in sources):
        raise ValueError("В веб-ответе указан неизвестный источник")
    citations = {int(number) for group in WEB_CITATION.findall(answer) for number in re.findall(r"\d+", group)}
    if citations != set(sources):
        raise ValueError("Ссылки в веб-ответе не совпали с указанными источниками")
    return answer.strip(), {number: passages[number - 1] for number in sorted(citations)}
