"""把 apply 结果投影成 effective readback。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.services.action.execution.context import ActionContext
from app.services.action.execution.object_values import effective_properties
from app.services.action.presentation.names import Names


def build_readback(
    session: Session,
    context: ActionContext,
    result: dict[str, Any],
) -> dict[str, Any]:
    names = Names(session, context.detail.parameters)
    return {
        "execution_id": result.get("execution_id"),
        "objects": [_object_result(item, names) for item in _items(result.get("objects"))],
        "relations": [_relation_result(item, names) for item in _items(result.get("relations"))],
    }


def _object_result(item: dict[str, Any], names: Names) -> dict[str, Any]:
    object_type_id = _uuid(item.get("object_type_id"))
    values = effective_properties({"properties": item.get("properties")})
    fields = [
        {
            "property_id": key,
            "name": names.property_name(_uuid(key)),
            "value": value,
        }
        for key, value in sorted(values.items())
    ]
    return {
        "object_type_id": str(object_type_id) if object_type_id else None,
        "object_type_name": names.object_type_name(object_type_id),
        "primary_key": item.get("primary_key"),
        "exists": bool(item.get("exists")),
        "title": item.get("title"),
        "version": item.get("version"),
        "fields": fields,
    }


def _relation_result(item: dict[str, Any], names: Names) -> dict[str, Any]:
    relation_id = _uuid(item.get("relation_id"))
    from_type_id = _uuid(item.get("from_object_type_id"))
    to_type_id = _uuid(item.get("to_object_type_id"))
    return {
        "relation_id": str(relation_id) if relation_id else None,
        "relation_name": names.relation_name(relation_id),
        "from_object_type_id": str(from_type_id) if from_type_id else None,
        "from_object_type_name": names.object_type_name(from_type_id),
        "from_primary_key": item.get("from_primary_key"),
        "to_object_type_id": str(to_type_id) if to_type_id else None,
        "to_object_type_name": names.object_type_name(to_type_id),
        "to_primary_key": item.get("to_primary_key"),
        "exists": bool(item.get("exists")),
    }


def _items(value: Any) -> list[dict[str, Any]]:
    return [item for item in value if isinstance(item, dict)] if isinstance(value, list) else []


def _uuid(value: Any) -> uuid.UUID | None:
    if value is None:
        return None
    try:
        return uuid.UUID(str(value))
    except ValueError:
        return None


__all__ = ["build_readback"]
