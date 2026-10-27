"""改动集共享的常量与元素加载。"""

from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import ChangesetOp
from app.services.element_kinds.registry import REGISTRY

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


def json_safe(value: object) -> object:
    """存进 JSONB 前把 UUID 转成字符串，其余原样，避免 JSON 序列化失败。"""
    return str(value) if isinstance(value, uuid.UUID) else value


def coerce_field(field: str, value: object) -> object:
    """把从 JSON 读回的字段值转回列类型。约定：以 ``_id`` 结尾的是 UUID 列。"""
    if field.endswith("_id") and isinstance(value, str):
        try:
            return uuid.UUID(value)
        except ValueError as exc:
            raise InvariantViolationError(f"field {field!r} is not a valid uuid") from exc
    return value


def apply_fields(element: object, fields: dict) -> None:
    for field, value in fields.items():
        setattr(element, field, coerce_field(field, value))


def op_kind(op: ChangesetOp) -> str:
    kind = op.payload.get("kind")
    if not isinstance(kind, str):
        raise InvariantViolationError(f"changeset op {op.id} is missing a target kind")
    return kind


def op_target(op: ChangesetOp) -> uuid.UUID:
    if op.target_id is None:
        raise InvariantViolationError(f"changeset op {op.id} is missing a target id")
    return op.target_id


def element_owner_id(session: Session, kind: str, element: object) -> uuid.UUID | None:
    entry = REGISTRY.get(kind)
    if entry is None:
        return None
    return entry.load_owner_id(session, element)


def load_element(
    session: Session,
    kind: str,
    element_id: uuid.UUID,
    *,
    owner_id: uuid.UUID | None = None,
):
    entry = REGISTRY.get(kind)
    if entry is None:
        raise InvariantViolationError(f"unsupported changeset op target: {kind!r}")
    element = session.get(entry.model, element_id)
    if element is None:
        raise NotFoundError(f"{kind} {element_id} not found")
    if owner_id is not None and element_owner_id(session, kind, element) != owner_id:
        raise NotFoundError(f"{kind} {element_id} not found")
    return element
