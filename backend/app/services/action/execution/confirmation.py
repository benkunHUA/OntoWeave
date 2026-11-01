"""把 edit-set preview 投影成前端确认卡片载荷。"""

from __future__ import annotations

import uuid
from collections.abc import Mapping
from typing import Any

from sqlalchemy.orm import Session

from app.services.action.execution.context import ActionContext
from app.services.action.execution.object_values import effective_properties
from app.services.action.presentation.names import Names

CONFIRMATION_KIND = "action_confirmation"
CONFIRM_LABEL = "确认执行"
REJECT_OPTION = {"label": "取消", "answer": {"decision": "reject"}}


def build_confirmation(
    session: Session,
    context: ActionContext,
    preview: dict[str, Any],
    *,
    preview_hash: str,
    execution_id: uuid.UUID,
    expected: dict[str, Any],
    generated_values: Mapping[uuid.UUID, Any] | None = None,
) -> dict[str, Any]:
    names = Names(session, context.detail.parameters)
    objects = [_object_change(item, names) for item in _list(preview.get("objects"))]
    relations = [_relation_change(item, names) for item in _list(preview.get("relations"))]
    answer = {
        "decision": "approve",
        "preview_hash": preview_hash,
        "execution_id": str(execution_id),
        "expected": expected,
    }
    if generated_values:
        answer["generated_values"] = {
            str(assignment_id): value for assignment_id, value in generated_values.items()
        }
    return {
        "kind": CONFIRMATION_KIND,
        "title": f"执行「{context.action.name}」？",
        "summary": _summary(objects, relations),
        "action": {
            "id": str(context.action.id),
            "name": context.action.name,
            "description": context.action.description,
        },
        "preview_hash": preview_hash,
        "execution_id": str(execution_id),
        "object_changes": objects,
        "relation_changes": relations,
        "warnings": [],
        "options": [
            {
                "label": CONFIRM_LABEL,
                "answer": answer,
            },
            REJECT_OPTION,
        ],
    }


def _object_change(change: dict[str, Any], names: Names) -> dict[str, Any]:
    object_type_id = _uuid(change.get("object_type_id"))
    primary_key = str(change.get("primary_key") or "")
    before = change.get("before") if isinstance(change.get("before"), dict) else None
    after = change.get("after") if isinstance(change.get("after"), dict) else None
    state = after or before or {}
    return {
        "kind": str(change.get("kind") or ""),
        "object_type_id": str(object_type_id) if object_type_id else None,
        "object_type_name": names.object_type_name(object_type_id),
        "primary_key": primary_key,
        "title": str(state.get("title") or primary_key),
        "fields": _field_changes(before, after, names),
    }


def _field_changes(
    before: dict[str, Any] | None,
    after: dict[str, Any] | None,
    names: Names,
) -> list[dict[str, Any]]:
    before_values = effective_properties(before or {})
    after_values = effective_properties(after or {})
    fields: list[dict[str, Any]] = []
    for property_id in sorted(set(before_values) | set(after_values)):
        before_value = before_values.get(property_id)
        after_value = after_values.get(property_id)
        if before_value == after_value:
            continue
        parsed_id = _uuid(property_id)
        fields.append(
            {
                "property_id": str(parsed_id) if parsed_id else property_id,
                "name": names.property_name(parsed_id) if parsed_id else property_id,
                "before": before_value,
                "after": after_value,
            }
        )
    return fields


def _relation_change(change: dict[str, Any], names: Names) -> dict[str, Any]:
    relation_id = _uuid(change.get("relation_id"))
    from_type_id = _uuid(change.get("from_object_type_id"))
    to_type_id = _uuid(change.get("to_object_type_id"))
    return {
        "kind": str(change.get("kind") or ""),
        "relation_id": str(relation_id) if relation_id else None,
        "relation_name": names.relation_name(relation_id),
        "from_object_type_id": str(from_type_id) if from_type_id else None,
        "from_object_type_name": names.object_type_name(from_type_id),
        "from_primary_key": str(change.get("from_primary_key") or ""),
        "to_object_type_id": str(to_type_id) if to_type_id else None,
        "to_object_type_name": names.object_type_name(to_type_id),
        "to_primary_key": str(change.get("to_primary_key") or ""),
        "before_exists": bool(change.get("before_exists")),
        "after_exists": bool(change.get("after_exists")),
    }


def _summary(objects: list[dict[str, Any]], relations: list[dict[str, Any]]) -> str:
    parts = [
        _count(objects, "OBJECT_CREATE", "新增", "个对象"),
        _count(objects, "OBJECT_MODIFY", "修改", "个对象"),
        _count(objects, "OBJECT_DELETE", "删除", "个对象"),
        _count(relations, "RELATION_CREATE", "新增", "条关系"),
        _count(relations, "RELATION_DELETE", "删除", "条关系"),
    ]
    return "；".join(item for item in parts if item) or "无数据变更"


def _count(changes: list[dict[str, Any]], kind: str, verb: str, unit: str) -> str:
    total = sum(1 for item in changes if item["kind"] == kind)
    return f"{verb} {total} {unit}" if total else ""


def _list(value: Any) -> list[dict[str, Any]]:
    return [item for item in value if isinstance(item, dict)] if isinstance(value, list) else []


def _uuid(value: Any) -> uuid.UUID | None:
    if value is None:
        return None
    try:
        return uuid.UUID(str(value))
    except ValueError:
        return None


__all__ = ["CONFIRMATION_KIND", "CONFIRM_LABEL", "REJECT_OPTION", "build_confirmation"]
