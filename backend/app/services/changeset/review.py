"""改动集定稿（确认 / 撤销）与它的对话回执。"""

from __future__ import annotations

import logging
import uuid

from sqlalchemy.orm import Session

from app.models import ActionType, Changeset, ObjectType, Property
from app.services import conversation, embedding, english_names
from app.services.changeset.common import OP_DELETE, OP_VIEW
from app.services.changeset.lifecycle import confirm_changeset, reject_changeset
from app.services.changeset.notice import (
    CONFIRM,
    NOTICE_BLOCK_KIND,
    REVERT,
    Notice,
    ThreadNotifier,
    _thread_note,
    describe_ops,
    summarize,
)
from app.services.changeset.store import get_changeset, list_ops
from app.services.element_kinds.types import ACTION_TYPE, OBJECT_TYPE, PROPERTY

logger = logging.getLogger(__name__)


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
    cs = confirm_changeset(session, changeset_id, owner_id=owner_id)
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
    cs = reject_changeset(session, changeset_id, owner_id=owner_id)
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
    for op in list_ops(session, changeset_id):
        kind = op.payload.get("kind")
        if op.target_id is None:
            continue
        if op.op in (OP_DELETE, OP_VIEW):
            continue
        if kind == OBJECT_TYPE and session.get(ObjectType, op.target_id) is not None:
            embedding.try_refresh_object_type_embedding(
                session, object_type_id=op.target_id, embedder=embedder
            )
        elif kind == ACTION_TYPE and session.get(ActionType, op.target_id) is not None:
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
    for op in list_ops(session, changeset_id):
        if op.target_id is None:
            continue
        kind = op.payload.get("kind")
        if kind == OBJECT_TYPE:
            object_type_ids.add(op.target_id)
        elif kind == PROPERTY:
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
    cs = get_changeset(session, changeset_id, owner_id=owner_id)
    ops = list_ops(session, changeset_id)
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
    summary = summarize(decision, list_ops(session, changeset.id))
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
