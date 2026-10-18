from __future__ import annotations

import json
import uuid
from typing import Any

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.agent import runtime
from app.config import get_settings
from app.deps import get_agent_checkpointer, get_current_user_id, get_session

router = APIRouter(tags=["agent"])

# 关掉代理缓冲，否则事件被攒着一起发，前端看不到渐进效果。
SSE_HEADERS = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}


class MessageIn(BaseModel):
    text: str


class ResumeIn(BaseModel):
    # 回传给图的中断响应：ask_user 是字符串，工具审批是 {"decisions": [...]}。
    answer: Any
    # 展示并入库的用户消息；不传就按 answer 生成。
    text: str | None = None


def _stream(request: Request, conversation_id: uuid.UUID, **kwargs) -> StreamingResponse:
    settings = get_settings()
    return StreamingResponse(
        runtime.stream_run(
            request.app.state.session_factory,
            settings,
            get_current_user_id(),
            conversation_id=conversation_id,
            checkpointer=get_agent_checkpointer(request.app.state, settings),
            **kwargs,
        ),
        media_type="text/event-stream",
        headers=SSE_HEADERS,
    )


@router.post("/conversations/{conversation_id}/messages")
def post_message(
    conversation_id: uuid.UUID,
    payload: MessageIn,
    request: Request,
    session: Session = Depends(get_session),
) -> StreamingResponse:
    # 先在普通请求里校验会话存在与归属，别等流开了才报 404
    runtime.validate_conversation(
        session,
        conversation_id=conversation_id,
        owner_id=get_current_user_id(),
    )
    return _stream(request, conversation_id, text=payload.text)


@router.post("/conversations/{conversation_id}/resume")
def post_resume(
    conversation_id: uuid.UUID,
    payload: ResumeIn,
    request: Request,
    session: Session = Depends(get_session),
) -> StreamingResponse:
    runtime.validate_conversation(
        session,
        conversation_id=conversation_id,
        owner_id=get_current_user_id(),
    )
    display = payload.text or (
        payload.answer
        if isinstance(payload.answer, str)
        else json.dumps(payload.answer, ensure_ascii=False)
    )
    return _stream(request, conversation_id, text=display, resume=payload.answer)
