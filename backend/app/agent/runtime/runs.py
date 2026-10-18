"""一次运行的生命周期。"""

from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.agent.definitions.registry import get_definition
from app.models import Conversation
from app.services import conversation


def thread_config(conversation_id: uuid.UUID) -> dict:
    """模型线程的键约定：一条会话一个线程，所有读写都从这里取，别各写各的。"""
    return {"configurable": {"thread_id": str(conversation_id)}}


def validate_conversation(
    session: Session,
    *,
    conversation_id: uuid.UUID,
    owner_id: uuid.UUID,
) -> Conversation:
    """确认会话存在、归属正确，并且有可用的 Agent 定义。"""
    conv = conversation.get_conversation(session, conversation_id, owner_id=owner_id)
    get_definition(conv.kind)
    return conv
