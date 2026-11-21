import json
import time
from typing import Any

import openai

RETRY_TEMPERATURE_STEP = 0.3
NETWORK_PAUSES_SECONDS = (15, 30, 60, 120)


class ModelResponseError(Exception):
    pass


class OpenAiCompatibleModel:
    def __init__(self, client: openai.OpenAI, model: str, max_tokens: int, thinking: bool, attempts: int):
        self._client = client
        self._model = model
        self._max_tokens = max_tokens
        self._thinking = thinking
        self._attempts = attempts

    def complete(self, system: str, user: str, schema: dict[str, Any]) -> dict[str, Any]:
        failures = []
        for attempt in range(self._attempts):
            try:
                return self._reachable_request(system, user, schema, attempt * RETRY_TEMPERATURE_STEP)
            except (ModelResponseError, json.JSONDecodeError) as error:
                failures.append(f"{type(error).__name__}: {error}")
        raise ModelResponseError(" | ".join(failures))

    def _reachable_request(self, system: str, user: str, schema: dict[str, Any], temperature: float) -> dict[str, Any]:
        for pause in NETWORK_PAUSES_SECONDS:
            try:
                return self._request(system, user, schema, temperature)
            except openai.APIError as error:
                if not transient(error):
                    raise
                time.sleep(pause)
        return self._request(system, user, schema, temperature)

    def _request(self, system: str, user: str, schema: dict[str, Any], temperature: float) -> dict[str, Any]:
        stream = self._client.chat.completions.create(
            model=self._model,
            messages=[{"role": "system", "content": system}, {"role": "user", "content": user}],
            temperature=temperature,
            max_tokens=self._max_tokens,
            stream=True,
            response_format={
                "type": "json_schema",
                "json_schema": {"name": "extraction", "schema": schema, "strict": True},
            },
            extra_body={"chat_template_kwargs": {"enable_thinking": self._thinking}},
        )
        parts = []
        finish_reason = None
        for chunk in stream:
            for choice in chunk.choices:
                parts.append(choice.delta.content or "")
                finish_reason = choice.finish_reason or finish_reason
        if finish_reason != "stop":
            raise ModelResponseError(f"finish_reason={finish_reason}")
        return json.loads("".join(parts))


def transient(error: openai.APIError) -> bool:
    return not isinstance(error, openai.APIStatusError) or error.status_code >= 500
