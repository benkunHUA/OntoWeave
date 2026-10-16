"""一轮运行的流式产出：SSE 帧与消息内容块。

协议的形状定义在 `app.schemas`（agent_events / message_blocks），这里负责按协议**产出**：
模型的流式增量怎么折成有序内容块，块怎么变成事件。块一旦切换类型就收口，所以思考 /
正文 / 工具调用天然按时间交错，前端收到的顺序就是最终落库的消息结构。
"""

from __future__ import annotations

import json
import uuid
from typing import Any

from langchain_core.messages import BaseMessage

from app.schemas.agent_events import (
    PROTOCOL_VERSION,
    AgentEvent,
    BlockDeltaEvent,
    BlockEndEvent,
    BlockStartEvent,
    InterruptEvent,
    ToolEndEvent,
    ToolStartEvent,
)
from app.schemas.message_blocks import (
    FreeTextMode,
    InterruptBlock,
    InterruptDetail,
    InterruptOption,
    MessageBlock,
    ReasoningBlock,
    TextBlock,
    ToolBlock,
    ToolStatus,
    dump_block,
)

# 工具结果进 SSE 和入库前都截断，避免长 JSON 撑爆消息。
TOOL_RESULT_LIMIT = 2000

__all__ = [
    "PROTOCOL_VERSION",
    "TOOL_RESULT_LIMIT",
    "TurnBuilder",
    "block_fragments",
    "content_text",
    "dump_block",
    "jsonable",
    "sse",
]


def sse(event: AgentEvent) -> str:
    """一条 SSE 帧：带上协议版本，逐字对应前端解析的那行 `data: {...}`。"""
    payload = {"v": PROTOCOL_VERSION, "type": event.type, **event.model_dump(exclude_unset=True)}
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


def jsonable(value: Any) -> Any:
    """把 LLM 给的参数压成一定能落库、能序列化的形状。"""
    try:
        return json.loads(json.dumps(value, default=str, ensure_ascii=False))
    except (TypeError, ValueError):
        return str(value)


def content_text(content: Any) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict) and isinstance(item.get("text"), str):
                parts.append(item["text"])
        return "".join(parts)
    return "" if content is None else str(content)


def block_fragments(message: BaseMessage) -> list[tuple[str, str]]:
    """按出现顺序取出消息里的思考 / 正文片段。

    用 langchain 统一后的 content block，provider 差异（DeepSeek 的
    reasoning_content 等）已经被它归一成 reasoning 块，这里不用再分辨。
    工具调用不放这儿，交给 updates 统一处理，免得两条流各算一遍。
    """
    fragments: list[tuple[str, str]] = []
    for block in getattr(message, "content_blocks", None) or []:
        if not isinstance(block, dict):
            continue
        kind = block.get("type")
        if kind == "reasoning":
            piece = block.get("reasoning") or block.get("text")
        elif kind == "text":
            piece = block.get("text")
        else:
            continue
        if isinstance(piece, str) and piece:
            fragments.append((kind, piece))
    return fragments


class TurnBuilder:
    """把一轮回复攒成有序的内容块，同时吐出对应的 SSE 事件。

    块一旦切换类型就收口，因此思考 / 正文 / 工具调用天然按时间交错，
    前端拿到的事件顺序就是最终的消息结构。
    """

    def __init__(self, message_id: str) -> None:
        self.message_id = message_id
        self.blocks: list[MessageBlock] = []
        self._open: TextBlock | ReasoningBlock | None = None
        self._tools: dict[str, ToolBlock] = {}
        # 中断挂起的调用要到下一轮才有 ToolMessage，本轮找不到起始块，先攒着。
        self.orphan_tools: dict[str, dict] = {}

    def _close_open(self) -> list[AgentEvent]:
        if self._open is None:
            return []
        block, self._open = self._open, None
        if isinstance(block, ReasoningBlock):
            block.done = True
        return [BlockEndEvent(messageId=self.message_id, blockId=block.id)]

    def _append(self, kind: str, text: str) -> list[AgentEvent]:
        events: list[AgentEvent] = []
        if not isinstance(self._open, TextBlock | ReasoningBlock) or self._open.kind != kind:
            events += self._close_open()
            block = (
                ReasoningBlock(id=uuid.uuid4().hex)
                if kind == "reasoning"
                else TextBlock(id=uuid.uuid4().hex)
            )
            self.blocks.append(block)
            self._open = block
            events.append(
                BlockStartEvent(messageId=self.message_id, blockId=block.id, kind=block.kind)
            )
        self._open.text += text
        events.append(BlockDeltaEvent(messageId=self.message_id, blockId=self._open.id, text=text))
        return events

    def reasoning(self, text: str) -> list[AgentEvent]:
        return self._append("reasoning", text)

    def text(self, text: str) -> list[AgentEvent]:
        return self._append("text", text)

    def tool_start(
        self,
        *,
        call_id: str,
        name: str,
        args: Any,
        agent: str | None = None,
        parent_call_id: str | None = None,
    ) -> list[AgentEvent]:
        """起一个工具块；子智能体内部的调用带上 agent / parent_call_id 好让前端叠起来。"""
        events = self._close_open()
        block = ToolBlock(
            id=uuid.uuid4().hex,
            callId=call_id,
            name=name,
            args=jsonable(args),
            status="running",
            result=None,
            truncated=False,
            durationMs=None,
            agent=agent,
            parentCallId=parent_call_id,
        )
        self.blocks.append(block)
        self._tools[call_id] = block
        events.append(
            ToolStartEvent(
                messageId=self.message_id,
                callId=call_id,
                name=name,
                args=block.args,
                agent=agent,
                parentCallId=parent_call_id,
            )
        )
        return events

    def tool_end(
        self, *, call_id: str, status: ToolStatus, result: str, duration_ms: int
    ) -> list[AgentEvent]:
        truncated = len(result) > TOOL_RESULT_LIMIT
        stored = result[:TOOL_RESULT_LIMIT]
        block = self._tools.get(call_id)
        if block is None:
            # 中断那轮只起了块、没等到结果；这一轮才拿到 ToolMessage。
            self.orphan_tools[call_id] = {
                "status": status,
                "result": stored,
                "durationMs": duration_ms or None,
            }
        else:
            block.status = status
            block.result = stored
            block.truncated = truncated
            block.durationMs = duration_ms
        return [
            ToolEndEvent(
                messageId=self.message_id,
                callId=call_id,
                status=status,
                result=stored,
                truncated=truncated,
                durationMs=duration_ms,
            )
        ]

    def finish(self) -> list[AgentEvent]:
        return self._close_open()

    def interrupt(
        self,
        *,
        question: str,
        options: list[InterruptOption],
        free_text: FreeTextMode | None,
        details: list[InterruptDetail],
    ) -> list[AgentEvent]:
        """中断也是一块：落库后刷新页面还能原样重建等待回答的状态。"""
        events = self._close_open()
        block = InterruptBlock(
            id=uuid.uuid4().hex,
            question=question,
            options=options,
            freeText=free_text,
            details=details,
        )
        self.blocks.append(block)
        events.append(
            InterruptEvent(
                messageId=self.message_id,
                blockId=block.id,
                question=block.question,
                options=block.options,
                freeText=block.freeText,
                details=block.details,
            )
        )
        return events

    def payload(self) -> list[dict]:
        """落库用的块（前端读回来要用同一份形状）。"""
        return [dump_block(block) for block in self.blocks]

    @property
    def text_content(self) -> str:
        return "".join(block.text for block in self.blocks if isinstance(block, TextBlock))
