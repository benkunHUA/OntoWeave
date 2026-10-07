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

OP_CREATE = "create"
OP_UPDATE = "update"
OP_DELETE = "delete"
VALID_OPS = {OP_CREATE, OP_UPDATE, OP_DELETE}

_ELEMENT_MODELS = {
    "object_type": ObjectType,
    "relation": Relation,
    "property": Property,
}

# 撤销时允许还原的字段白名单。key 是元素种类，值是字段集合。
# 不在白名单里的键一律拒绝——否则调用方可以通过 payload 把 id、owner_id、
# status 这类字段写回去，绕过状态机。
_RESTORABLE_FIELDS = {
    "object_type": {"name", "description"},
    "property": {"name", "datatype", "required"},
    "relation": {"name", "direction"},
}


def create_changeset(
    session: Session, *, graph_id: uuid.UUID, owner_id: uuid.UUID | None = None
) -> Changeset:
    graph.get_graph(session, graph_id, owner_id=owner_id)
    cs = Changeset(graph_id=graph_id, status=OPEN)
    session.add(cs)
    session.flush()
    return cs


def get_changeset(
    session: Session, changeset_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> Changeset:
    cs = session.get(Changeset, changeset_id)
    if cs is None:
        raise NotFoundError(f"changeset {changeset_id} not found")
    if owner_id is not None:
        graph.get_graph(session, cs.graph_id, owner_id=owner_id)
    return cs


def list_changesets(
    session: Session,
    *,
    graph_id: uuid.UUID,
    owner_id: uuid.UUID | None = None,
    limit: int = 50,
    offset: int = 0,
) -> list[Changeset]:
    graph.get_graph(session, graph_id, owner_id=owner_id)
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
    owner_id: uuid.UUID | None = None,
) -> ChangesetOp:
    cs = get_changeset(session, changeset_id, owner_id=owner_id)
    if cs.status != OPEN:
        raise InvalidTransitionError(
            f"changeset {changeset_id} is {cs.status}, cannot record new ops"
        )
    _validate_op(op, target_id, payload or {})
    row = ChangesetOp(
        changeset_id=changeset_id,
        op=op,
        target_id=target_id,
        payload=payload or {},
    )
    session.add(row)
    session.flush()
    return row


def _validate_op(op: str, target_id: uuid.UUID | None, payload: dict) -> None:
    """在记录阶段就把不合法的操作挡掉，别留到确认时才发现。"""
    if op not in VALID_OPS:
        raise InvariantViolationError(f"unsupported changeset op: {op!r}")
    if target_id is None:
        raise InvariantViolationError(f"changeset op {op!r} requires a target id")
    kind = payload.get("kind")
    if kind not in _ELEMENT_MODELS:
        raise InvariantViolationError(
            f"changeset op {op!r} requires a known target kind, got {kind!r}"
        )
    if op == OP_UPDATE:
        for field in payload.get("before", {}):
            if field not in _RESTORABLE_FIELDS[kind]:
                raise InvariantViolationError(
                    f"changeset op cannot restore field {field!r} on {kind}"
                )


def _op_kind(op: ChangesetOp) -> str:
    kind = op.payload.get("kind")
    if not isinstance(kind, str):
        raise InvariantViolationError(f"changeset op {op.id} is missing a target kind")
    return kind


def _op_target(op: ChangesetOp) -> uuid.UUID:
    if op.target_id is None:
        raise InvariantViolationError(f"changeset op {op.id} is missing a target id")
    return op.target_id


def _element_owner_id(session: Session, kind: str, element) -> uuid.UUID | None:
    if hasattr(element, "owner_id"):
        return element.owner_id
    if kind == "property":
        owner_type = session.get(ObjectType, element.owner_type_id)
        return owner_type.owner_id if owner_type is not None else None
    return None


def _load_element(
    session: Session,
    kind: str,
    element_id: uuid.UUID,
    *,
    owner_id: uuid.UUID | None = None,
):
    model = _ELEMENT_MODELS.get(kind)
    if model is None:
        raise InvariantViolationError(f"unsupported changeset op target: {kind!r}")
    element = session.get(model, element_id)
    if element is None:
        raise NotFoundError(f"{kind} {element_id} not found")
    if owner_id is not None and _element_owner_id(session, kind, element) != owner_id:
        raise NotFoundError(f"{kind} {element_id} not found")
    return element


def _promote(
    session: Session,
    kind: str,
    element_id: uuid.UUID,
    *,
    owner_id: uuid.UUID | None = None,
) -> None:
    if kind == "object_type":
        ontology.transition_object_type(session, element_id, "confirmed", owner_id=owner_id)
    elif kind == "relation":
        ontology.transition_relation(session, element_id, "confirmed", owner_id=owner_id)
    elif kind == "property":
        ontology.transition_property(session, element_id, "confirmed", owner_id=owner_id)
    else:
        raise InvariantViolationError(f"unsupported changeset op target: {kind!r}")


def confirm_changeset(
    session: Session, changeset_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> Changeset:
    """确认：新建的元素翻成已确认，标记删除的元素真正删掉。

    先把每条操作校验干净——种类合法、目标存在、状态允许流转——再落动作，
    这样动作阶段不会因为合法性问题中途失败。
    """
    cs = get_changeset(session, changeset_id, owner_id=owner_id)
    if cs.status != OPEN:
        raise InvalidTransitionError(f"changeset {changeset_id} is already {cs.status}")

    ops = list_ops(session, cs.id)
    for op in ops:
        _validate_op(op.op, op.target_id, op.payload)
        kind = _op_kind(op)
        target_id = _op_target(op)
        element = _load_element(session, kind, target_id, owner_id=owner_id)
        if op.op == OP_CREATE and element.status != ontology.DRAFT:
            raise InvalidTransitionError(
                f"{kind} {target_id} is {element.status}, cannot be confirmed from here"
            )

    for op in ops:
        if op.op == OP_CREATE:
            _promote(session, _op_kind(op), _op_target(op), owner_id=owner_id)
        elif op.op == OP_UPDATE:
            pass  # 值在草稿阶段已经写好，确认时无需再动
        elif op.op == OP_DELETE:
            session.delete(_load_element(session, _op_kind(op), _op_target(op), owner_id=owner_id))

    cs.status = CONFIRMED
    session.flush()
    return cs


def reject_changeset(
    session: Session, changeset_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> Changeset:
    """撤销：删掉本次新建的元素、把改过的还原；标记删除的本来就没删，无事发生。"""
    cs = get_changeset(session, changeset_id, owner_id=owner_id)
    if cs.status != OPEN:
        raise InvalidTransitionError(f"changeset {changeset_id} is already {cs.status}")

    ops = list_ops(session, cs.id)
    for op in ops:
        _validate_op(op.op, op.target_id, op.payload)
        _load_element(session, _op_kind(op), _op_target(op), owner_id=owner_id)

    for op in reversed(ops):
        if op.op == OP_CREATE:
            session.delete(_load_element(session, _op_kind(op), _op_target(op), owner_id=owner_id))
        elif op.op == OP_UPDATE:
            element = _load_element(session, _op_kind(op), _op_target(op), owner_id=owner_id)
            for field, value in op.payload.get("before", {}).items():
                setattr(element, field, value)
        elif op.op == OP_DELETE:
            pass  # 草稿阶段没真删

    cs.status = REJECTED
    session.flush()
    return cs
