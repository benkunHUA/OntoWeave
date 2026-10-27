"""动作类型的读写、状态流转与图成员关系。"""

from __future__ import annotations

import uuid

from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import ActionType, GraphAction
from app.services import status
from app.services.graph.core import get_graph


def create_action_type(
    session: Session,
    *,
    owner_id: uuid.UUID,
    name: str,
    description: str | None = None,
    home_graph_id: uuid.UUID | None = None,
) -> ActionType:
    """建一个草稿动作类型。草稿不写向量，确认时才补，检索因此只认已确认的动作。"""
    action = ActionType(
        owner_id=owner_id,
        name=name,
        description=description,
        status=status.DRAFT,
        home_graph_id=home_graph_id,
    )
    session.add(action)
    session.flush()
    return action


def get_action_type(session: Session, action_type_id: uuid.UUID) -> ActionType:
    action = session.get(ActionType, action_type_id)
    if action is None:
        raise NotFoundError(f"action type {action_type_id} not found")
    return action


def list_action_types(
    session: Session,
    *,
    owner_id: uuid.UUID | None = None,
    query: str | None = None,
    status_value: str | None = None,
    limit: int = 50,
    offset: int = 0,
) -> list[ActionType]:
    """按名称排序分页；加 id 作次级排序键，翻页时顺序稳定。

    `query` 按名称 / 描述做子串匹配——接口层的检索与调试用它，面向智能体的语义检索
    走 `embedding.search_action_types`。
    """
    stmt = select(ActionType)
    if owner_id is not None:
        stmt = stmt.where(ActionType.owner_id == owner_id)
    if query:
        pattern = f"%{query}%"
        stmt = stmt.where(
            or_(ActionType.name.ilike(pattern), ActionType.description.ilike(pattern))
        )
    if status_value is not None:
        stmt = stmt.where(ActionType.status == status_value)
    return list(
        session.scalars(stmt.order_by(ActionType.name, ActionType.id).limit(limit).offset(offset))
    )


def transition_action_type(
    session: Session,
    action_type_id: uuid.UUID,
    target: str,
    *,
    owner_id: uuid.UUID | None = None,
) -> ActionType:
    action = get_action_type(session, action_type_id)
    if owner_id is not None and action.owner_id != owner_id:
        raise NotFoundError(f"action type {action_type_id} not found")
    status.ensure_transition(action.status, target, f"action type {action_type_id}")
    action.status = target
    session.flush()
    return action


def add_action_to_graph(
    session: Session,
    graph_id: uuid.UUID,
    action_type_id: uuid.UUID,
    *,
    owner_id: uuid.UUID | None = None,
) -> None:
    """把动作类型挂进图。动作类型是共享池元素，图成员表只记「这张图用了它」。"""
    get_graph(session, graph_id, owner_id=owner_id)
    get_action_type(session, action_type_id)
    if session.get(GraphAction, (graph_id, action_type_id)) is not None:
        return
    session.add(GraphAction(graph_id=graph_id, action_type_id=action_type_id))
    session.flush()


def list_graph_actions(session: Session, graph_id: uuid.UUID) -> list[uuid.UUID]:
    rows = session.scalars(
        select(GraphAction.action_type_id)
        .where(GraphAction.graph_id == graph_id)
        .order_by(GraphAction.added_at, GraphAction.action_type_id)
    )
    return list(rows)
