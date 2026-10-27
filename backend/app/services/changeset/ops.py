"""往改动集里登记操作，并在草稿阶段应用字段更新。"""

from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

import app.services.changeset.common as common
import app.services.changeset.store as store
from app.errors import InvalidTransitionError, InvariantViolationError
from app.models import ChangesetOp
from app.services.element_kinds.registry import REGISTRY


def record_op(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    op: str,
    target_id: uuid.UUID | None = None,
    payload: dict | None = None,
    owner_id: uuid.UUID | None = None,
) -> ChangesetOp:
    cs = store.lock_changeset(session, changeset_id, owner_id=owner_id)
    if cs.status != common.OPEN:
        raise InvalidTransitionError(
            f"changeset {changeset_id} is {cs.status}, cannot record new ops"
        )
    validate_op(op, target_id, payload or {})
    if op == common.OP_UPDATE:
        apply_update_payload(session, target_id, payload or {}, owner_id=owner_id)
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
    cs = store.lock_changeset(session, changeset_id, owner_id=owner_id)
    if cs.status != common.OPEN:
        raise InvalidTransitionError(
            f"changeset {changeset_id} is {cs.status}, cannot record new ops"
        )
    payload = {
        "kind": common.VIEW_KIND,
        "user_id": str(user_id),
        "before": dict(before),
        "after": dict(after),
    }
    return append_op(
        session,
        changeset_id=changeset_id,
        op=common.OP_VIEW,
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
    entry = REGISTRY.get(element_kind)
    if entry is None:
        raise InvariantViolationError(f"unsupported element kind: {element_kind!r}")
    allowed = entry.restorable_fields
    if not patch:
        raise InvariantViolationError("update requires at least one patched field")
    unknown = set(patch) - allowed
    if unknown:
        raise InvariantViolationError(f"cannot patch fields: {sorted(unknown)}")

    cs = store.lock_changeset(session, changeset_id, owner_id=owner_id)
    if cs.status != common.OPEN:
        raise InvalidTransitionError(
            f"changeset {changeset_id} is {cs.status}, cannot record new ops"
        )
    element = common.load_element(session, element_kind, element_id, owner_id=owner_id)
    before = {field: common.json_safe(getattr(element, field)) for field in patch}
    payload = {
        "kind": element_kind,
        "before": before,
        "after": {field: common.json_safe(value) for field, value in patch.items()},
    }
    validate_op(common.OP_UPDATE, element_id, payload)
    apply_update_payload(session, element_id, payload, owner_id=owner_id)
    return append_op(
        session,
        changeset_id=changeset_id,
        op=common.OP_UPDATE,
        target_id=element_id,
        payload=payload,
    )


def append_op(
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


def apply_update_payload(
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
    element = common.load_element(session, kind, element_id, owner_id=owner_id)

    stale = {
        field: {"expected": expected, "actual": common.json_safe(getattr(element, field))}
        for field, expected in before.items()
        if common.json_safe(getattr(element, field)) != expected
    }
    if stale:
        raise InvariantViolationError(
            f"{kind} {element_id} changed after the update snapshot was taken: {stale}"
        )

    common.apply_fields(element, after)
    session.flush()


def validate_op(op: str, target_id: uuid.UUID | None, payload: dict) -> None:
    """在记录阶段就把不合法的操作挡掉，别留到确认时才发现。"""
    if op not in common.VALID_OPS:
        raise InvariantViolationError(f"unsupported changeset op: {op!r}")
    if target_id is None:
        raise InvariantViolationError(f"changeset op {op!r} requires a target id")
    kind = payload.get("kind")
    if kind not in REGISTRY:
        raise InvariantViolationError(
            f"changeset op {op!r} requires a known target kind, got {kind!r}"
        )
    if op == common.OP_UPDATE:
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
        unknown = set(before) - REGISTRY[kind].restorable_fields
        if unknown:
            raise InvariantViolationError(
                f"changeset op cannot restore fields {sorted(unknown)} on {kind}"
            )


def validate_view_op(op: ChangesetOp) -> None:
    """视图改动没有本体目标：只有一份 before/after 快照和一个归属用户。"""
    if op.target_id is not None:
        raise InvariantViolationError(f"view op {op.id} must not carry a target id")
    if op.payload.get("kind") != common.VIEW_KIND:
        raise InvariantViolationError(f"view op {op.id} has the wrong kind")
    before = op.payload.get("before")
    after = op.payload.get("after")
    if not isinstance(before, dict) or not isinstance(after, dict):
        raise InvariantViolationError(f"view op {op.id} needs 'before' and 'after' specs")
    try:
        uuid.UUID(str(op.payload.get("user_id")))
    except (TypeError, ValueError) as exc:
        raise InvariantViolationError(f"view op {op.id} has an invalid user id") from exc
