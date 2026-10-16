from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy.orm import Session

from app.agent import thread
from app.config import get_settings
from app.deps import get_agent_checkpointer, get_current_user_id, get_session
from app.models import Changeset
from app.schemas.changeset import (
    ChangesetDetail,
    ChangesetOpCreate,
    ChangesetOpRead,
    ChangesetRead,
    CreateObjectTypeEdit,
    CreatePropertyEdit,
    CreateRelationEdit,
    DeleteEdit,
    GraphEditsCreate,
    ReuseObjectTypeEdit,
    UpdateEdit,
)
from app.services import changeset, changeset_review, element_edit, embedding

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


def _detail(session: Session, cs: Changeset) -> ChangesetDetail:
    ops = [ChangesetOpRead.model_validate(op) for op in changeset.list_ops(session, cs.id)]
    return ChangesetDetail(id=cs.id, graph_id=cs.graph_id, status=cs.status, ops=ops)


@router.get("/changesets/{changeset_id}", response_model=ChangesetDetail)
def get_changeset(
    changeset_id: uuid.UUID, session: Session = Depends(get_session)
) -> ChangesetDetail:
    cs = changeset.get_changeset(session, changeset_id, owner_id=get_current_user_id())
    return _detail(session, cs)


def _edit_ops(payload: GraphEditsCreate) -> list[element_edit.EditOp]:
    """把请求里的编辑转成服务层的操作对象。接口层只做这层形态转换。"""
    ops: list[element_edit.EditOp] = []
    for edit in payload.edits:
        if isinstance(edit, UpdateEdit):
            ops.append(
                element_edit.ElementUpdate(
                    element_kind=edit.element_kind,
                    element_id=edit.element_id,
                    patch=edit.patch,
                )
            )
        elif isinstance(edit, CreatePropertyEdit):
            ops.append(
                element_edit.PropertyCreate(
                    owner_type_id=edit.owner_type_id,
                    name=edit.name,
                    datatype=edit.datatype,
                    required=edit.required,
                    is_array=edit.is_array,
                    is_primary_key=edit.is_primary_key,
                    is_title_key=edit.is_title_key,
                )
            )
        elif isinstance(edit, CreateObjectTypeEdit):
            ops.append(element_edit.ObjectTypeCreate(name=edit.name, description=edit.description))
        elif isinstance(edit, ReuseObjectTypeEdit):
            ops.append(element_edit.ObjectTypeReuse(object_type_id=edit.object_type_id))
        elif isinstance(edit, CreateRelationEdit):
            ops.append(
                element_edit.RelationCreate(
                    source_type_id=edit.source_type_id,
                    target_type_id=edit.target_type_id,
                    name=edit.name,
                    direction=edit.direction,
                    cardinality=edit.cardinality,
                    forward_label=edit.forward_label,
                    reverse_label=edit.reverse_label,
                    foreign_key_property_id=edit.foreign_key_property_id,
                )
            )
        elif isinstance(edit, DeleteEdit):
            ops.append(
                element_edit.ElementDelete(
                    element_kind=edit.element_kind,
                    element_id=edit.element_id,
                )
            )
    return ops


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
        ops=_edit_ops(payload),
        owner_id=get_current_user_id(),
        embedder=_request_embedder(),
    )
    return _detail(session, cs)


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


def _thread_notifier(request: Request) -> changeset_review.ThreadNotifier:
    """把「定稿结果写回模型线程」这条 agent 依赖接到 review 服务上。

    服务层不感知 agent，钩子在这里注入：模型线程不更新，下一轮就会引用已经被
    撤销的对象和关系（子智能体核对时会发现图里根本没有）。
    """
    settings = get_settings()
    owner_id = get_current_user_id()

    def notify(session: Session, notice: changeset_review.Notice) -> None:
        thread.record_decision(
            session,
            settings,
            owner_id,
            conversation_id=notice.conversation_id,
            graph_id=notice.graph_id,
            changeset_id=notice.changeset_id,
            text=notice.text,
            checkpointer=get_agent_checkpointer(request.app.state, settings),
        )

    return notify


def _request_embedder() -> embedding.Embedder:
    """定稿时给已确认的对象补向量用的嵌入器；没配 key 就回落到本地实现。"""
    return embedding.build_embedder(get_settings())


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
            notify=_thread_notifier(request),
            embedder=_request_embedder(),
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
            notify=_thread_notifier(request),
            embedder=_request_embedder(),
        )
    )
