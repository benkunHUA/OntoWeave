"""会话消息读写。"""

from __future__ import annotations

import uuid
from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import ConversationMessage
from app.services.conversation.lookup import ensure_writable, get_conversation


def add_message(
    session: Session,
    *,
    conversation_id: uuid.UUID,
    role: str,
    content: str,
    kind: str = "message",
    blocks: list[dict] | None = None,
) -> ConversationMessage:
    conv = get_conversation(session, conversation_id)
    ensure_writable(conv)
    # 会话列表按更新时间排序；有来有回才说明这个会话刚被用过。
    conv.updated_at = datetime.now(UTC)
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
    ensure_writable(get_conversation(session, conversation_id))
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
