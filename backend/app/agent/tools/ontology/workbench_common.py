"""映射工作台工具的共享解析、确认与绑定字段读取。"""

from __future__ import annotations

import json
from collections.abc import Callable
from typing import Any

import app.agent.tools.ontology.workbench_constants as workbench_constants


def parse_mapping(value: dict[str, Any] | str) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError as exc:
            raise ValueError("mapping 必须是 JSON 对象") from exc
        if isinstance(parsed, dict):
            return parsed
    raise ValueError("mapping 必须是 JSON 对象")


def is_confirmed(answer: Any) -> bool:
    if isinstance(answer, bool):
        return answer
    if isinstance(answer, dict):
        if answer.get("confirmed") is not None:
            return bool(answer["confirmed"])
        decision = str(answer.get("decision") or "").strip().lower()
        return decision in workbench_constants.CONFIRM_ANSWERS
    return str(answer or "").strip().lower() in workbench_constants.CONFIRM_ANSWERS


def confirm(interrupt_fn: Callable[[dict], Any], action: str, question: str) -> dict | None:
    answer = interrupt_fn(
        {
            "question": question,
            "options": ["确认", "取消"],
            "details": [],
            "confirmation": True,
            "action": action,
        }
    )
    return None if is_confirmed(answer) else workbench_constants.interrupted(action)


def mapping_value(row: dict[str, Any], snake: str, camel: str) -> Any:
    return row.get(snake, row.get(camel))


def binding_dataset(binding: dict[str, Any] | None) -> tuple[str | None, str | None]:
    if not binding:
        return None, None
    kind = mapping_value(binding, "draft_dataset_kind", "draftDatasetKind")
    identity = mapping_value(binding, "draft_dataset_id", "draftDatasetId")
    return (str(kind) if kind else None, str(identity) if identity else None)


def binding_mapping(binding: dict[str, Any] | None) -> dict[str, Any] | None:
    if not binding:
        return None
    mapping = mapping_value(binding, "draft_mapping", "draftMapping")
    return mapping if isinstance(mapping, dict) else None


def keys_changed(
    previous: dict[str, Any] | None, current: dict[str, Any]
) -> tuple[bool, dict[str, Any]]:
    old_primary = mapping_value(previous or {}, "primary_key_attribute_id", "primaryKeyAttributeId")
    old_title = mapping_value(previous or {}, "title_key_attribute_id", "titleKeyAttributeId")
    new_primary = mapping_value(current, "primary_key_attribute_id", "primaryKeyAttributeId")
    new_title = mapping_value(current, "title_key_attribute_id", "titleKeyAttributeId")
    changed = str(old_primary) != str(new_primary) or str(old_title) != str(new_title)
    return changed, {
        "previous_primary_key_attribute_id": old_primary,
        "previous_title_key_attribute_id": old_title,
        "primary_key_attribute_id": new_primary,
        "title_key_attribute_id": new_title,
    }


__all__ = [
    "binding_dataset",
    "binding_mapping",
    "confirm",
    "keys_changed",
    "mapping_value",
    "parse_mapping",
]
