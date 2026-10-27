"""会话查询、归属与可写性。"""

from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import Conversation
from app.vocabulary import CONVERSATION_KINDS, ConversationKind


def _ensure_supported_kind(kind: str) -> None:
    if kind not in CONVERSATION_KINDS:
        raise InvariantViolationError(f"unsupported conversation kind {kind!r}")


def get_conversation(
    session: Session, conversation_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> Conversation:
    conv = session.get(Conversation, conversation_id)
    if conv is None or (owner_id is not None and conv.owner_id != owner_id):
        raise NotFoundError(f"conversation {conversation_id} not found")
    return conv


def ensure_writable(conv: Conversation) -> None:
    if conv.archived_at is not None:
        raise InvariantViolationError(f"conversation {conv.id} is archived")


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
