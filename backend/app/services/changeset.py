from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvalidTransitionError, InvariantViolationError, NotFoundError
from app.models import Changeset, ChangesetOp, ObjectType, Property, Relation
from app.services import graph, ontology

OPEN = "open"
CONFIRMED = "confirmed"
REJECTED = "rejected"

_ELEMENT_MODELS = {
    "object_type": ObjectType,
    "relation": Relation,
    "property": Property,
}


def create_changeset(session: Session, *, graph_id: uuid.UUID) -> Changeset:
    graph.get_graph(session, graph_id)
    cs = Changeset(graph_id=graph_id, status=OPEN)
    session.add(cs)
    session.flush()
    return cs


def get_changeset(session: Session, changeset_id: uuid.UUID) -> Changeset:
    cs = session.get(Changeset, changeset_id)
    if cs is None:
        raise NotFoundError(f"changeset {changeset_id} not found")
    return cs


def list_changesets(
    session: Session, *, graph_id: uuid.UUID, limit: int = 50, offset: int = 0
) -> list[Changeset]:
    return list(
        session.scalars(
            select(Changeset)
            .where(Changeset.graph_id == graph_id)
            .order_by(Changeset.created_at.desc(), Changeset.id)
            .limit(limit)
            .offset(offset)
        )
    )


def list_ops(session: Session, changeset_id: uuid.UUID) -> list[ChangesetOp]:
    return list(
        session.scalars(
            select(ChangesetOp)
            .where(ChangesetOp.changeset_id == changeset_id)
            .order_by(ChangesetOp.created_at, ChangesetOp.id)
        )
    )


def record_op(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    op: str,
    target_id: uuid.UUID | None = None,
    payload: dict | None = None,
) -> ChangesetOp:
    cs = get_changeset(session, changeset_id)
    if cs.status != OPEN:
        raise InvalidTransitionError(
            f"changeset {changeset_id} is {cs.status}, cannot record new ops"
        )
    row = ChangesetOp(
        changeset_id=changeset_id,
        op=op,
        target_id=target_id,
        payload=payload or {},
    )
    session.add(row)
    session.flush()
    return row


def _op_kind(op: ChangesetOp) -> str:
    kind = op.payload.get("kind")
    if not isinstance(kind, str):
        raise InvariantViolationError(f"changeset op {op.id} is missing a target kind")
    return kind


def _op_target(op: ChangesetOp) -> uuid.UUID:
    if op.target_id is None:
        raise InvariantViolationError(f"changeset op {op.id} is missing a target id")
    return op.target_id


def _load_element(session: Session, kind: str, element_id: uuid.UUID):
    model = _ELEMENT_MODELS.get(kind)
    if model is None:
        raise InvariantViolationError(f"unsupported changeset op target: {kind!r}")
    element = session.get(model, element_id)
    if element is None:
        raise NotFoundError(f"{kind} {element_id} not found")
    return element


def _promote(session: Session, kind: str, element_id: uuid.UUID) -> None:
    if kind == "object_type":
        ontology.transition_object_type(session, element_id, "confirmed")
    elif kind == "relation":
        ontology.transition_relation(session, element_id, "confirmed")
    elif kind == "property":
        ontology.transition_property(session, element_id, "confirmed")
    else:
        raise InvariantViolationError(f"unsupported changeset op target: {kind!r}")


def confirm_changeset(session: Session, changeset_id: uuid.UUID) -> Changeset:
    """确认：新建的元素翻成已确认，标记删除的元素真正删掉。

    先做一遍全量校验再落动作，避免改到一半失败留下半生不熟的状态。
    """
    cs = get_changeset(session, changeset_id)
    if cs.status != OPEN:
        raise InvalidTransitionError(f"changeset {changeset_id} is already {cs.status}")

    ops = list_ops(session, cs.id)
    for op in ops:
        _load_element(session, _op_kind(op), _op_target(op))

    for op in ops:
        if op.op == "create":
            _promote(session, _op_kind(op), _op_target(op))
        elif op.op == "update":
            pass  # 值在草稿阶段已经写好，确认时无需再动
        elif op.op == "delete":
            session.delete(_load_element(session, _op_kind(op), _op_target(op)))

    cs.status = CONFIRMED
    session.flush()
    return cs


def reject_changeset(session: Session, changeset_id: uuid.UUID) -> Changeset:
    """撤销：删掉本次新建的元素、把改过的还原；标记删除的本来就没删，无事发生。"""
    cs = get_changeset(session, changeset_id)
    if cs.status != OPEN:
        raise InvalidTransitionError(f"changeset {changeset_id} is already {cs.status}")

    for op in reversed(list_ops(session, cs.id)):
        if op.op == "create":
            session.delete(_load_element(session, _op_kind(op), _op_target(op)))
        elif op.op == "update":
            element = _load_element(session, _op_kind(op), _op_target(op))
            for field, value in op.payload.get("before", {}).items():
                setattr(element, field, value)
        elif op.op == "delete":
            pass  # 草稿阶段没真删

    cs.status = REJECTED
    session.flush()
    return cs
