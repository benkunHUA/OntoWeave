"""SSE 事件协议（后端一侧的定义，前端 `AgentEvent` 与它一一对应）。

一轮运行的所有事件都在这里：每加一种事件，前后端两份联合类型都要补一条。
加字段可以不动 PROTOCOL_VERSION，改已有字段的语义时必须抬。
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.message_blocks import FreeTextMode, InterruptDetail, InterruptOption, ToolStatus

# 前后端约定的输出协议版本。
PROTOCOL_VERSION = 1


class _Event(BaseModel):
    model_config = ConfigDict(extra="allow")


class RunStartEvent(_Event):
    type: Literal["run.start"] = "run.start"
    runId: str
    conversationId: str
    messageId: str


class BlockStartEvent(_Event):
    type: Literal["block.start"] = "block.start"
    messageId: str
    blockId: str
    kind: Literal["text", "reasoning"]


class BlockDeltaEvent(_Event):
    type: Literal["block.delta"] = "block.delta"
    messageId: str
    blockId: str
    text: str


class BlockEndEvent(_Event):
    type: Literal["block.end"] = "block.end"
    messageId: str
    blockId: str


class ToolStartEvent(_Event):
    type: Literal["tool.start"] = "tool.start"
    messageId: str
    callId: str
    name: str
    args: dict
    summary: str | None = None
    agent: str | None = None
    parentCallId: str | None = None


class ToolEndEvent(_Event):
    type: Literal["tool.end"] = "tool.end"
    messageId: str
    callId: str
    status: ToolStatus
    result: str
    truncated: bool
    durationMs: int


class InterruptEvent(_Event):
    type: Literal["interrupt"] = "interrupt"
    messageId: str
    blockId: str
    question: str
    options: list[InterruptOption]
    freeText: FreeTextMode | None
    details: list[InterruptDetail]


class StateUpdateEvent(_Event):
    type: Literal["state.update"] = "state.update"
    changesetId: str | None = None
    ops: list[dict] | None = None
    view: dict = Field(default_factory=dict)
    # 数据连接会话的连接状态摘要；建模会话不填。
    dataConnection: dict | None = None


class RunErrorEvent(_Event):
    type: Literal["run.error"] = "run.error"
    message: str


class RunEndEvent(_Event):
    """一轮运行收尾。`paused` 必须显式给：前端靠它区分「做完了」和「等人回答」。"""

    type: Literal["run.end"] = "run.end"
    messageId: str
    paused: bool


AgentEvent = Annotated[
    RunStartEvent
    | BlockStartEvent
    | BlockDeltaEvent
    | BlockEndEvent
    | ToolStartEvent
    | ToolEndEvent
    | InterruptEvent
    | StateUpdateEvent
    | RunErrorEvent
    | RunEndEvent,
    Field(discriminator="type"),
]
