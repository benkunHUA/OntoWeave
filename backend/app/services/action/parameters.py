"""动作参数的读写、形状校验与不变量。"""

from __future__ import annotations

import uuid
from collections.abc import Collection
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import ActionParameter
from app.services import enum_values as enum_values_rules
from app.services import object_type, status, value_types
from app.services.action.types import get_action_type
from app.vocabulary import ACTION_PARAMETER_KINDS, ActionParameterKind, PropertyDatatype


def get_parameter(session: Session, parameter_id: uuid.UUID) -> ActionParameter:
    parameter = session.get(ActionParameter, parameter_id)
    if parameter is None:
        raise NotFoundError(f"action parameter {parameter_id} not found")
    return parameter


def list_parameters(session: Session, action_type_id: uuid.UUID) -> list[ActionParameter]:
    return list(
        session.scalars(
            select(ActionParameter)
            .where(ActionParameter.action_type_id == action_type_id)
            .order_by(ActionParameter.order_index, ActionParameter.name)
        )
    )


def ensure_slot_free(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    name: str | None = None,
    order_index: int | None = None,
    exclude_ids: Collection[uuid.UUID] = (),
) -> None:
    """同一个动作里，参数名与顺序位各占一个。

    `exclude_ids` 让调用方把「本次已经决定不要的参数」排除掉：删掉一个参数、再建一个
    同名参数是常见操作，不该被还没落地的删除挡住。
    """
    stmt = select(ActionParameter).where(ActionParameter.action_type_id == action_type_id)
    if exclude_ids:
        stmt = stmt.where(ActionParameter.id.not_in(list(exclude_ids)))
    for existing in session.scalars(stmt):
        if name is not None and existing.name == name:
            raise InvariantViolationError(
                f"action type {action_type_id} already has a parameter named {name!r}"
            )
        if order_index is not None and existing.order_index == order_index:
            raise InvariantViolationError(
                f"action type {action_type_id} already has a parameter at position {order_index}"
            )


def next_order_index(session: Session, action_type_id: uuid.UUID) -> int:
    """新参数接在末尾。"""
    indexes = session.scalars(
        select(ActionParameter.order_index).where(ActionParameter.action_type_id == action_type_id)
    )
    return max(indexes, default=-1) + 1


def create_parameter(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    name: str,
    kind: str = ActionParameterKind.VALUE.value,
    description: str | None = None,
    datatype: str | None = None,
    target_object_type_id: uuid.UUID | None = None,
    is_array: bool = False,
    is_required: bool = False,
    default_value: Any | None = None,
    enum_values: list[str] | None = None,
    order_index: int | None = None,
    exclude_ids: Collection[uuid.UUID] = (),
) -> ActionParameter:
    """给动作加一个参数。两组列（值参数 / 对象引用参数）必须配套，不能混着给。"""
    action = get_action_type(session, action_type_id)
    if not name.strip():
        raise InvariantViolationError("action parameter name must not be empty")
    shape = _parameter_shape(
        kind=kind,
        datatype=datatype,
        target_object_type_id=target_object_type_id,
        enum_values=enum_values,
        default_value=default_value,
        is_array=is_array,
        where=f"action parameter {name!r}",
    )
    if shape.target_object_type_id is not None:
        object_type.get_object_type(session, shape.target_object_type_id)
    position = next_order_index(session, action_type_id) if order_index is None else order_index
    ensure_slot_free(
        session,
        action_type_id=action.id,
        name=name.strip(),
        order_index=position,
        exclude_ids=exclude_ids,
    )
    parameter = ActionParameter(
        action_type_id=action.id,
        name=name.strip(),
        description=description,
        kind=shape.kind,
        datatype=shape.datatype,
        target_object_type_id=shape.target_object_type_id,
        is_array=is_array,
        is_required=is_required,
        default_value=shape.default_value,
        enum_values=shape.enum_values,
        order_index=position,
        status=status.DRAFT,
    )
    session.add(parameter)
    session.flush()
    return parameter


def validate_parameter_patch(
    session: Session,
    *,
    parameter_id: uuid.UUID,
    patch: dict,
    exclude_ids: Collection[uuid.UUID] = (),
) -> None:
    """改参数前校验：名字 / 顺序位不撞，两组列改完仍然配套。

    `exclude_ids` 是本改动集里已经标删、还没落地的参数：把「说明」改成刚删掉的「备注」
    这种改名，不该被那行还没真删的旧记录挡住。
    """
    parameter = get_parameter(session, parameter_id)
    where = f"action parameter {parameter.name!r}"
    shape = _parameter_shape(
        kind=patch.get("kind", parameter.kind),
        datatype=patch.get("datatype", parameter.datatype),
        target_object_type_id=patch.get("target_object_type_id", parameter.target_object_type_id),
        enum_values=patch.get("enum_values", parameter.enum_values),
        default_value=patch.get("default_value", parameter.default_value),
        is_array=patch.get("is_array", parameter.is_array),
        where=where,
    )
    if "target_object_type_id" in patch and shape.target_object_type_id is not None:
        object_type.get_object_type(session, shape.target_object_type_id)

    name = patch.get("name")
    if name is not None:
        if not str(name).strip():
            raise InvariantViolationError("action parameter name must not be empty")
        name = str(name).strip()
    ensure_slot_free(
        session,
        action_type_id=parameter.action_type_id,
        name=name,
        order_index=patch.get("order_index"),
        exclude_ids=[parameter.id, *exclude_ids],
    )


class _ParameterShape:
    """校验通过后的四个落库字段：kind 决定谁有值，其余一律为 None。"""

    __slots__ = ("kind", "datatype", "target_object_type_id", "default_value", "enum_values")

    def __init__(self, kind, datatype, target_object_type_id, default_value, enum_values) -> None:
        self.kind = kind
        self.datatype = datatype
        self.target_object_type_id = target_object_type_id
        self.default_value = default_value
        self.enum_values = enum_values


def _parameter_shape(
    *,
    kind: str,
    datatype: str | None,
    target_object_type_id: uuid.UUID | None,
    enum_values: list[str] | None,
    default_value: Any | None,
    is_array: bool,
    where: str,
) -> _ParameterShape:
    if kind not in ACTION_PARAMETER_KINDS:
        raise InvariantViolationError(
            f"{where}: unsupported parameter kind {kind!r}; "
            f"expected one of {list(ACTION_PARAMETER_KINDS)}"
        )

    if kind == ActionParameterKind.OBJECT_REF.value:
        if target_object_type_id is None:
            raise InvariantViolationError(f"{where}: object_ref parameter needs an object type")
        if datatype is not None or enum_values or default_value is not None:
            raise InvariantViolationError(
                f"{where}: object_ref parameter cannot carry datatype, enum_values or default_value"
            )
        return _ParameterShape(kind, None, target_object_type_id, None, None)

    if datatype is None:
        raise InvariantViolationError(f"{where}: value parameter needs a datatype")
    try:
        PropertyDatatype(datatype)
    except ValueError as exc:
        raise InvariantViolationError(f"{where}: unsupported datatype {datatype!r}") from exc
    if target_object_type_id is not None:
        raise InvariantViolationError(f"{where}: value parameter cannot point at an object type")

    stored_enum_values = _enum_values_for(datatype=datatype, values=enum_values, where=where)
    if default_value is not None:
        value_types.ensure_value_matches(
            datatype,
            default_value,
            where=f"{where} default_value",
            is_array=is_array,
            enum_values=stored_enum_values,
        )
    return _ParameterShape(kind, datatype, None, default_value, stored_enum_values)


def _enum_values_for(*, datatype: str, values: list[str] | None, where: str) -> list[str] | None:
    if datatype == PropertyDatatype.ENUM:
        if not values:
            raise InvariantViolationError(f"{where}: enum datatype requires enum_values")
        return enum_values_rules.ensure_valid(values, where=where)
    if values:
        raise InvariantViolationError(
            f"{where}: enum_values is only allowed when datatype is 'enum'"
        )
    return None
