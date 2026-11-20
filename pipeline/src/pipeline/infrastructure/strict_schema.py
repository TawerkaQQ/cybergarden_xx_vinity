from typing import Any

from pydantic import BaseModel

BLANK_VALUES = ("", 0, None)
NULLABLE_BOOLEAN = {"type": "string", "enum": ["true", "false", ""]}


class StrictSchema:
    def __init__(self, model: type[BaseModel]):
        source = model.model_json_schema()
        self._definitions = source.pop("$defs", {})
        self._root = source
        self.json_schema = {
            **self._strict(self._root),
            "$defs": {name: self._strict(node) for name, node in self._definitions.items()},
        }

    def decode(self, payload: Any) -> Any:
        return self._decode(payload, self._root)

    def _strict(self, node: dict) -> dict:
        if "$ref" in node:
            return {"$ref": node["$ref"]}
        if "anyOf" in node:
            return self._strict_nullable(self._non_null(node))
        if node.get("type") == "object":
            properties = {name: self._strict(child) for name, child in node["properties"].items()}
            return {
                "type": "object",
                "properties": properties,
                "required": list(properties),
                "additionalProperties": False,
            }
        if node.get("type") == "array":
            return {"type": "array", "items": self._strict(node["items"])}
        return {key: value for key, value in node.items() if key in ("type", "enum", "const")}

    def _strict_nullable(self, node: dict) -> dict:
        strict = self._strict(node)
        if strict.get("type") == "boolean":
            return NULLABLE_BOOLEAN
        if "enum" in strict:
            return {**strict, "enum": [*strict["enum"], ""]}
        return strict

    def _decode(self, value: Any, node: dict) -> Any:
        node = self._resolve(node)
        if "anyOf" in node:
            return self._decode_nullable(value, self._resolve(self._non_null(node)))
        if node.get("type") == "object":
            return {name: self._decode(value[name], child) for name, child in node["properties"].items()}
        if node.get("type") == "array":
            return [self._decode(item, node["items"]) for item in value]
        return value

    def _decode_nullable(self, value: Any, node: dict) -> Any:
        if value in BLANK_VALUES:
            return None
        if node.get("type") == "boolean":
            return value == "true"
        decoded = self._decode(value, node)
        if node.get("type") == "object" and all(item in BLANK_VALUES for item in decoded.values()):
            return None
        return decoded

    def _resolve(self, node: dict) -> dict:
        if "$ref" in node:
            return self._definitions[node["$ref"].rsplit("/", 1)[-1]]
        return node

    @staticmethod
    def _non_null(node: dict) -> dict:
        return next(option for option in node["anyOf"] if option.get("type") != "null")
