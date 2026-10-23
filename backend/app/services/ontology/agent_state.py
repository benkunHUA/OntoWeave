"""映射 Agent 每轮加载的状态。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.services.ontology.bindings import list_runs, safe_binding
from app.services.ontology.common import require_subject
from app.services.ontology.definitions import get_subject_definition
from app.services.ontology.relation_sync import sync_subject_relations_best_effort


def load_agent_state(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
) -> dict[str, Any]:
    """每轮重新读取资源、绑定、草稿与最近 run；失败时保留可解释的 load_error。"""
    kind = require_subject(session, owner_id, subject_kind, subject_id)
    definition = get_subject_definition(
        session, owner_id=owner_id, subject_kind=kind, subject_id=subject_id
    )
    sync_subject_relations_best_effort(
        session, client, owner_id=owner_id, subject_kind=kind, subject_id=subject_id
    )
    try:
        binding = safe_binding(client, owner_id=owner_id, subject_kind=kind, subject_id=subject_id)
        runs = list_runs(
            session, client, owner_id=owner_id, subject_kind=kind, subject_id=subject_id, limit=1
        )
        return {
            "subject_kind": kind,
            "subject_id": subject_id,
            "definition": definition,
            "binding": binding,
            "mapping": binding.get("draft_mapping") if binding else None,
            "latest_run": runs[0] if runs else (binding.get("recent_run") if binding else None),
            "load_error": None,
        }
    except Exception as exc:  # 数据服务失败时仍允许 Agent 解释现状，不阻塞对话。
        return {
            "subject_kind": kind,
            "subject_id": subject_id,
            "definition": definition,
            "binding": None,
            "mapping": None,
            "latest_run": None,
            "load_error": str(exc),
        }


__all__ = ["load_agent_state"]
