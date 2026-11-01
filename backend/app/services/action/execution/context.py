"""行动执行上下文：归属、发布状态、定义快照与哈希。"""

from __future__ import annotations

import hashlib
import json
import uuid
from dataclasses import dataclass
from typing import Any

from sqlalchemy.orm import Session

from app.services.action import presentation
from app.services.action.detail import ActionDetail
from app.services.action.execution.errors import ActionExecutionError
from app.services.action.validation import unpublished_elements
from app.services.graph import core as graph


@dataclass(frozen=True)
class ActionContext:
    detail: ActionDetail
    graph_id: uuid.UUID
    owner_id: uuid.UUID
    conversation_id: uuid.UUID | None
    snapshot: dict[str, Any]
    definition_hash: str

    @property
    def action(self):
        return self.detail.action


def load_action_context(
    session: Session,
    *,
    graph_id: uuid.UUID,
    action_type_id: uuid.UUID,
    owner_id: uuid.UUID,
    conversation_id: uuid.UUID | None = None,
) -> ActionContext:
    graph_row = graph.get_graph(session, graph_id, owner_id=owner_id)
    if graph_row.published_at is None:
        raise ActionExecutionError("ACTION_NOT_PUBLISHED", "graph has not been published")
    try:
        detail = presentation.load_action_detail_in_graph(
            session,
            action_type_id,
            owner_id=owner_id,
            graph_id=graph_id,
        )
    except presentation.ActionNotInGraphError as exc:
        raise ActionExecutionError("ACTION_NOT_IN_GRAPH", str(exc)) from exc
    unpublished = unpublished_elements(session, detail)
    if unpublished:
        raise ActionExecutionError(
            "ACTION_NOT_EXECUTABLE",
            "action definition is not fully confirmed",
            details={"elements": unpublished},
        )
    snapshot = presentation.action_detail_to_dict(detail, session)
    canonical = json.dumps(
        snapshot,
        sort_keys=True,
        ensure_ascii=False,
        separators=(",", ":"),
        default=str,
    )
    return ActionContext(
        detail=detail,
        graph_id=graph_id,
        owner_id=owner_id,
        conversation_id=conversation_id,
        snapshot=snapshot,
        definition_hash=hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
    )
