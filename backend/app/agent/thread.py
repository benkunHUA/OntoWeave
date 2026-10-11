"""把用户在图外的定稿动作写回模型线程。

确认 / 撤销是用户点按钮做的事，不走 agent 循环；可模型下一轮还会引用草稿里的
对象。线程不更新，就会出现「模型以为景点还在、图里其实已经被撤销」的错位，
子智能体核对时也会一头雾水。这里把定稿结果并进上一条助手消息，让模型以当前图
的实际内容为准。

线程是模型真正读到的上下文（按 thread_id 存检查点），和 conversation_message
那张展示用的转录表是两回事，别写混。
"""

from __future__ import annotations

import logging
import uuid
from typing import Any

from langchain_core.messages import AIMessage
from langgraph.checkpoint.base import BaseCheckpointSaver
from sqlalchemy.orm import Session

from app.agent import runtime
from app.config import Settings

logger = logging.getLogger(__name__)


def _merged_content(content: Any, note: str) -> Any:
    """把旁白并进已有内容：字符串直接接，块列表就补一个 text 块。"""
    if isinstance(content, str):
        return f"{content}\n\n{note}" if content else note
    if isinstance(content, list):
        return [*content, {"type": "text", "text": f"\n\n{note}"}]
    return note


def append_note(agent: Any, conversation_id: uuid.UUID, text: str) -> None:
    """把旁白并进上一条助手消息，而不是新增一条 user 消息。

    新增 user 消息会让「旁白 + 用户下一条提问」变成两条连续 user，影响模型效果；
    并进上一条 assistant 消息，线程始终严格 user/assistant 交替。万一最后一条不是
    助手消息（线程为空、或停在工具错误上），退化成追加一条 assistant 消息。
    """
    config = runtime.thread_config(conversation_id)
    messages = (agent.get_state(config).values or {}).get("messages") or []
    last = messages[-1] if messages else None
    if isinstance(last, AIMessage):
        merged = last.model_copy(update={"content": _merged_content(last.content, text)})
        agent.update_state(config, {"messages": [merged]})
        return
    agent.update_state(config, {"messages": [AIMessage(content=text)]})


def record_decision(
    session: Session,
    settings: Settings,
    owner_id: uuid.UUID,
    *,
    conversation_id: uuid.UUID,
    graph_id: uuid.UUID,
    changeset_id: uuid.UUID,
    text: str,
    checkpointer: BaseCheckpointSaver | None,
) -> None:
    """确认 / 撤销后把结果写回模型线程。

    写回是锦上添花：没有检查点、模型没配、线程状态异常，都不该拖垮定稿这个主
    流程，所以整体兜底，只留一条告警。
    """
    if checkpointer is None:
        return
    try:
        run = runtime.RunContext(
            conversation_id=conversation_id,
            graph_id=graph_id,
            changeset_id=changeset_id,
        )
        agent = runtime.build_agent_for_run(
            session, settings, run, owner_id, checkpointer=checkpointer
        )
        append_note(agent, conversation_id, text)
    except Exception:  # pragma: no cover - 兜底分支由测试用桩覆盖
        logger.warning("failed to write changeset decision into agent thread", exc_info=True)
