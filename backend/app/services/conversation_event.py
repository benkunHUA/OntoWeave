"""数据连接事件的落库、游标与幂等。"""

from __future__ import annotations

import logging
import uuid
from collections.abc import Callable
from datetime import datetime
from typing import Any

from sqlalchemy import and_, or_, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import Conversation, ConversationEvent
from app.services import conversation
from app.services.event_bus import ConversationEventBus
from app.vocabulary import ConversationKind

logger = logging.getLogger(__name__)

SessionFactory = Callable[[], Session]


def _uuid_or_none(value: Any) -> uuid.UUID | None:
    if value in (None, ""):
        return None
    try:
        return uuid.UUID(str(value))
    except (TypeError, ValueError):
        return None


def _conversation_id(session: Session, payload: dict) -> uuid.UUID | None:
    """conversationId 指向任何存在的会话；connectionId 仍按连接会话反查。"""
    direct = _uuid_or_none(payload.get("conversationId"))
    if direct is not None:
        row = session.get(Conversation, direct)
        return row.id if row is not None else None
    connection_id = _uuid_or_none(payload.get("connectionId"))
    if connection_id is None:
        return None
    found = conversation.find_conversation_by_resource(
        session,
        kind=ConversationKind.DATA_CONNECTION.value,
        resource_id=connection_id,
    )
    return found.id if found is not None else None


def append_event(
    session: Session,
    *,
    conversation_id: uuid.UUID,
    event_id: uuid.UUID,
    event_type: str,
    summary: str,
    payload: dict,
    operation_id: uuid.UUID | None = None,
    run_id: uuid.UUID | None = None,
    turn_id: uuid.UUID | None = None,
) -> bool:
    """按 `event_id` 幂等写入；返回是否真的新增了一条。"""
    statement = (
        pg_insert(ConversationEvent)
        .values(
            id=uuid.uuid4(),
            conversation_id=conversation_id,
            event_id=event_id,
            operation_id=operation_id,
            run_id=run_id,
            turn_id=turn_id,
            event_type=event_type,
            summary=summary,
            payload=payload,
        )
        .on_conflict_do_nothing(index_elements=[ConversationEvent.event_id])
        .returning(ConversationEvent.id)
    )
    return session.execute(statement).scalar_one_or_none() is not None


def ingest_envelope(
    factory: SessionFactory,
    envelope: dict,
    *,
    bus: ConversationEventBus | None = None,
    turn_id: uuid.UUID | None = None,
) -> bool:
    """消费一条 Outbox 信封。格式不对或找不到会话就丢弃，不阻塞队列。"""
    event_id = _uuid_or_none(envelope.get("eventId"))
    event_type = envelope.get("eventType")
    payload = envelope.get("payload")
    if event_id is None or not isinstance(event_type, str) or not isinstance(payload, dict):
        logger.warning("discard malformed data event: %r", envelope)
        return False

    with factory() as session:
        conversation_id = _conversation_id(session, payload)
        if conversation_id is None:
            logger.info("data event %s has no conversation, skipped", event_id)
            return False
        inserted = append_event(
            session,
            conversation_id=conversation_id,
            event_id=event_id,
            operation_id=_uuid_or_none(payload.get("operationId")),
            run_id=_uuid_or_none(payload.get("runId")),
            turn_id=turn_id or _uuid_or_none(payload.get("turnId")),
            event_type=event_type,
            summary=str(payload.get("summary") or event_type),
            payload=payload,
        )
        session.commit()
    if inserted and bus is not None:
        bus.publish(conversation_id)
    return inserted


def _cursor(
    session: Session,
    *,
    conversation_id: uuid.UUID,
    after_event_id: uuid.UUID | None,
) -> tuple[datetime, uuid.UUID] | None:
    if after_event_id is None:
        return None
    row = session.scalar(
        select(ConversationEvent).where(
            ConversationEvent.id == after_event_id,
            ConversationEvent.conversation_id == conversation_id,
        )
    )
    if row is None:
        raise NotFoundError(f"conversation event {after_event_id} not found")
    return row.created_at, row.id


def list_events(
    session: Session,
    conversation_id: uuid.UUID,
    *,
    owner_id: uuid.UUID,
    after_event_id: uuid.UUID | None = None,
    limit: int = 100,
) -> list[ConversationEvent]:
    conversation.get_conversation(session, conversation_id, owner_id=owner_id)
    cursor = _cursor(
        session,
        conversation_id=conversation_id,
        after_event_id=after_event_id,
    )
    statement = select(ConversationEvent).where(
        ConversationEvent.conversation_id == conversation_id
    )
    if cursor is not None:
        created_at, event_row_id = cursor
        statement = statement.where(
            or_(
                ConversationEvent.created_at > created_at,
                and_(
                    ConversationEvent.created_at == created_at,
                    ConversationEvent.id > event_row_id,
                ),
            )
        )
    return list(
        session.scalars(
            statement.order_by(ConversationEvent.created_at, ConversationEvent.id).limit(limit)
        )
    )


def list_recent_events(
    session: Session,
    conversation_id: uuid.UUID,
    *,
    limit: int = 10,
) -> list[ConversationEvent]:
    rows = list(
        session.scalars(
            select(ConversationEvent)
            .where(ConversationEvent.conversation_id == conversation_id)
            .order_by(ConversationEvent.created_at.desc(), ConversationEvent.id.desc())
            .limit(limit)
        )
    )
    rows.reverse()
    return rows
