import json
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from config import get_chat_settings


class ResponseError(ValueError):
    def __init__(self, message: str, status: int | None = None):
        super().__init__(message)
        self.status = status


class TruncatedResponse(ResponseError):
    pass


def complete(system: str, user: str, max_tokens: int, *, schema: dict | None = None) -> str:
    settings = get_chat_settings()
    payload = {
        **settings.options,
        "model": settings.profile,
        "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
        "max_tokens": max_tokens,
    }
    if schema is not None:
        payload["temperature"] = 0
        payload["response_format"] = {
            "type": "json_schema",
            "json_schema": {"name": "rag_result_groups", "strict": True, "schema": schema},
        }
    headers = {"Content-Type": "application/json"}
    if settings.token:
        headers["Authorization"] = f"Bearer {settings.token}"
    request = Request(settings.url, data=json.dumps(payload, ensure_ascii=False).encode(), headers=headers, method="POST")
    try:
        with urlopen(request, timeout=180) as response:
            result = json.load(response)
    except HTTPError as error:
        raise ResponseError(f"Сервис вернул HTTP {error.code}", status=error.code) from error
    except OSError as error:
        raise OSError("Не удалось получить ответ сервиса") from error
    except json.JSONDecodeError as error:
        raise ResponseError("Сервис вернул некорректный JSON") from error
    try:
        choice = result["choices"][0]
        content = choice["message"]["content"]
    except (KeyError, IndexError, TypeError) as error:
        raise ResponseError("Сервис вернул ответ без текста") from error
    if schema is not None and choice.get("finish_reason") == "length":
        try:
            if isinstance(content, str) and isinstance(json.loads(content), dict):
                return content.strip()
        except json.JSONDecodeError:
            pass
        raise TruncatedResponse("Ответ сервиса достиг ограничения на число токенов")
    if not isinstance(content, str) or not content.strip():
        raise ResponseError("Сервис вернул пустой ответ")
    return content.strip()
