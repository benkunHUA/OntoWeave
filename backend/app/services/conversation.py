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
