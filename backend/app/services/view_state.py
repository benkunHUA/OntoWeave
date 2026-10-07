from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.models import ViewState
from app.services import graph


def get_view_state(
    session: Session, *, graph_id: uuid.UUID, user_id: uuid.UUID
) -> ViewState | None:
    return session.get(ViewState, (graph_id, user_id))


def upsert_view_state(
    session: Session,
    *,
    graph_id: uuid.UUID,
    user_id: uuid.UUID,
    name: str | None,
    spec: dict,
) -> ViewState:
    """视图状态是整体读写的 JSONB，用覆盖语义最简单也最不容易写错。"""
    graph.get_graph(session, graph_id)
    vs = session.get(ViewState, (graph_id, user_id))
    if vs is None:
        vs = ViewState(graph_id=graph_id, user_id=user_id, name=name, spec=spec)
        session.add(vs)
    else:
        vs.name = name
        vs.spec = spec
    session.flush()
    return vs
