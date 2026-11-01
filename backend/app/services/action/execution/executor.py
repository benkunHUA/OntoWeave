"""行动执行编排：校验、预览、确认、重验、apply、readback。"""

from __future__ import annotations

import uuid
from collections.abc import Callable
from typing import Any

from sqlalchemy.orm import Session

from app.clients.data_service.ontology_client import EditConflict
from app.errors import DataServiceUnavailableError
from app.services.action.execution.context import ActionContext, load_action_context
from app.services.action.execution.criteria import evaluate_criteria
from app.services.action.execution.errors import ActionExecutionError
from app.services.action.execution.parameters import resolve_parameters
from app.services.action.execution.planner import plan_edit_set
from app.services.action.execution.preview import ActionPreview, preview_action
from app.services.action.execution.readback import build_readback

InterruptFn = Callable[[dict[str, Any]], Any]


def execute_action(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    conversation_id: uuid.UUID | None,
    graph_id: uuid.UUID,
    action_type_id: uuid.UUID,
    parameters: dict[str, Any] | None,
    tool_call_id: str,
    interrupt_fn: InterruptFn,
) -> dict[str, Any]:
    context = load_action_context(
        session,
        graph_id=graph_id,
        action_type_id=action_type_id,
        owner_id=owner_id,
        conversation_id=conversation_id,
    )
    resolved = resolve_parameters(context.detail, parameters, owner_id=owner_id, client=client)
    evaluate_criteria(context.detail, resolved, owner_id=owner_id, client=client)
    draft = plan_edit_set(
        session,
        context.detail,
        resolved,
        owner_id=owner_id,
        client=client,
    )
    execution_id, idempotency_key = execution_identity(
        owner_id=owner_id,
        conversation_id=conversation_id,
        tool_call_id=tool_call_id,
    )
    first = preview_action(
        session,
        context,
        resolved,
        draft,
        execution_id=execution_id,
        idempotency_key=idempotency_key,
        client=client,
    )
    answer = interrupt_fn(_interrupt_payload(first))
    approval = _approval(answer)
    if approval is None:
        return {
            "status": "CANCELLED",
            "action": _action_payload(context),
            "message": "用户取消执行，未写入任何数据",
        }
    confirmed_hash = _confirmed_hash(approval, execution_id=execution_id) or first.preview_hash
    confirmed_expected = _confirmed_expected(approval) or first.expected

    second = preview_action(
        session,
        context,
        resolved,
        draft,
        execution_id=execution_id,
        idempotency_key=idempotency_key,
        client=client,
        expected=confirmed_expected,
        previous_hash=confirmed_hash,
    )
    applied = _apply_with_retry(client, second.request)
    return {
        "status": "APPLIED",
        "execution_id": str(execution_id),
        "preview_hash": second.preview_hash,
        "action": _action_payload(context),
        "readback": build_readback(session, context, applied),
    }


def execution_identity(
    *,
    owner_id: uuid.UUID,
    conversation_id: uuid.UUID | None,
    tool_call_id: str,
) -> tuple[uuid.UUID, str]:
    if not tool_call_id.strip():
        raise ActionExecutionError("INVALID_ACTION_CALL", "action call is missing its tool_call_id")
    conversation_key = str(conversation_id or "none")
    idempotency_key = f"{conversation_key}:{tool_call_id}"
    seed = f"ontoweave:action-execution:{owner_id}:{idempotency_key}"
    return uuid.uuid5(uuid.NAMESPACE_URL, seed), idempotency_key


def _interrupt_payload(preview: ActionPreview) -> dict[str, Any]:
    confirmation = preview.confirmation
    return {
        "question": confirmation["title"],
        "options": [],
        "details": [{"label": "变更摘要", "value": confirmation["summary"]}],
        "confirmation": confirmation,
    }


def _approval(answer: Any) -> dict[str, Any] | None:
    if isinstance(answer, bool):
        return {} if answer else None
    if not isinstance(answer, dict):
        return None
    if answer.get("decision") is not None:
        return answer if str(answer["decision"]).strip().lower() == "approve" else None
    decisions = answer.get("decisions")
    if not isinstance(decisions, list) or not decisions:
        return None
    approved = all(
        isinstance(item, dict) and str(item.get("type") or "").strip().lower() == "approve"
        for item in decisions
    )
    return {} if approved else None


def _confirmed_hash(answer: dict[str, Any], *, execution_id: uuid.UUID) -> str | None:
    raw_execution_id = answer.get("execution_id")
    if raw_execution_id is not None and str(raw_execution_id) != str(execution_id):
        raise ActionExecutionError(
            "INVALID_CONFIRMATION",
            "confirmation does not belong to this execution",
        )
    value = answer.get("preview_hash")
    return value if isinstance(value, str) and value else None


def _confirmed_expected(answer: dict[str, Any]) -> dict[str, Any] | None:
    value = answer.get("expected")
    if not isinstance(value, dict):
        return None
    objects = value.get("objects")
    relations = value.get("relations")
    if not isinstance(objects, list) or not isinstance(relations, list):
        return None
    return value


def _apply_with_retry(client: Any, request: dict[str, Any]) -> dict[str, Any]:
    try:
        return client.apply_edit_set(request)
    except EditConflict as exc:
        raise ActionExecutionError(
            "EDIT_CONFLICT",
            "edit set changed before apply",
            details={"conflicts": exc.conflicts},
        ) from exc
    except DataServiceUnavailableError:
        pass
    try:
        return client.apply_edit_set(request)
    except EditConflict as exc:
        raise ActionExecutionError(
            "EDIT_CONFLICT",
            "edit set changed before apply",
            details={"conflicts": exc.conflicts},
        ) from exc
    except DataServiceUnavailableError as exc:
        raise ActionExecutionError(
            "UNCERTAIN",
            "apply outcome is uncertain; retry with the same idempotency key",
            details={"idempotency_key": request.get("idempotency_key")},
        ) from exc


def _action_payload(context: ActionContext) -> dict[str, Any]:
    return {
        "id": str(context.action.id),
        "name": context.action.name,
    }


__all__ = ["execute_action", "execution_identity"]
