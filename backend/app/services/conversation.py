from __future__ import annotations

import uuid

from sqlalchemy import select, update
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import Conversation, ConversationMessage, Graph, ViewState
from app.services import graph, graph_cleanup
from app.vocabulary import CONVERSATION_KINDS, ConversationKind


def _ensure_supported_kind(kind: str) -> None:
    if kind not in CONVERSATION_KINDS:
        raise InvariantViolationError(f"unsupported conversation kind {kind!r}")


def create_conversation(
    session: Session,
    *,
    owner_id: uuid.UUID,
    title: str | None = None,
    kind: str = ConversationKind.MODELING.value,
    resource_id: uuid.UUID | None = None,
) -> Conversation:
    """按会话类型建立资源绑定；建模会话仍保持一次对话一张图。"""
    _ensure_supported_kind(kind)
    if kind == ConversationKind.MODELING:
        if resource_id is not None:
            raise InvariantViolationError("modeling conversations cannot set resource_id")
        g = graph.create_graph(session, owner_id=owner_id, name=title)
        resource_id = g.id
    elif resource_id is None:
        raise InvariantViolationError(f"{kind} conversations require a resource_id")

    conv = Conversation(
        owner_id=owner_id,
        kind=kind,
        resource_id=resource_id,
        title=title,
    )
    session.add(conv)
    session.flush()
    return conv


def get_conversation(
    session: Session, conversation_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> Conversation:
    conv = session.get(Conversation, conversation_id)
    if conv is None or (owner_id is not None and conv.owner_id != owner_id):
        raise NotFoundError(f"conversation {conversation_id} not found")
    return conv


def find_conversation_by_graph(
    session: Session, graph_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> Conversation | None:
    """一图一会话，按图反查。找不到返回 None，交给调用方决定是不是硬要求。"""
    return find_conversation_by_resource(
        session,
        kind=ConversationKind.MODELING.value,
        resource_id=graph_id,
        owner_id=owner_id,
    )


def find_conversation_by_resource(
    session: Session,
    *,
    kind: str,
    resource_id: uuid.UUID,
    owner_id: uuid.UUID | None = None,
) -> Conversation | None:
    _ensure_supported_kind(kind)
    stmt = select(Conversation).where(
        Conversation.kind == kind,
        Conversation.resource_id == resource_id,
    )
    if owner_id is not None:
        stmt = stmt.where(Conversation.owner_id == owner_id)
    return session.scalar(stmt)


def rename_conversation(
    session: Session,
    conversation_id: uuid.UUID,
    *,
    title: str,
    owner_id: uuid.UUID | None = None,
) -> Conversation:
    conv = get_conversation(session, conversation_id, owner_id=owner_id)
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
    具体口径见 `app.services.graph_cleanup`。其它资源会话只删会话与消息。
    """
    conv = get_conversation(session, conversation_id, owner_id=owner_id)
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
    return list(
        session.scalars(
            stmt.order_by(Conversation.created_at.desc(), Conversation.id)
            .limit(limit)
            .offset(offset)
        )
    )


def add_message(
    session: Session,
    *,
    conversation_id: uuid.UUID,
    role: str,
    content: str,
    kind: str = "message",
    blocks: list[dict] | None = None,
) -> ConversationMessage:
    get_conversation(session, conversation_id)
    message = ConversationMessage(
        conversation_id=conversation_id,
        role=role,
        content=content,
        kind=kind,
        blocks=blocks,
    )
    session.add(message)
    session.flush()
    return message


def resolve_tool_block(
    session: Session,
    *,
    conversation_id: uuid.UUID,
    call_id: str,
    status: str,
    result: str = "",
    duration_ms: int | None = None,
    limit: int = 50,
) -> bool:
    """把历史消息里悬挂的工具块收口，返回是否真的改到了。

    `ask_user`、工具审批这类在工具内部中断的调用，ToolMessage 要等用户回答后的
    下一轮才出现，而那一轮并没有对应的起始块。状态必须回填到上一轮那条消息里，
    否则前端刷新后还是「等待你的判断」，一直转圈。
    """
    rows = session.scalars(
        select(ConversationMessage)
        .where(ConversationMessage.conversation_id == conversation_id)
        .order_by(ConversationMessage.created_at.desc(), ConversationMessage.id.desc())
        .limit(limit)
    )
    for row in rows:
        blocks = row.blocks
        if not blocks:
            continue
        for index, block in enumerate(blocks):
            if not isinstance(block, dict) or block.get("kind") != "tool":
                continue
            if block.get("callId") != call_id or block.get("status") != "running":
                continue
            # 整块换新对象，别原地改 dict：JSON 列不跟踪原地变更。
            next_blocks = list(blocks)
            next_blocks[index] = {
                **block,
                "status": status,
                "result": result,
                "durationMs": duration_ms,
            }
            row.blocks = next_blocks
            return True
    return False


def list_messages(
    session: Session,
    conversation_id: uuid.UUID,
    *,
    owner_id: uuid.UUID | None = None,
    limit: int = 200,
) -> list[ConversationMessage]:
    get_conversation(session, conversation_id, owner_id=owner_id)
    rows = list(
        session.scalars(
            select(ConversationMessage)
            .where(ConversationMessage.conversation_id == conversation_id)
            .order_by(ConversationMessage.created_at.desc(), ConversationMessage.id.desc())
            .limit(limit)
        )
    )
    rows.reverse()
    return rows
