"""Java 本体 DTO 的键格式转换。"""

from __future__ import annotations

import uuid
from enum import Enum
from typing import Any


def snake_keys(value: Any) -> Any:
    if isinstance(value, list):
        return [snake_keys(item) for item in value]
    if not isinstance(value, dict):
        return value
    return {_snake_key(str(key)): snake_keys(item) for key, item in value.items()}


def _snake_key(key: str) -> str:
    chars: list[str] = []
    for index, char in enumerate(key):
        if char.isupper() and index and (key[index - 1].islower() or key[index - 1].isdigit()):
            chars.append("_")
        chars.append(char.lower())
    return "".join(chars)


def camel_keys(value: Any) -> Any:
    if isinstance(value, uuid.UUID):
        return str(value)
    if isinstance(value, Enum):
        return value.value
    if isinstance(value, list):
        return [camel_keys(item) for item in value]
    if not isinstance(value, dict):
        return value
    return {_camel_key(str(key)): camel_keys(item) for key, item in value.items()}


def _camel_key(key: str) -> str:
    head, *tail = key.split("_")
    return head + "".join(part[:1].upper() + part[1:] for part in tail)


def list_from(data: Any, *keys: str) -> list[dict]:
    if isinstance(data, list):
        return [item for item in data if isinstance(item, dict)]
    if isinstance(data, dict):
        for key in keys:
            items = data.get(key)
            if isinstance(items, list):
                return [item for item in items if isinstance(item, dict)]
    return []


__all__ = ["camel_keys", "list_from", "snake_keys"]
