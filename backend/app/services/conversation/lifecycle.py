"""会话创建、重命名、归档与删除。"""

from __future__ import annotations

import uuid
from datetime import UTC, datetime

from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import Conversation, Graph, ViewState
from app.services import graph
from app.services.conversation.lookup import (
    _ensure_supported_kind,
    ensure_writable,
    find_conversation_by_resource,
    get_conversation,
)
from app.services.graph import cleanup as graph_cleanup
from app.vocabulary import ConversationKind


def create_conversation(
    session: Session,
    *,
    owner_id: uuid.UUID,
    title: str | None = None,
    kind: str = ConversationKind.MODELING.value,
    resource_id: uuid.UUID | None = None,
    conversation_id: uuid.UUID | None = None,
) -> Conversation:
    """按会话类型建立资源绑定；建模会话仍保持一次对话一张图。"""
    _ensure_supported_kind(kind)
    if kind == ConversationKind.MODELING:
        if resource_id is not None:
            raise InvariantViolationError("modeling conversations cannot set resource_id")
        g = graph.create_graph(session, owner_id=owner_id, name=title)
        resource_id = g.id
    elif kind == ConversationKind.QUERY:
        # 查询会话可以跨图连续追问，resource_id 只是会话身份，不绑定固定图。
        resource_id = resource_id or uuid.uuid4()
    elif resource_id is None:
        raise InvariantViolationError(f"{kind} conversations require a resource_id")

    conv = Conversation(
        id=conversation_id or uuid.uuid4(),
        owner_id=owner_id,
        kind=kind,
        resource_id=resource_id,
        title=title,
    )
    session.add(conv)
    session.flush()
    return conv


def ensure_resource_conversation(
    session: Session,
    *,
    owner_id: uuid.UUID,
    kind: str,
    resource_id: uuid.UUID,
    title: str,
    conversation_id: uuid.UUID | None = None,
) -> Conversation:
    conv, _ = ensure_resource_conversation_with_status(
        session,
        owner_id=owner_id,
        kind=kind,
        resource_id=resource_id,
        title=title,
        conversation_id=conversation_id,
    )
    return conv


def ensure_resource_conversation_with_status(
    session: Session,
    *,
    owner_id: uuid.UUID,
    kind: str,
    resource_id: uuid.UUID,
    title: str,
    conversation_id: uuid.UUID | None = None,
) -> tuple[Conversation, bool]:
    """按资源取会话；只有首次使用时才落库。

    资源方（数据连接 / 数据流程）会先分配 conversation_id 并交给 Java 保存，
    所以这里允许用该 id 建行，保证两边指向同一个会话。
    """
    if kind == ConversationKind.MODELING:
        raise InvariantViolationError("modeling conversations cannot bind an external resource")

    existing = find_conversation_by_resource(
        session,
        kind=kind,
        resource_id=resource_id,
        owner_id=owner_id,
    )
    if existing is not None:
        return existing, False

    try:
        with session.begin_nested():
            created = create_conversation(
                session,
                owner_id=owner_id,
                title=title,
                kind=kind,
                resource_id=resource_id,
                conversation_id=conversation_id,
            )
        return created, True
    except IntegrityError:
        # 并发首条消息可能同时尝试建行；savepoint 回滚后按资源重新收敛。
        session.expire_all()
        raced = find_conversation_by_resource(
            session,
            kind=kind,
            resource_id=resource_id,
            owner_id=owner_id,
        )
        if raced is None:
            raise
        return raced, False


def rename_conversation(
    session: Session,
    conversation_id: uuid.UUID,
    *,
    title: str,
    owner_id: uuid.UUID | None = None,
) -> Conversation:
    conv = get_conversation(session, conversation_id, owner_id=owner_id)
    ensure_writable(conv)
    normalized = title.strip()
    if not normalized:
        raise InvariantViolationError("conversation title must not be empty")
    conv.title = normalized
    if conv.kind == ConversationKind.MODELING:
        graph_row = graph.get_graph(session, conv.resource_id, owner_id=owner_id)
        graph_row.name = normalized
        session.execute(
            update(ViewState).where(ViewState.graph_id == conv.resource_id).values(name=normalized)
        )
    session.flush()
    return conv


def delete_conversation(
    session: Session,
    conversation_id: uuid.UUID,
    *,
    owner_id: uuid.UUID | None = None,
    scope: graph_cleanup.DeleteScope = graph_cleanup.DEFAULT_DELETE_SCOPE,
) -> None:
    """删会话；建模会话连带删掉它一对一的那张图。

    只有建模会话持有图。图被删时，图成员（graph_object / graph_relation）、改动集、视图状态随
    ON DELETE CASCADE 一起清掉；共享池里的对象类型 / 关系只把 home_graph_id
    置空（ON DELETE SET NULL）。要不要顺手清理这些共享元素由 `scope` 决定，
    具体口径见 `app.services.graph.cleanup`。其它资源会话只删会话与消息。
    """
    conv = get_conversation(session, conversation_id, owner_id=owner_id)
    ensure_writable(conv)
    graph_row = None
    if conv.kind == ConversationKind.MODELING:
        graph_id = conv.resource_id
        graph_cleanup.purge_graph_elements(
            session, graph_id=graph_id, owner_id=owner_id, scope=scope
        )
        graph_row = session.get(Graph, graph_id)
    session.delete(conv)
    if graph_row is not None:
        session.delete(graph_row)
    session.flush()


def list_conversations(
    session: Session,
    *,
    owner_id: uuid.UUID | None = None,
    kind: str | None = None,
    include_archived: bool = False,
    limit: int = 50,
    offset: int = 0,
) -> list[Conversation]:
    if kind is not None:
        _ensure_supported_kind(kind)
    stmt = select(Conversation)
    if owner_id is not None:
        stmt = stmt.where(Conversation.owner_id == owner_id)
    if kind is not None:
        stmt = stmt.where(Conversation.kind == kind)
    if not include_archived:
        stmt = stmt.where(Conversation.archived_at.is_(None))
    return list(
        session.scalars(
            stmt.order_by(Conversation.updated_at.desc(), Conversation.id)
            .limit(limit)
            .offset(offset)
        )
    )


def archive_conversation(
    session: Session,
    conversation_id: uuid.UUID,
    *,
    owner_id: uuid.UUID | None = None,
) -> Conversation:
    """逻辑归档；重复调用保持幂等，不触碰消息或其它资源。"""
    conv = get_conversation(session, conversation_id, owner_id=owner_id)
    if conv.archived_at is None:
        conv.archived_at = datetime.now(UTC)
        session.flush()
    return conv
