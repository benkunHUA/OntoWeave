"""生成 edit-set 请求、调用 preview、校验并构造确认载荷。"""

from __future__ import annotations

import hashlib
import json
import uuid
from dataclasses import dataclass
from typing import Any

from sqlalchemy.orm import Session

from app.services.action.execution.confirmation import build_confirmation
from app.services.action.execution.context import ActionContext
from app.services.action.execution.errors import ActionExecutionError
from app.services.action.execution.parameters import ResolvedParameters
from app.services.action.execution.planner_types import EditSetDraft


@dataclass(frozen=True)
class ActionPreview:
    request: dict[str, Any]
    preview: dict[str, Any]
    expected: dict[str, Any]
    preview_hash: str
    confirmation: dict[str, Any]


def preview_action(
    session: Session,
    context: ActionContext,
    parameters: ResolvedParameters,
    draft: EditSetDraft,
    *,
    execution_id: uuid.UUID,
    idempotency_key: str,
    client: Any,
    expected: dict[str, Any] | None = None,
    previous_hash: str | None = None,
) -> ActionPreview:
    request = build_edit_set_request(
        context,
        parameters,
        draft,
        execution_id=execution_id,
        idempotency_key=idempotency_key,
        expected=expected,
    )
    preview = client.preview_edit_set(request)
    conflicts = _conflicts(preview)
    if conflicts or not preview.get("applicable"):
        code = "PREVIEW_STALE" if previous_hash else "PREVIEW_CONFLICT"
        raise ActionExecutionError(
            code,
            "edit set preview is not applicable" if conflicts else "edit set preview is stale",
            details={"conflicts": conflicts, "previous_hash": previous_hash},
        )

    next_expected = expected_from_preview(preview)
    final_request = {**request, "expected": next_expected}
    preview_hash = hash_preview(final_request, preview)
    if previous_hash is not None and preview_hash != previous_hash:
        raise ActionExecutionError(
            "PREVIEW_STALE",
            "edit set preview changed after confirmation",
            details={
                "previous_hash": previous_hash,
                "current_hash": preview_hash,
            },
        )
    return ActionPreview(
        request=final_request,
        preview=preview,
        expected=next_expected,
        preview_hash=preview_hash,
        confirmation=build_confirmation(
            session,
            context,
            preview,
            preview_hash=preview_hash,
            execution_id=execution_id,
            expected=next_expected,
            generated_values=draft.generated_values,
        ),
    )


def build_edit_set_request(
    context: ActionContext,
    parameters: ResolvedParameters,
    draft: EditSetDraft,
    *,
    execution_id: uuid.UUID,
    idempotency_key: str,
    expected: dict[str, Any] | None = None,
) -> dict[str, Any]:
    return {
        "execution_id": execution_id,
        "owner_id": context.owner_id,
        "conversation_id": context.conversation_id,
        "graph_id": context.graph_id,
        "action_type_id": context.action.id,
        "action_definition_hash": context.definition_hash,
        "action_snapshot": context.snapshot,
        "parameters": parameters.normalized,
        "idempotency_key": idempotency_key,
        "expected": expected or {"objects": [], "relations": []},
        **_draft_payload(draft),
    }


def expected_from_preview(preview: dict[str, Any]) -> dict[str, Any]:
    objects: list[dict[str, Any]] = []
    for change in _changes(preview.get("objects")):
        if change.get("kind") == "OBJECT_CREATE":
            continue
        before = change.get("before")
        if not isinstance(before, dict) or before.get("version") is None:
            continue
        objects.append(
            {
                "object_type_id": change.get("object_type_id"),
                "primary_key": change.get("primary_key"),
                "version": before["version"],
            }
        )
    relations = [
        {
            "relation_id": change.get("relation_id"),
            "from_primary_key": change.get("from_primary_key"),
            "to_primary_key": change.get("to_primary_key"),
            "version": change.get("before_version", 0),
        }
        for change in _changes(preview.get("relations"))
    ]
    return {"objects": objects, "relations": relations}


def hash_preview(request: dict[str, Any], preview: dict[str, Any]) -> str:
    basis = {
        "action_definition_hash": request["action_definition_hash"],
        "parameters": request["parameters"],
        "object_creates": request["object_creates"],
        "object_updates": request["object_updates"],
        "object_deletes": request["object_deletes"],
        "relation_creates": request["relation_creates"],
        "relation_deletes": request["relation_deletes"],
        "preview": {
            "objects": _changes(preview.get("objects")),
            "relations": _changes(preview.get("relations")),
        },
    }
    canonical = json.dumps(_jsonable(basis), sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def _draft_payload(draft: EditSetDraft) -> dict[str, Any]:
    return {
        "object_creates": [
            {
                "object_type_id": item.object_type_id,
                "primary_key": item.primary_key,
                "values": item.values,
            }
            for item in draft.object_creates
        ],
        "object_updates": [
            {
                "object_type_id": item.object_type_id,
                "primary_key": item.primary_key,
                "set": item.set_values,
                "clear": list(item.clear),
            }
            for item in draft.object_updates
        ],
        "object_deletes": [
            {"object_type_id": item.object_type_id, "primary_key": item.primary_key}
            for item in draft.object_deletes
        ],
        "relation_creates": [_relation_payload(item) for item in draft.relation_creates],
        "relation_deletes": [_relation_payload(item) for item in draft.relation_deletes],
    }


def _relation_payload(item: Any) -> dict[str, Any]:
    return {
        "relation_id": item.relation_id,
        "from_primary_key": item.from_primary_key,
        "to_primary_key": item.to_primary_key,
    }


def _conflicts(preview: dict[str, Any]) -> list[dict[str, Any]]:
    return _changes(preview.get("conflicts"))


def _changes(value: Any) -> list[dict[str, Any]]:
    return [item for item in value if isinstance(item, dict)] if isinstance(value, list) else []


def _jsonable(value: Any) -> Any:
    if isinstance(value, uuid.UUID):
        return str(value)
    if isinstance(value, dict):
        return {str(key): _jsonable(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_jsonable(item) for item in value]
    if isinstance(value, tuple):
        return [_jsonable(item) for item in value]
    return value


__all__ = [
    "ActionPreview",
    "build_edit_set_request",
    "expected_from_preview",
    "hash_preview",
    "preview_action",
]
