"""改动集定稿的摘要、元素描述与会话回执。"""

from __future__ import annotations

import uuid
from collections import Counter
from collections.abc import Callable
from dataclasses import dataclass

from sqlalchemy.orm import Session

from app.models import ActionParameter, ActionType, ChangesetOp, ObjectType, Property, Relation
from app.services.changeset.common import OP_CREATE, OP_DELETE, OP_UPDATE, OP_VIEW, VIEW_KIND
from app.services.element_kinds.types import (
    ACTION_PARAMETER,
    ACTION_TYPE,
    ELEMENT_KIND_LABELS,
)

CONFIRM = "confirm"
REVERT = "revert"

#: 确认 / 撤销在会话里会落成这种块，前端据此渲染成一条低调的系统提示。
NOTICE_BLOCK_KIND = "notice"

# 操作类型的中文说法。视图改动不是本体，但撤销时同样会被翻回去，一并报出来。
_OP_LABELS = {
    OP_CREATE: "新增",
    OP_UPDATE: "修改",
    OP_DELETE: "删除",
    OP_VIEW: "视图调整",
}
# 视图改动没有具体元素名字，回写线程时用这一行代替。
_VIEW_LINE = "调整视图"

_OP_ORDER = (
    OP_CREATE,
    OP_UPDATE,
    OP_DELETE,
    OP_VIEW,
)

_HEADLINES = {
    CONFIRM: "已确认本次改动",
    REVERT: "已撤销本次改动",
}

# 写回模型线程的开场与收尾：让模型知道该以什么为准，别再引用已撤销的内容。
_THREAD_HEADS = {
    CONFIRM: "（系统回执）用户已确认本次改动，以下内容已生效：",
    REVERT: "（系统回执）用户已撤销本次改动，以下内容已回退：",
}
_THREAD_TAILS = {
    CONFIRM: "后续请以当前图为准。",
    REVERT: "这些对象、关系和属性已从图中移除，后续请以当前图为准，不要再引用它们。",
}


@dataclass(frozen=True)
class Notice:
    """一次定稿要在会话里留下的痕迹：展示用的回执 + 写回模型线程时要用到的定位信息。"""

    conversation_id: uuid.UUID
    graph_id: uuid.UUID
    changeset_id: uuid.UUID
    #: 写回模型线程的旁白文本（已带系统口径，不是展示用的内容）。
    text: str


#: 把 Notice 写回模型线程的钩子。由接口层注入，服务层不感知 agent。
ThreadNotifier = Callable[[Session, Notice], None]


def summarize(decision: str, ops: list[ChangesetOp]) -> str:
    """把改动集的操作压成一句话，例如「已撤销本次改动：新增 2、修改 1。」"""
    counts = Counter(op.op for op in ops if op.op in _OP_LABELS)
    parts = [f"{_OP_LABELS[name]} {counts[name]}" for name in _OP_ORDER if counts[name]]
    headline = _HEADLINES.get(decision, "本次改动已处理")
    if not parts:
        return f"{headline}。"
    return f"{headline}：{'、'.join(parts)}。"


def describe_ops(session: Session, ops: list[ChangesetOp]) -> list[str]:
    """把每条操作写成一行带具体名字的说明，供写回模型线程。

    必须在确认 / 撤销**之前**调用：确认会真删「标记删除」的元素，撤销会删掉本次
    新建的元素，之后再查就只剩 id 了，名字已经不在库里。
    """
    lines: list[str] = []
    for op in ops:
        kind = op.payload.get("kind")
        if kind == VIEW_KIND:
            lines.append(_VIEW_LINE)
            continue
        label = _OP_LABELS.get(op.op, op.op)
        target = _describe_target(session, kind, op.target_id)
        if op.op == OP_UPDATE:
            target = f"{target}（{_describe_changes(op)}）"
        lines.append(f"{label} {target}")
    return lines


def _describe_target(session: Session, kind: str | None, target_id: uuid.UUID | None) -> str:
    label = ELEMENT_KIND_LABELS.get(kind or "", kind or "元素")
    if target_id is None:
        return label
    if kind == "object_type":
        obj = session.get(ObjectType, target_id)
        return f"{label}「{obj.name}」" if obj is not None else f"{label}（已不存在）"
    if kind == "property":
        prop = session.get(Property, target_id)
        if prop is None:
            return f"{label}（已不存在）"
        owner = session.get(ObjectType, prop.owner_type_id)
        return (
            f"{owner.name} 的{label}「{prop.name}」"
            if owner is not None
            else f"{label}「{prop.name}」"
        )
    if kind == ACTION_TYPE:
        action_type = session.get(ActionType, target_id)
        return (
            f"{label}「{action_type.name}」" if action_type is not None else f"{label}（已不存在）"
        )
    if kind == ACTION_PARAMETER:
        parameter = session.get(ActionParameter, target_id)
        return f"{label}「{parameter.name}」" if parameter is not None else f"{label}（已不存在）"
    if kind == "relation":
        rel = session.get(Relation, target_id)
        if rel is None:
            return f"{label}（已不存在）"
        source = session.get(ObjectType, rel.source_type_id)
        target = session.get(ObjectType, rel.target_type_id)
        pair = (
            f"（{source.name} → {target.name}）"
            if source is not None and target is not None
            else ""
        )
        return f"{label}「{rel.name}」{pair}"
    return label


def _describe_changes(op: ChangesetOp) -> str:
    before = op.payload.get("before") or {}
    after = op.payload.get("after") or {}
    return "、".join(f"{field}：{before.get(field)} → {after.get(field)}" for field in after)


def _thread_note(decision: str, detail: list[str], summary: str) -> str:
    tail = _THREAD_TAILS.get(decision, "")
    if not detail:
        return f"{summary}{tail}"
    head = _THREAD_HEADS.get(decision, summary)
    body = "".join(f"\n- {line}" for line in detail)
    return f"{head}{body}\n{tail}"
