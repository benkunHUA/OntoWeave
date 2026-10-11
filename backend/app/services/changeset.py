from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvalidTransitionError, InvariantViolationError, NotFoundError
from app.models import Changeset, ChangesetOp, ObjectType, Property, Relation
from app.services import graph, ontology, view_state

OPEN = "open"
CONFIRMED = "confirmed"
REJECTED = "rejected"

OP_CREATE = "create"
OP_UPDATE = "update"
OP_DELETE = "delete"
# 视图改动不进本体，但同样归改动集管：撤销时要能翻回去。
OP_VIEW = "view"
VALID_OPS = {OP_CREATE, OP_UPDATE, OP_DELETE}
VIEW_KIND = "view"

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


def _lock_changeset(
    session: Session, changeset_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> Changeset:
    """拿改动集行锁。

    record / confirm / reject 都从这里进入，保证「检查状态再写」是原子的。
    第二个并发请求会等锁，拿到锁后再看到最新状态，而不是基于旧快照继续写。
    """
    cs = session.scalar(
        select(Changeset)
        .where(Changeset.id == changeset_id)
        .with_for_update()
        .execution_options(populate_existing=True)
    )
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
    cs = _lock_changeset(session, changeset_id, owner_id=owner_id)
    if cs.status != OPEN:
        raise InvalidTransitionError(
            f"changeset {changeset_id} is {cs.status}, cannot record new ops"
        )
    _validate_op(op, target_id, payload or {})
    if op == OP_UPDATE:
        _apply_update_payload(session, target_id, payload or {}, owner_id=owner_id)
    row = ChangesetOp(
        changeset_id=changeset_id,
        op=op,
        target_id=target_id,
        payload=payload or {},
    )
    session.add(row)
    session.flush()
    return row


def record_view_change(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    user_id: uuid.UUID,
    before: dict,
    after: dict,
    owner_id: uuid.UUID | None = None,
) -> ChangesetOp:
    """记录绘图子智能体对视图状态的改动。

    视图状态本身不进本体，但它同样属于这一轮「待确认的改动」：撤销时要把
    它翻回改动前的样子，所以这里把前后快照整个存进改动集。
    """
    cs = _lock_changeset(session, changeset_id, owner_id=owner_id)
    if cs.status != OPEN:
        raise InvalidTransitionError(
            f"changeset {changeset_id} is {cs.status}, cannot record new ops"
        )
    payload = {
        "kind": VIEW_KIND,
        "user_id": str(user_id),
        "before": dict(before),
        "after": dict(after),
    }
    return _append_op(
        session,
        changeset_id=changeset_id,
        op=OP_VIEW,
        target_id=None,
        payload=payload,
    )


def record_update(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    element_kind: str,
    element_id: uuid.UUID,
    patch: dict,
    owner_id: uuid.UUID | None = None,
) -> ChangesetOp:
    """记录字段更新：调用方只给 patch，服务层负责采集 before 并应用 after。"""
    allowed = _RESTORABLE_FIELDS.get(element_kind)
    if allowed is None:
        raise InvariantViolationError(f"unsupported element kind: {element_kind!r}")
    if not patch:
        raise InvariantViolationError("update requires at least one patched field")
    unknown = set(patch) - allowed
    if unknown:
        raise InvariantViolationError(f"cannot patch fields: {sorted(unknown)}")

    cs = _lock_changeset(session, changeset_id, owner_id=owner_id)
    if cs.status != OPEN:
        raise InvalidTransitionError(
            f"changeset {changeset_id} is {cs.status}, cannot record new ops"
        )
    element = _load_element(session, element_kind, element_id, owner_id=owner_id)
    before = {field: getattr(element, field) for field in patch}
    payload = {
        "kind": element_kind,
        "before": before,
        "after": dict(patch),
    }
    _validate_op(OP_UPDATE, element_id, payload)
    _apply_update_payload(session, element_id, payload, owner_id=owner_id)
    return _append_op(
        session,
        changeset_id=changeset_id,
        op=OP_UPDATE,
        target_id=element_id,
        payload=payload,
    )


def _append_op(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    op: str,
    target_id: uuid.UUID | None,
    payload: dict,
) -> ChangesetOp:
    row = ChangesetOp(
        changeset_id=changeset_id,
        op=op,
        target_id=target_id,
        payload=payload,
    )
    session.add(row)
    session.flush()
    return row


def _apply_update_payload(
    session: Session,
    element_id: uuid.UUID | None,
    payload: dict,
    *,
    owner_id: uuid.UUID | None = None,
) -> None:
    if element_id is None:
        raise InvariantViolationError("update requires a target id")
    kind = payload["kind"]
    before = payload["before"]
    after = payload["after"]
    element = _load_element(session, kind, element_id, owner_id=owner_id)

    stale = {
        field: {"expected": expected, "actual": getattr(element, field)}
        for field, expected in before.items()
        if getattr(element, field) != expected
    }
    if stale:
        raise InvariantViolationError(
            f"{kind} {element_id} changed after the update snapshot was taken: {stale}"
        )

    for field, value in after.items():
        setattr(element, field, value)
    session.flush()


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
        before = payload.get("before")
        after = payload.get("after")
        if not isinstance(before, dict) or not before:
            raise InvariantViolationError("update requires a non-empty 'before' snapshot")
        if not isinstance(after, dict) or not after:
            raise InvariantViolationError("update requires a non-empty 'after' snapshot")
        if set(before) != set(after):
            raise InvariantViolationError(
                "update 'before' and 'after' snapshots must contain the same fields"
            )
        unknown = set(before) - _RESTORABLE_FIELDS[kind]
        if unknown:
            raise InvariantViolationError(
                f"changeset op cannot restore fields {sorted(unknown)} on {kind}"
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


# 删除时的阶段顺序：先子后父。relation / property 对 object_type 都是
# ON DELETE CASCADE——先删父行的话，数据库会把子行一起带走，ORM 再删一次就是
# 0 匹配（SAWarning）。按种类分阶段、每阶段 flush 一次，把 DELETE 顺序钉死。
_DELETE_PHASE_ORDER = ("relation", "property", "object_type")


def _delete_elements(
    session: Session,
    elements: list[tuple[str, uuid.UUID]],
    *,
    owner_id: uuid.UUID | None = None,
) -> None:
    """按「先子后父」删除一批元素，顺序稳定、不重复删被级联带走的行。"""
    for kind in _DELETE_PHASE_ORDER:
        pending = [
            (element_kind, element_id)
            for element_kind, element_id in elements
            if element_kind == kind
        ]
        for element_kind, element_id in pending:
            session.delete(_load_element(session, element_kind, element_id, owner_id=owner_id))
        if pending:
            session.flush()


def _validate_view_op(op: ChangesetOp) -> None:
    """视图改动没有本体目标：只有一份 before/after 快照和一个归属用户。"""
    if op.target_id is not None:
        raise InvariantViolationError(f"view op {op.id} must not carry a target id")
    if op.payload.get("kind") != VIEW_KIND:
        raise InvariantViolationError(f"view op {op.id} has the wrong kind")
    before = op.payload.get("before")
    after = op.payload.get("after")
    if not isinstance(before, dict) or not isinstance(after, dict):
        raise InvariantViolationError(f"view op {op.id} needs 'before' and 'after' specs")
    try:
        uuid.UUID(str(op.payload.get("user_id")))
    except (TypeError, ValueError) as exc:
        raise InvariantViolationError(f"view op {op.id} has an invalid user id") from exc


def _restore_view(
    session: Session,
    cs: Changeset,
    op: ChangesetOp,
    *,
    owner_id: uuid.UUID | None = None,
) -> None:
    """把视图状态翻回这条视图改动发生前快照的样子。"""
    _validate_view_op(op)
    view_state.upsert_view_state(
        session,
        graph_id=cs.graph_id,
        user_id=uuid.UUID(str(op.payload["user_id"])),
        spec=dict(op.payload["before"]),
        owner_id=owner_id,
    )


def confirm_changeset(
    session: Session, changeset_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> Changeset:
    """确认：新建的元素翻成已确认，标记删除的元素真正删掉。

    先把每条操作校验干净——种类合法、目标存在、状态允许流转——再落动作，
    这样动作阶段不会因为合法性问题中途失败。
    """
    cs = _lock_changeset(session, changeset_id, owner_id=owner_id)
    if cs.status != OPEN:
        raise InvalidTransitionError(f"changeset {changeset_id} is already {cs.status}")

    ops = list_ops(session, cs.id)
    for op in ops:
        if op.op == OP_VIEW:
            # 视图改动在记录时就已经生效，确认阶段没有本体动作要做。
            continue
        _validate_op(op.op, op.target_id, op.payload)
        kind = _op_kind(op)
        target_id = _op_target(op)
        element = _load_element(session, kind, target_id, owner_id=owner_id)
        if op.op == OP_CREATE and element.status != ontology.DRAFT:
            raise InvalidTransitionError(
                f"{kind} {target_id} is {element.status}, cannot be confirmed from here"
            )

    deletions: list[tuple[str, uuid.UUID]] = []
    for op in ops:
        if op.op == OP_VIEW:
            continue
        if op.op == OP_CREATE:
            _promote(session, _op_kind(op), _op_target(op), owner_id=owner_id)
        elif op.op == OP_UPDATE:
            kind = _op_kind(op)
            element = _load_element(session, kind, _op_target(op), owner_id=owner_id)
            for field, value in op.payload["after"].items():
                setattr(element, field, value)
        elif op.op == OP_DELETE:
            deletions.append((_op_kind(op), _op_target(op)))

    _delete_elements(session, deletions, owner_id=owner_id)
    cs.status = CONFIRMED
    session.flush()
    return cs


def reject_changeset(
    session: Session, changeset_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> Changeset:
    """撤销：删掉本次新建的元素、把改过的还原；标记删除的本来就没删，无事发生。"""
    cs = _lock_changeset(session, changeset_id, owner_id=owner_id)
    if cs.status != OPEN:
        raise InvalidTransitionError(f"changeset {changeset_id} is already {cs.status}")

    ops = list_ops(session, cs.id)
    for op in ops:
        if op.op == OP_VIEW:
            _validate_view_op(op)
            continue
        _validate_op(op.op, op.target_id, op.payload)
        _load_element(session, _op_kind(op), _op_target(op), owner_id=owner_id)

    created: list[tuple[str, uuid.UUID]] = []
    for op in reversed(ops):
        if op.op == OP_VIEW:
            _restore_view(session, cs, op, owner_id=owner_id)
        elif op.op == OP_CREATE:
            created.append((_op_kind(op), _op_target(op)))
        elif op.op == OP_UPDATE:
            element = _load_element(session, _op_kind(op), _op_target(op), owner_id=owner_id)
            for field, value in op.payload.get("before", {}).items():
                setattr(element, field, value)
        elif op.op == OP_DELETE:
            pass  # 草稿阶段没真删

    _delete_elements(session, created, owner_id=owner_id)
    cs.status = REJECTED
    session.flush()
    return cs


# 工具层要复用这两样：可还原字段白名单、元素加载器（含归属校验）。
RESTORABLE_FIELDS = _RESTORABLE_FIELDS
load_element = _load_element
