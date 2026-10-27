from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy.orm import Session

from app.api import runtime_dependencies
from app.deps import get_current_user_id, get_session
from app.schemas.changeset import (
    ChangesetDetail,
    ChangesetOpCreate,
    ChangesetOpRead,
    ChangesetRead,
    GraphEditsCreate,
)
from app.services import changeset, element_edit
from app.services.changeset import review as changeset_review

router = APIRouter(tags=["changesets"])


@router.post("/graphs/{graph_id}/changesets", response_model=ChangesetRead, status_code=201)
def create_changeset(graph_id: uuid.UUID, session: Session = Depends(get_session)) -> ChangesetRead:
    cs = changeset.create_changeset(session, graph_id=graph_id, owner_id=get_current_user_id())
    return ChangesetRead.model_validate(cs)


@router.get("/graphs/{graph_id}/changesets", response_model=list[ChangesetRead])
def list_changesets(
    graph_id: uuid.UUID,
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    session: Session = Depends(get_session),
) -> list[ChangesetRead]:
    items = changeset.list_changesets(
        session,
        graph_id=graph_id,
        owner_id=get_current_user_id(),
        limit=limit,
        offset=offset,
    )
    return [ChangesetRead.model_validate(item) for item in items]


@router.get("/changesets/{changeset_id}", response_model=ChangesetDetail)
def get_changeset(
    changeset_id: uuid.UUID, session: Session = Depends(get_session)
) -> ChangesetDetail:
    return ChangesetDetail.model_validate(
        changeset.changeset_detail(session, changeset_id, owner_id=get_current_user_id())
    )


@router.post("/graphs/{graph_id}/edits", response_model=ChangesetDetail)
def edit_graph_elements(
    graph_id: uuid.UUID,
    payload: GraphEditsCreate,
    session: Session = Depends(get_session),
) -> ChangesetDetail:
    """抽屉里的表单保存：把对象的编辑登记进未定稿的改动集。

    支持新建对象类型、复用已有类型、新建关系、改名/改类型、新增字段、删除字段——
    与模型走工具写入时是同一套 service 规则。没有改动集就顺手建一个，界面不必先手动
    开一个改动集。返回改动集详情，前端据此刷新「改动待确认」那一条。
    """
    cs = element_edit.apply_edits(
        session,
        graph_id=graph_id,
        ops=element_edit.edits_from_payload(payload),
        owner_id=get_current_user_id(),
        embedder=runtime_dependencies.request_embedder(),
        name_generator=runtime_dependencies.request_name_generator(),
    )
    return ChangesetDetail.model_validate(
        changeset.changeset_detail(session, cs.id, owner_id=get_current_user_id())
    )


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
        owner_id=get_current_user_id(),
    )
    return ChangesetOpRead.model_validate(op)


@router.post("/changesets/{changeset_id}/confirm", response_model=ChangesetRead)
def confirm_changeset(
    changeset_id: uuid.UUID, request: Request, session: Session = Depends(get_session)
) -> ChangesetRead:
    # 走 review 层而不是直接调 changeset 服务：确认/撤销都要在会话里留一条回执。
    return ChangesetRead.model_validate(
        changeset_review.confirm(
            session,
            changeset_id,
            owner_id=get_current_user_id(),
            notify=runtime_dependencies.thread_notifier(request),
            embedder=runtime_dependencies.request_embedder(),
            name_generator=runtime_dependencies.request_name_generator(),
        )
    )


@router.post("/changesets/{changeset_id}/reject", response_model=ChangesetRead)
def reject_changeset(
    changeset_id: uuid.UUID, request: Request, session: Session = Depends(get_session)
) -> ChangesetRead:
    return ChangesetRead.model_validate(
        changeset_review.revert(
            session,
            changeset_id,
            owner_id=get_current_user_id(),
            notify=runtime_dependencies.thread_notifier(request),
            embedder=runtime_dependencies.request_embedder(),
        )
    )
