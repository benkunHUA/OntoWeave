"""改动集定稿（确认 / 撤销）与它的对话回执。

改动集本身只记操作历史，用户读的是会话。这里把两者接上：确认或撤销落定后，
往对应会话追一条系统提示，别让「撤销了」在对话里悄无声息，用户回头也查得到。
同时把定稿结果通过 `notify` 钩子交给调用方——接口层再用它写回模型线程，免得
模型继续引用已经被撤销的对象和关系。

确认 = 提升草稿 + 回执；撤销 = 回滚草稿与视图 + 回执。调用方只认这两个入口，
免得将来有别的调用点漏掉回执、或者顺序写反。
"""

from __future__ import annotations

import logging
import uuid
from collections import Counter
from collections.abc import Callable
from dataclasses import dataclass

from sqlalchemy.orm import Session

from app.models import (
    ActionParameter,
    ActionType,
    Changeset,
    ChangesetOp,
    ObjectType,
    Property,
    Relation,
)
from app.services import changeset as changeset_service
from app.services import conversation, element_kinds, embedding, english_names

logger = logging.getLogger(__name__)

CONFIRM = "confirm"
REVERT = "revert"

#: 确认 / 撤销在会话里会落成这种块，前端据此渲染成一条低调的系统提示。
NOTICE_BLOCK_KIND = "notice"

# 操作类型的中文说法。视图改动不是本体，但撤销时同样会被翻回去，一并报出来。
_OP_LABELS = {
    changeset_service.OP_CREATE: "新增",
    changeset_service.OP_UPDATE: "修改",
    changeset_service.OP_DELETE: "删除",
    changeset_service.OP_VIEW: "视图调整",
}
# 视图改动没有具体元素名字，回写线程时用这一行代替。
_VIEW_LINE = "调整视图"

_OP_ORDER = (
    changeset_service.OP_CREATE,
    changeset_service.OP_UPDATE,
    changeset_service.OP_DELETE,
    changeset_service.OP_VIEW,
)

# 元素种类的中文说法，写回模型线程时按名字点名，别只报数量。
_ELEMENT_LABELS = {
    element_kinds.OBJECT_TYPE: "对象类型",
    element_kinds.PROPERTY: "属性",
    element_kinds.RELATION: "关系",
    element_kinds.ACTION_TYPE: "动作",
    element_kinds.ACTION_PARAMETER: "动作参数",
    element_kinds.ACTION_RULE: "动作规则",
    element_kinds.ACTION_RULE_ASSIGNMENT: "字段赋值",
    element_kinds.ACTION_CRITERIA_NODE: "提交校验条件",
    element_kinds.ACTION_CRITERIA_OPERAND: "条件操作数",
}

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
        if kind == changeset_service.VIEW_KIND:
            lines.append(_VIEW_LINE)
            continue
        label = _OP_LABELS.get(op.op, op.op)
        target = _describe_target(session, kind, op.target_id)
        if op.op == changeset_service.OP_UPDATE:
            target = f"{target}（{_describe_changes(op)}）"
        lines.append(f"{label} {target}")
    return lines


def _describe_target(session: Session, kind: str | None, target_id: uuid.UUID | None) -> str:
    label = _ELEMENT_LABELS.get(kind or "", kind or "元素")
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
    if kind == element_kinds.ACTION_TYPE:
        action_type = session.get(ActionType, target_id)
        return (
            f"{label}「{action_type.name}」" if action_type is not None else f"{label}（已不存在）"
        )
    if kind == element_kinds.ACTION_PARAMETER:
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


def confirm(
    session: Session,
    changeset_id: uuid.UUID,
    *,
    owner_id: uuid.UUID | None = None,
    notify: ThreadNotifier | None = None,
    embedder: embedding.Embedder | None = None,
    name_generator: english_names.EnglishNameGenerator | None = None,
) -> Changeset:
    _, detail = _inspect(session, changeset_id, owner_id=owner_id)
    cs = changeset_service.confirm_changeset(session, changeset_id, owner_id=owner_id)
    _refresh_english_names(session, changeset_id, name_generator)
    _refresh_embeddings(session, changeset_id, embedder)
    _record(session, cs, CONFIRM, detail=detail, owner_id=owner_id, notify=notify)
    return cs


def revert(
    session: Session,
    changeset_id: uuid.UUID,
    *,
    owner_id: uuid.UUID | None = None,
    notify: ThreadNotifier | None = None,
    embedder: embedding.Embedder | None = None,
) -> Changeset:
    _, detail = _inspect(session, changeset_id, owner_id=owner_id)
    cs = changeset_service.reject_changeset(session, changeset_id, owner_id=owner_id)
    _refresh_embeddings(session, changeset_id, embedder)
    _record(session, cs, REVERT, detail=detail, owner_id=owner_id, notify=notify)
    return cs


def _refresh_embeddings(
    session: Session, changeset_id: uuid.UUID, embedder: embedding.Embedder | None
) -> None:
    """定稿后让向量与确认状态对齐。

    确认：本次新建的对象转成 confirmed，这时才补向量；撤销：被改回来的对象
    重新算一遍。被删掉（确认时真删、撤销时删掉本次新建）的对象已经查不到，
    直接跳过；删除操作不改文本，也不用重算。算向量是尽力而为——失败只记日志，
    不回滚已经定稿的本体改动。
    """
    if embedder is None:
        return
    for op in changeset_service.list_ops(session, changeset_id):
        kind = op.payload.get("kind")
        if op.target_id is None:
            continue
        if op.op in (changeset_service.OP_DELETE, changeset_service.OP_VIEW):
            continue
        if kind == element_kinds.OBJECT_TYPE and session.get(ObjectType, op.target_id) is not None:
            embedding.try_refresh_object_type_embedding(
                session, object_type_id=op.target_id, embedder=embedder
            )
        elif (
            kind == element_kinds.ACTION_TYPE and session.get(ActionType, op.target_id) is not None
        ):
            embedding.try_refresh_action_type_embedding(
                session, action_type_id=op.target_id, embedder=embedder
            )


def _refresh_english_names(
    session: Session,
    changeset_id: uuid.UUID,
    generator: english_names.EnglishNameGenerator | None,
) -> None:
    """只补本次涉及对象里仍为空的英文名；已有值不回写，也不看中文名是否改过。"""
    object_type_ids: set[uuid.UUID] = set()
    for op in changeset_service.list_ops(session, changeset_id):
        if op.target_id is None:
            continue
        kind = op.payload.get("kind")
        if kind == element_kinds.OBJECT_TYPE:
            object_type_ids.add(op.target_id)
        elif kind == element_kinds.PROPERTY:
            prop = session.get(Property, op.target_id)
            if prop is not None:
                object_type_ids.add(prop.owner_type_id)
    for object_type_id in object_type_ids:
        obj = session.get(ObjectType, object_type_id)
        if obj is not None:
            english_names.fill_object_english_names(session, object_type=obj, generator=generator)


def _inspect(
    session: Session, changeset_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> tuple[Changeset, list[str]]:
    """定稿前先取快照并解析名字，动作之后元素可能就查不到了。"""
    cs = changeset_service.get_changeset(session, changeset_id, owner_id=owner_id)
    ops = changeset_service.list_ops(session, changeset_id)
    return cs, describe_ops(session, ops)


def _record(
    session: Session,
    changeset: Changeset,
    decision: str,
    *,
    detail: list[str],
    owner_id: uuid.UUID | None = None,
    notify: ThreadNotifier | None = None,
) -> None:
    """往会话追一条系统提示，并通过钩子写回模型线程。

    找不到会话就静默跳过——回执是锦上添花，别拖垮主流程。
    """
    conv = conversation.find_conversation_by_graph(session, changeset.graph_id, owner_id=owner_id)
    if conv is None:
        return
    summary = summarize(decision, changeset_service.list_ops(session, changeset.id))
    conversation.add_message(
        session,
        conversation_id=conv.id,
        role="assistant",
        content=summary,
        blocks=[{"id": uuid.uuid4().hex, "kind": NOTICE_BLOCK_KIND, "text": summary}],
    )
    if notify is not None:
        notify(
            session,
            Notice(
                conversation_id=conv.id,
                graph_id=changeset.graph_id,
                changeset_id=changeset.id,
                text=_thread_note(decision, detail, summary),
            ),
        )


def _thread_note(decision: str, detail: list[str], summary: str) -> str:
    tail = _THREAD_TAILS.get(decision, "")
    if not detail:
        return f"{summary}{tail}"
    head = _THREAD_HEADS.get(decision, summary)
    body = "".join(f"\n- {line}" for line in detail)
    return f"{head}{body}\n{tail}"
