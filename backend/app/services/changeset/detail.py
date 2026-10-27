"""改动集详情的读取形状。"""

from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.services.changeset.store import get_changeset, list_ops


def changeset_detail(
    session: Session, changeset_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> dict:
    cs = get_changeset(session, changeset_id, owner_id=owner_id)
    return {
        "id": cs.id,
        "graph_id": cs.graph_id,
        "status": cs.status,
        "ops": list_ops(session, changeset_id),
    }
