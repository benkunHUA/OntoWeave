"""条件与操作数的形状校验。

一条条件是「一个运算符 + 至多两个操作数」：运算符能用在哪类数据上，矩阵在
`vocabulary`；两侧类型对不对得上，判断在 `value_types`。校验只认 `OperandSpec`
这种形状，所以补丁路径与建树路径共用同一份判断，不会出现「建的时候让过、改的时候不让过」。
"""

from __future__ import annotations

import uuid
from collections.abc import Sequence
from typing import Any

from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import ActionCriteriaNode
from app.services import action, value_types
from app.services import property as property_service
from app.services.criteria import store
from app.vocabulary import (
    ACTION_CRITERIA_OPERATORS,
    ACTION_CRITERIA_SIDES,
    UNARY_OPERATORS,
    ActionCriteriaNodeKind,
    ActionOperandKind,
    ActionParameterKind,
    criteria_operators_for,
)


def ensure_condition_shape(session: Session, *, node: ActionCriteriaNode, where: str) -> None:
    """按库里现有的操作数校验一条条件。"""
    if node.node_kind != ActionCriteriaNodeKind.CONDITION.value:
        raise InvariantViolationError(f"{where}: node {node.id} is not a condition")
    sides: dict[str, store.OperandSpec] = {}
    for operand in store.list_operands(session, [node.id]):
        spec = store.operand_spec(operand)
        if spec.side in sides:
            raise InvariantViolationError(f"{where}: more than one {spec.side} operand")
        sides[spec.side] = spec
        ensure_operand_shape(
            session,
            action_type_id=node.action_type_id,
            operand=spec,
            where=f"{where} {spec.side} operand",
        )
    ensure_condition_sides(
        session,
        action_type_id=node.action_type_id,
        operator=node.operator,
        left=sides.get(store.LEFT),
        right=sides.get(store.RIGHT),
        where=where,
    )


def ensure_condition_sides(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    operator: str | None,
    left: store.OperandSpec | None,
    right: store.OperandSpec | None,
    where: str,
) -> None:
    """两侧的搭配：运算符认不认左边这个类型、该不该有右边、右边配不配得上。"""
    if left is None:
        raise InvariantViolationError(f"{where}: the condition needs a left operand")
    if operator not in ACTION_CRITERIA_OPERATORS:
        raise InvariantViolationError(f"{where}: unsupported operator {operator!r}")
    datatype, enum_values = _operand_datatype(session, left, where=where)
    if operator not in criteria_operators_for(datatype):
        raise InvariantViolationError(
            f"{where}: operator {operator!r} does not apply to datatype {datatype!r}"
        )
    if operator in UNARY_OPERATORS:
        if right is not None:
            raise InvariantViolationError(f"{where}: operator {operator!r} takes no right operand")
        return
    if right is None:
        raise InvariantViolationError(f"{where}: operator {operator!r} needs a right operand")
    _ensure_sides_match(
        session,
        left_datatype=datatype,
        left_enum_values=enum_values,
        right=right,
        where=where,
    )


def ensure_operand_shape(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    operand: store.OperandSpec,
    where: str,
) -> None:
    """操作数的种类决定哪一列有值，其余一律必须为空。"""
    if operand.side not in ACTION_CRITERIA_SIDES:
        raise InvariantViolationError(f"{where}: unsupported side {operand.side!r}")
    if operand.kind == ActionOperandKind.PARAMETER.value:
        if operand.value_parameter_id is None:
            raise InvariantViolationError(f"{where}: parameter operand needs a value parameter")
        if operand.object_parameter_id is not None or operand.object_property_id is not None:
            raise InvariantViolationError(
                f"{where}: parameter operand must not carry other columns"
            )
        if operand.static_value is not None:
            raise InvariantViolationError(
                f"{where}: parameter operand must not carry other columns"
            )
        _ensure_value_parameter(
            session,
            parameter_id=operand.value_parameter_id,
            action_type_id=action_type_id,
            where=where,
        )
        return
    if operand.kind == ActionOperandKind.OBJECT_PROPERTY.value:
        if operand.object_parameter_id is None or operand.object_property_id is None:
            raise InvariantViolationError(
                f"{where}: object_property operand needs the object parameter and the property"
            )
        if operand.value_parameter_id is not None or operand.static_value is not None:
            raise InvariantViolationError(
                f"{where}: object_property operand must not carry a value parameter or static value"
            )
        parameter = _ensure_object_parameter(
            session,
            parameter_id=operand.object_parameter_id,
            action_type_id=action_type_id,
            where=where,
        )
        prop = property_service.get_property(session, operand.object_property_id)
        if prop.owner_type_id != parameter.target_object_type_id:
            raise InvariantViolationError(
                f"{where}: property {prop.id} does not belong to the parameter's object type"
            )
        if prop.is_array:
            raise InvariantViolationError(f"{where}: array properties are not supported here yet")
        return
    if operand.kind == ActionOperandKind.STATIC.value:
        if operand.static_value is None:
            raise InvariantViolationError(
                f"{where}: static operand needs a value; use 'is null' to test emptiness"
            )
        if operand.value_parameter_id is not None or operand.object_parameter_id is not None:
            raise InvariantViolationError(f"{where}: static operand must not carry other columns")
        if operand.object_property_id is not None:
            raise InvariantViolationError(f"{where}: static operand must not carry other columns")
        return
    if operand.kind == ActionOperandKind.NULL.value:
        raise InvariantViolationError(
            f"{where}: operand kind 'null' is reserved for a later version"
        )
    raise InvariantViolationError(
        f"{where}: unsupported operand kind {operand.kind!r}; "
        "expected one of parameter, object_property, static"
    )


def _operand_datatype(
    session: Session, operand: store.OperandSpec, *, where: str
) -> tuple[str, Sequence[str] | None]:
    """操作数自带的数据类型与候选值。静态值没有类型，不能站在左边。"""
    if operand.kind == ActionOperandKind.PARAMETER.value:
        parameter = action.get_parameter(session, _required(operand.value_parameter_id))
        if parameter.datatype is None:
            raise InvariantViolationError(f"{where}: parameter has no datatype")
        return parameter.datatype, parameter.enum_values
    if operand.kind == ActionOperandKind.OBJECT_PROPERTY.value:
        prop = property_service.get_property(session, _required(operand.object_property_id))
        return prop.datatype, prop.enum_values
    raise InvariantViolationError(
        f"{where}: the left operand must be a parameter or an object property"
    )


def _ensure_sides_match(
    session: Session,
    *,
    left_datatype: str,
    left_enum_values: Sequence[str] | None,
    right: store.OperandSpec,
    where: str,
) -> None:
    if right.kind == ActionOperandKind.STATIC.value:
        value_types.ensure_value_matches(
            left_datatype,
            right.static_value,
            where=f"{where} right operand",
            enum_values=left_enum_values,
        )
        return
    right_datatype, right_enum_values = _operand_datatype(session, right, where=where)
    if right_datatype != left_datatype:
        raise InvariantViolationError(
            f"{where}: the two sides disagree on datatype ({right_datatype!r} vs {left_datatype!r})"
        )
    if left_datatype == "enum" and not value_types.same_enum_values(
        left_enum_values, right_enum_values
    ):
        raise InvariantViolationError(f"{where}: the two sides must use the same enum values")


def _required(value: uuid.UUID | None) -> uuid.UUID:
    """形状校验已经保证这几列有值，读的时候再兜一道，免得 None 流进查询。"""
    if value is None:
        raise InvariantViolationError("criteria operand is missing a referenced id")
    return value


def _ensure_value_parameter(
    session: Session, *, parameter_id: uuid.UUID, action_type_id: uuid.UUID, where: str
) -> None:
    parameter = action.get_parameter(session, parameter_id)
    if parameter.action_type_id != action_type_id:
        raise InvariantViolationError(f"{where}: the value parameter belongs to another action")
    if parameter.kind != ActionParameterKind.VALUE.value:
        raise InvariantViolationError(f"{where}: expected a value parameter")
    if parameter.is_array:
        raise InvariantViolationError(f"{where}: array parameters are not supported here yet")


def _ensure_object_parameter(
    session: Session, *, parameter_id: uuid.UUID, action_type_id: uuid.UUID, where: str
) -> Any:
    parameter = action.get_parameter(session, parameter_id)
    if parameter.action_type_id != action_type_id:
        raise InvariantViolationError(f"{where}: the object parameter belongs to another action")
    if parameter.kind != ActionParameterKind.OBJECT_REF.value:
        raise InvariantViolationError(f"{where}: expected an object reference parameter")
    if parameter.target_object_type_id is None:
        raise InvariantViolationError(f"{where}: object reference parameter has no object type")
    return parameter
