from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.deps import get_session
from app.schemas.changeset import (
    ChangesetDetail,
    ChangesetOpCreate,
    ChangesetOpRead,
    ChangesetRead,
)
from app.services import changeset

router = APIRouter(tags=["changesets"])


@router.post("/graphs/{graph_id}/changesets", response_model=ChangesetRead, status_code=201)
def create_changeset(graph_id: uuid.UUID, session: Session = Depends(get_session)) -> ChangesetRead:
    cs = changeset.create_changeset(session, graph_id=graph_id)
    return ChangesetRead.model_validate(cs)


@router.get("/graphs/{graph_id}/changesets", response_model=list[ChangesetRead])
def list_changesets(
    graph_id: uuid.UUID,
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    session: Session = Depends(get_session),
) -> list[ChangesetRead]:
    items = changeset.list_changesets(session, graph_id=graph_id, limit=limit, offset=offset)
    return [ChangesetRead.model_validate(item) for item in items]


@router.get("/changesets/{changeset_id}", response_model=ChangesetDetail)
def get_changeset(
    changeset_id: uuid.UUID, session: Session = Depends(get_session)
) -> ChangesetDetail:
    cs = changeset.get_changeset(session, changeset_id)
    ops = [ChangesetOpRead.model_validate(op) for op in changeset.list_ops(session, cs.id)]
    return ChangesetDetail(id=cs.id, graph_id=cs.graph_id, status=cs.status, ops=ops)


@router.post("/changesets/{changeset_id}/ops", response_model=ChangesetOpRead, status_code=201)
def record_op(
    changeset_id: uuid.UUID,
    payload: ChangesetOpCreate,
    session: Session = Depends(get_session),
) -> ChangesetOpRead:
    op = changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=payload.op,
        target_id=payload.target_id,
        payload=payload.payload,
    )
    return ChangesetOpRead.model_validate(op)


@router.post("/changesets/{changeset_id}/confirm", response_model=ChangesetRead)
def confirm_changeset(
    changeset_id: uuid.UUID, session: Session = Depends(get_session)
) -> ChangesetRead:
    return ChangesetRead.model_validate(changeset.confirm_changeset(session, changeset_id))


@router.post("/changesets/{changeset_id}/reject", response_model=ChangesetRead)
def reject_changeset(
    changeset_id: uuid.UUID, session: Session = Depends(get_session)
) -> ChangesetRead:
    return ChangesetRead.model_validate(changeset.reject_changeset(session, changeset_id))
