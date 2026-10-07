from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import Conversation
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


def get_conversation(session: Session, conversation_id: uuid.UUID) -> Conversation:
    conv = session.get(Conversation, conversation_id)
    if conv is None:
        raise NotFoundError(f"conversation {conversation_id} not found")
    return conv


def list_conversations(session: Session, *, limit: int = 50, offset: int = 0) -> list[Conversation]:
    return list(
        session.scalars(
            select(Conversation)
            .order_by(Conversation.created_at.desc(), Conversation.id)
            .limit(limit)
            .offset(offset)
        )
    )
