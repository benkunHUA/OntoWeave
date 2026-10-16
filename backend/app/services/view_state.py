from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.models import ViewState
from app.schemas.view_state import ViewSpec
from app.services import graph


def get_view_state(
    session: Session,
    *,
    graph_id: uuid.UUID,
    user_id: uuid.UUID,
    owner_id: uuid.UUID | None = None,
) -> ViewState | None:
    graph.get_graph(session, graph_id, owner_id=owner_id)
    return session.get(ViewState, (graph_id, user_id))


def upsert_view_state(
    session: Session,
    *,
    graph_id: uuid.UUID,
    user_id: uuid.UUID,
    spec: dict,
    owner_id: uuid.UUID | None = None,
) -> ViewState:
    """视图状态是整体读写的 JSONB，名称始终镜像图名称。

    写入前按 ViewSpec 归一：坏形状在这里就挡下——绘图子智能体的工具和前端画布
    都走这条路，是唯一能同时管住两边的落库点。
    """
    graph_row = graph.get_graph(session, graph_id, owner_id=owner_id)
    spec = ViewSpec.normalize(spec)
    vs = session.get(ViewState, (graph_id, user_id))
    if vs is None:
        vs = ViewState(graph_id=graph_id, user_id=user_id, name=graph_row.name, spec=spec)
        session.add(vs)
    else:
        vs.name = graph_row.name
        vs.spec = spec
    session.flush()
    return vs
