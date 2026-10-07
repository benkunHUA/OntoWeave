from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.agent import runtime
from app.config import get_settings
from app.deps import get_current_user_id, get_session

router = APIRouter(tags=["agent"])

# 关掉代理缓冲，否则事件被攒着一起发，前端看不到渐进效果。
SSE_HEADERS = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}


class MessageIn(BaseModel):
    text: str


class ResumeIn(BaseModel):
    answer: str


def _stream(request: Request, conversation_id: uuid.UUID, **kwargs) -> StreamingResponse:
    settings = get_settings()
    return StreamingResponse(
        runtime.stream_run(
            request.app.state.session_factory,
            settings,
            get_current_user_id(),
            conversation_id=conversation_id,
            checkpointer=_checkpointer(request, settings),
            **kwargs,
        ),
        media_type="text/event-stream",
        headers=SSE_HEADERS,
    )


def _checkpointer(request: Request, settings):
    """懒建并缓存检查点。没有它，interrupt_on 和 ask_user 都无法工作。"""
    state = request.app.state
    if state.checkpointer is None and state.enable_agent_checkpoints:
        from app.agent import graph as agent_graph

        state.checkpointer, state.checkpointer_pool = agent_graph.build_checkpointer(
            settings.database_url
        )
    return state.checkpointer


@router.post("/conversations/{conversation_id}/messages")
def post_message(
    conversation_id: uuid.UUID,
    payload: MessageIn,
    request: Request,
    session: Session = Depends(get_session),
) -> StreamingResponse:
    # 先在普通请求里校验会话存在与归属，别等流开了才报 404
    runtime.prepare_run(session, conversation_id=conversation_id, owner_id=get_current_user_id())
    return _stream(request, conversation_id, text=payload.text)


@router.post("/conversations/{conversation_id}/resume")
def post_resume(
    conversation_id: uuid.UUID,
    payload: ResumeIn,
    request: Request,
    session: Session = Depends(get_session),
) -> StreamingResponse:
    runtime.prepare_run(session, conversation_id=conversation_id, owner_id=get_current_user_id())
    return _stream(request, conversation_id, resume=payload.answer)
