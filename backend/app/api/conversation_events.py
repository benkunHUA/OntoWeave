"""对话事件历史与 SSE 推送。"""

from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Query, Request
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from app.deps import get_current_user_id, get_session
from app.schemas.conversation_event import ConversationEventRead
from app.services import conversation_event
from app.services.conversation_event_stream import iter_event_stream

router = APIRouter(tags=["conversation-events"])

SSE_HEADERS = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}


@router.get(
    "/conversations/{conversation_id}/events",
    response_model=list[ConversationEventRead],
)
def list_events(
    conversation_id: uuid.UUID,
    after_event_id: uuid.UUID | None = Query(None),
    limit: int = Query(100, ge=1, le=200),
    session: Session = Depends(get_session),
) -> list[ConversationEventRead]:
    rows = conversation_event.list_events(
        session,
        conversation_id,
        owner_id=get_current_user_id(),
        after_event_id=after_event_id,
        limit=limit,
    )
    return [ConversationEventRead.model_validate(row) for row in rows]


@router.get("/conversations/{conversation_id}/events/stream")
def stream_events(
    conversation_id: uuid.UUID,
    request: Request,
    after_event_id: uuid.UUID | None = Query(None),
    session: Session = Depends(get_session),
) -> StreamingResponse:
    # 先在普通请求里校验会话归属与游标，别等流开了才报错。
    conversation_event.list_events(
        session,
        conversation_id,
        owner_id=get_current_user_id(),
        after_event_id=after_event_id,
        limit=1,
    )
    return StreamingResponse(
        iter_event_stream(
            request.app.state.session_factory,
            request.app.state.event_bus,
            conversation_id=conversation_id,
            owner_id=get_current_user_id(),
            after_event_id=after_event_id,
        ),
        media_type="text/event-stream",
        headers=SSE_HEADERS,
    )
