from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.deps import get_current_user_id, get_session
from app.schemas.view_state import ViewStateRead, ViewStateWrite
from app.services import view_state

router = APIRouter(tags=["view-state"])


@router.get("/graphs/{graph_id}/view-state", response_model=ViewStateRead)
def get_view_state(graph_id: uuid.UUID, session: Session = Depends(get_session)) -> ViewStateRead:
    vs = view_state.get_view_state(session, graph_id=graph_id, user_id=get_current_user_id())
    if vs is None:
        return ViewStateRead(graph_id=graph_id, name=None, spec={})
    return ViewStateRead(graph_id=vs.graph_id, name=vs.name, spec=vs.spec)


@router.put("/graphs/{graph_id}/view-state", response_model=ViewStateRead)
def put_view_state(
    graph_id: uuid.UUID,
    payload: ViewStateWrite,
    session: Session = Depends(get_session),
) -> ViewStateRead:
    vs = view_state.upsert_view_state(
        session,
        graph_id=graph_id,
        user_id=get_current_user_id(),
        name=payload.name,
        spec=payload.spec,
    )
    return ViewStateRead(graph_id=vs.graph_id, name=vs.name, spec=vs.spec)
