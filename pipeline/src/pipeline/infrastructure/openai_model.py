import json
from typing import Any

import openai

RETRY_TEMPERATURE_STEP = 0.3


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
                return self._request(system, user, schema, attempt * RETRY_TEMPERATURE_STEP)
            except (ModelResponseError, json.JSONDecodeError) as error:
                failures.append(f"{type(error).__name__}: {error}")
        raise ModelResponseError(" | ".join(failures))

    def _request(self, system: str, user: str, schema: dict[str, Any], temperature: float) -> dict[str, Any]:
        response = self._client.chat.completions.create(
            model=self._model,
            messages=[{"role": "system", "content": system}, {"role": "user", "content": user}],
            temperature=temperature,
            max_tokens=self._max_tokens,
            response_format={
                "type": "json_schema",
                "json_schema": {"name": "extraction", "schema": schema, "strict": True},
            },
            extra_body={"chat_template_kwargs": {"enable_thinking": self._thinking}},
        )
        choice = response.choices[0]
        if choice.finish_reason != "stop":
            raise ModelResponseError(f"finish_reason={choice.finish_reason}")
        return json.loads(choice.message.content)
