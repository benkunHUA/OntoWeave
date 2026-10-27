"""助手消息的内容块：思考 / 正文 / 通知 / 工具调用 / 中断。

这些块既落进 `conversation_message.blocks`（JSONB），也直接走 SSE 推给前端，前端照
同一份形状渲染，所以形状在这里定义一份。字段名沿用线格式的 camelCase——JSONB 里存的、
接口返回的、前端读的都是这个形状，不为了 Python 侧好看多套一层名字映射。

落库与推送都用 `dump_block`（`exclude_unset`）：没显式给的字段不进 JSON，形状与这套
类型引入之前逐字节一致。
"""

from __future__ import annotations

from typing import Annotated, Any, Literal

from pydantic import BaseModel, ConfigDict, Field

ToolStatus = Literal["running", "ok", "error"]


class InterruptOption(BaseModel):
    """中断卡片上的一个选项：label 给人看，answer 原样回填给图。"""

    label: str
    answer: Any


class InterruptDetail(BaseModel):
    """确认卡片里的补充信息，比如候选类型的名称与描述。"""

    label: str
    value: str


class FreeTextMode(BaseModel):
    """自由文本怎么回填：raw 直接给字符串，respond 包成工具审批的 respond 决策。"""

    wrap: Literal["raw", "respond"]


class _Block(BaseModel):
    model_config = ConfigDict(extra="allow")

    id: str
    kind: str
    done: bool | None = None


class TextBlock(_Block):
    kind: Literal["text"] = "text"
    text: str = ""


class ReasoningBlock(_Block):
    kind: Literal["reasoning"] = "reasoning"
    text: str = ""


class NoticeBlock(_Block):
    """前端给用户的一行提示；后端目前不产出，留着对齐前端联合类型。"""

    kind: Literal["notice"] = "notice"
    text: str


class ToolBlock(_Block):
    kind: Literal["tool"] = "tool"
    callId: str
    name: str
    args: dict = Field(default_factory=dict)
    status: ToolStatus = "running"
    result: str | None = None
    truncated: bool = False
    durationMs: int | None = None
    # 面向用户的一行补充说明，例如“客户 · 搜索「张三」”；机器参数仍留在 args。
    summary: str | None = None
    # 子智能体内部的工具调用：agent 是子智能体名，parentCallId 是发起它的那次 `task` 调用。
    # 主智能体自己的调用不带这两个字段。
    agent: str | None = None
    parentCallId: str | None = None


class InterruptBlock(_Block):
    kind: Literal["interrupt"] = "interrupt"
    question: str
    options: list[InterruptOption] = Field(default_factory=list)
    freeText: FreeTextMode | None = None
    details: list[InterruptDetail] = Field(default_factory=list)


MessageBlock = Annotated[
    TextBlock | ReasoningBlock | NoticeBlock | ToolBlock | InterruptBlock,
    Field(discriminator="kind"),
]


def dump_block(block: MessageBlock) -> dict:
    """转成落库 / 推送用的 dict：只带调用方显式给过的字段，判别键 kind 始终在。"""
    return {"kind": block.kind, **block.model_dump(exclude_unset=True)}
