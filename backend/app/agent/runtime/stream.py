"""一轮对话的流式编排。

只做编排：把模型线程、事件协议、工具执行与落库串起来。会话类型对应的运行策略由
AgentDefinition 提供，事件形状在 events.py，中断形状在 interrupts.py。

这里自己管事务，不走请求级依赖注入——流式响应会活过依赖的生命周期。
"""

from __future__ import annotations

import logging
import time
import uuid
from collections.abc import Iterator
from dataclasses import replace
from typing import Any

from langchain_core.messages import AIMessage, AIMessageChunk, HumanMessage, ToolMessage
from langgraph.types import Command
from sqlalchemy.orm import Session, sessionmaker

from app.agent import observability
from app.agent.definitions.base import AgentRunContext
from app.agent.definitions.registry import get_definition
from app.agent.runtime.errors import user_facing_run_error
from app.agent.runtime.events import TurnBuilder, block_fragments, content_text, sse
from app.agent.runtime.interrupts import normalize_interrupt
from app.agent.runtime.runs import thread_config
from app.agent.runtime.subagent_trace import SubagentTrace
from app.config import Settings
from app.schemas.agent_events import (
    RunEndEvent,
    RunErrorEvent,
    RunStartEvent,
    StateUpdateEvent,
)
from app.schemas.message_blocks import ToolStatus
from app.services import conversation

# langgraph 嵌套子图用 "|" 拼命名空间；顶层节点没有它。
_SUBGRAPH_SEP = "|"
_STREAM_MODES = ("updates", "messages", "values", "custom")
# deepagents 用这个工具名把活派给子智能体；参数里的 subagent_type 才是派给了谁。
_TASK_TOOL = "task"

logger = logging.getLogger(__name__)


def _split_chunk(chunk: Any) -> tuple[tuple[str, ...], str, Any]:
    """多 stream_mode 下 langgraph 吐 (mode, payload)。

    `subgraphs=True` 时前面还多一段命名空间：`(namespace, mode, payload)`。命名空间为空
    表示主智能体自己；非空就是某个工具内部调起来的图（目前只有 `task` 起的子智能体）。
    """
    if (
        isinstance(chunk, tuple)
        and len(chunk) == 3
        and isinstance(chunk[1], str)
        and chunk[1] in _STREAM_MODES
    ):
        return tuple(chunk[0] or ()), chunk[1], chunk[2]
    if (
        isinstance(chunk, tuple)
        and len(chunk) == 2
        and isinstance(chunk[0], str)
        and chunk[0] in _STREAM_MODES
    ):
        return (), chunk[0], chunk[1]
    return (), "updates", chunk


def _is_streamable(metadata: dict | None) -> bool:
    """只收主智能体的模型 token：子智能体和隐藏调用不该混进正文流。"""
    metadata = metadata or {}
    node = metadata.get("langgraph_node")
    if node is not None and node != "model":
        return False
    namespace = str(metadata.get("langgraph_checkpoint_ns") or "")
    return _SUBGRAPH_SEP not in namespace


def stream_run(
    factory: sessionmaker[Session],
    settings: Settings,
    owner_id: uuid.UUID,
    *,
    conversation_id: uuid.UUID,
    checkpointer=None,
    text: str | None = None,
    resume: Any | None = None,
    turn_id: uuid.UUID | None = None,
) -> Iterator[str]:
    """跑一轮并推 SSE 事件。

    这里自己管事务，不走请求级依赖注入——流式响应会活过依赖的生命周期。
    """
    session = factory()
    run: AgentRunContext | None = None
    run_id = uuid.uuid4().hex
    message_id = uuid.uuid4().hex
    turn_id = turn_id or uuid.uuid4()
    turn = TurnBuilder(message_id)
    try:
        conv = conversation.get_conversation(
            session,
            conversation_id,
            owner_id=owner_id,
        )
        definition = get_definition(conv.kind)
        run = replace(
            definition.prepare_run(session, conversation=conv, owner_id=owner_id),
            turn_id=turn_id,
        )
        conversation.add_message(
            session,
            conversation_id=conversation_id,
            role="user",
            content=text or "",
        )
        # 用户输入先独立提交，后面的模型或工具失败也不能把这句话丢掉。
        session.commit()
        agent = definition.build_graph(session, settings, run, checkpointer=checkpointer)
        config: dict = thread_config(conversation_id)
        # 配了 Langfuse 就把审计回调挂上；langgraph 会把它透传给子智能体。
        config.update(
            observability.trace_config(settings, conversation_id=conversation_id, owner_id=owner_id)
        )

        yield sse(
            RunStartEvent(
                runId=run_id,
                conversationId=str(conversation_id),
                messageId=message_id,
            )
        )
        payload: dict | Command = (
            Command(resume=resume)
            if resume is not None
            else {"messages": [HumanMessage(text or "")]}
        )
        paused = False
        interrupt_question: str | None = None
        # 模型能流式就只看 messages 的增量；不能流式时用 updates 的整条消息兜底，
        # 两种情况都只出一遍，靠这个标记二选一。
        saw_delta = False
        tool_started_at: dict[str, float] = {}
        trace = SubagentTrace(definition.visible_subagents)

        # subgraphs=True：子智能体内部的事件也吐出来，由 SubagentTrace 决定露不露给前端。
        for chunk in agent.stream(
            payload,
            config=config,
            stream_mode=["updates", "messages"],
            subgraphs=True,
        ):
            namespace, mode, data = _split_chunk(chunk)
            subagent = trace.call_for(namespace)
            if namespace and (subagent is None or not subagent.visible):
                continue
            if mode == "messages":
                # 只流主智能体的正文；子智能体的话留在它自己的任务里，不混进主对话。
                if subagent is not None:
                    continue
                message_chunk, metadata = data
                # 完整的 AIMessage 会再从 updates 走一遍，这里只认增量 chunk。
                if not isinstance(message_chunk, AIMessageChunk) or not _is_streamable(metadata):
                    continue
                fragments = block_fragments(message_chunk)
                if fragments:
                    saw_delta = True
                for kind, piece in fragments:
                    events = turn.reasoning(piece) if kind == "reasoning" else turn.text(piece)
                    for event in events:
                        yield sse(event)
                continue

            for node, update in (data or {}).items():
                if node == "__interrupt__":
                    paused = True
                    for item in update if isinstance(update, (list, tuple)) else [update]:
                        value = getattr(item, "value", None) or {}
                        question, options, free_text, details = normalize_interrupt(value)
                        interrupt_question = question or interrupt_question
                        for event in turn.interrupt(
                            question=question,
                            options=options,
                            free_text=free_text,
                            details=details,
                        ):
                            yield sse(event)
                    continue
                for message in (update or {}).get("messages", []) or []:
                    if isinstance(message, ToolMessage):
                        call_id = getattr(message, "tool_call_id", "") or ""
                        started = tool_started_at.pop(call_id, None)
                        trace.note_tool_end(call_id)
                        duration = int((time.monotonic() - started) * 1000) if started else 0
                        status: ToolStatus = (
                            "error" if getattr(message, "status", None) == "error" else "ok"
                        )
                        for event in turn.tool_end(
                            call_id=call_id,
                            status=status,
                            result=content_text(getattr(message, "content", "")),
                            duration_ms=duration,
                        ):
                            yield sse(event)
                        continue
                    if not isinstance(message, AIMessage):
                        continue
                    # 正文/思考既然已经流式出过，就不再回放整条；工具调用永远走这里。
                    if subagent is None and not saw_delta:
                        for kind, piece in block_fragments(message):
                            events = (
                                turn.reasoning(piece) if kind == "reasoning" else turn.text(piece)
                            )
                            for event in events:
                                yield sse(event)
                    for call in getattr(message, "tool_calls", None) or []:
                        call_id = str(call.get("id") or uuid.uuid4().hex)
                        name = str(call.get("name") or "")
                        args = call.get("args") or {}
                        tool_started_at[call_id] = time.monotonic()
                        if subagent is None and name == _TASK_TOOL:
                            trace.note_task_call(
                                call_id=call_id,
                                subagent_type=str(args.get("subagent_type") or ""),
                            )
                        summary = definition.summarize_tool_call(
                            session,
                            owner_id=owner_id,
                            name=name,
                            args=args,
                        )
                        for event in turn.tool_start(
                            call_id=call_id,
                            name=name,
                            args=args,
                            summary=summary,
                            agent=subagent.name if subagent else None,
                            parent_call_id=subagent.call_id if subagent else None,
                        ):
                            yield sse(event)

        for event in turn.finish():
            yield sse(event)

        assistant_text = turn.text_content or (interrupt_question or "")
        blocks = turn.payload()
        if assistant_text or blocks:
            conversation.add_message(
                session,
                conversation_id=conversation_id,
                role="assistant",
                content=assistant_text,
                blocks=blocks or None,
            )
        # 中断期间挂起的工具块活在上一轮的消息里，拿到结果后回填，别让它一直转圈。
        for call_id, resolution in turn.orphan_tools.items():
            conversation.resolve_tool_block(
                session,
                conversation_id=conversation_id,
                call_id=call_id,
                status=resolution["status"],
                result=resolution["result"],
                duration_ms=resolution["durationMs"],
            )
        session.commit()
        state_payload = definition.build_state_update(session, settings, run)
        # 定义层只产出一轮收尾状态；是否落库、何时提交由运行时决定。
        session.commit()
        yield sse(StateUpdateEvent(**state_payload))
        # paused 必须显式给出来：否则前端分不清「本轮做完」和「卡在中途等人回答」，
        # 两个都是 run.end，却要走向完全不同的交互。
        yield sse(RunEndEvent(messageId=message_id, paused=paused))
    except Exception as exc:
        logger.exception(
            "agent run failed: conversation=%s run=%s",
            conversation_id,
            run_id,
        )
        session.rollback()
        message = user_facing_run_error(exc)
        if run is not None:
            try:
                conversation.add_message(
                    session,
                    conversation_id=conversation_id,
                    role="assistant",
                    content=message,
                    kind="error",
                )
                session.commit()
            except Exception:
                session.rollback()
        yield sse(RunErrorEvent(message=message))
    finally:
        session.close()
