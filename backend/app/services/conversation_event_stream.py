"""对话事件的 SSE 编排：先补历史，再按游标增量推送。"""

from __future__ import annotations

import time
import uuid
from collections.abc import Callable, Iterator

from sqlalchemy.orm import Session, sessionmaker

from app.schemas.conversation_event import ConversationEventRead
from app.services import conversation_event
from app.services.event_bus import ConversationEventBus

HEARTBEAT_SECONDS = 15.0
POLL_SECONDS = 1.0


def _frame(event: ConversationEventRead) -> str:
    return f"event: conversation.event\ndata: {event.model_dump_json()}\n\n"


def iter_event_stream(
    factory: sessionmaker[Session],
    bus: ConversationEventBus,
    *,
    conversation_id: uuid.UUID,
    owner_id: uuid.UUID,
    after_event_id: uuid.UUID | None,
    should_stop: Callable[[], bool] | None = None,
) -> Iterator[str]:
    cursor = after_event_id
    last_heartbeat = time.monotonic()
    while should_stop is None or not should_stop():
        version = bus.snapshot(conversation_id)
        with factory() as session:
            events = conversation_event.list_events(
                session,
                conversation_id,
                owner_id=owner_id,
                after_event_id=cursor,
                limit=100,
            )
        if events:
            for item in events:
                yield _frame(ConversationEventRead.model_validate(item))
            cursor = events[-1].id
            last_heartbeat = time.monotonic()
            continue
        if time.monotonic() - last_heartbeat >= HEARTBEAT_SECONDS:
            yield "event: heartbeat\ndata: {}\n\n"
            last_heartbeat = time.monotonic()
        bus.wait(conversation_id, version, timeout=POLL_SECONDS)
