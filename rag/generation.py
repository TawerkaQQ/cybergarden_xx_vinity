from config import get_llm

PROMPT_TEMPLATE = """Ответь на вопрос, используя ТОЛЬКО контекст ниже.
Не добавляй информацию, которой нет в контексте. Если в контексте нет ответа - скажи об этом.
Контекст:
{context}

Вопрос: {query}
"""

def generate(query: str, results) -> str:
    context = "\n\n".join([r.page_content for r in results])
    prompt = PROMPT_TEMPLATE.format(context=context, query=query)
    return get_llm().invoke(prompt).content