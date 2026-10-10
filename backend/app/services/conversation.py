from __future__ import annotations

import uuid

from sqlalchemy import select, update
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import Conversation, ConversationMessage, ViewState
from app.services import graph


def create_conversation(
    session: Session, *, owner_id: uuid.UUID, title: str | None = None
) -> Conversation:
    """一次对话一张图：建会话的同时把图建出来，两者一对一。"""
    g = graph.create_graph(session, owner_id=owner_id, name=title)
    conv = Conversation(owner_id=owner_id, graph_id=g.id, title=title)
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
    graph_row = graph.get_graph(session, conv.graph_id, owner_id=owner_id)
    graph_row.name = normalized
    session.execute(
        update(ViewState).where(ViewState.graph_id == conv.graph_id).values(name=normalized)
    )
    session.flush()
    return conv


def list_conversations(
    session: Session,
    *,
    owner_id: uuid.UUID | None = None,
    limit: int = 50,
    offset: int = 0,
) -> list[Conversation]:
    stmt = select(Conversation)
    if owner_id is not None:
        stmt = stmt.where(Conversation.owner_id == owner_id)
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
