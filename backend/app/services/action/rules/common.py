"""动作规则与赋值共享的校验原语。"""

from __future__ import annotations

import uuid
from collections.abc import Collection, Sequence

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import ActionParameter, ActionRule, ActionRuleAssignment, Property, Relation
from app.services import relation as relation_service
from app.services import value_types
from app.services.action.parameters import get_parameter
from app.vocabulary import ActionParameterKind, ActionRuleKind

ASSIGNABLE_RULE_KINDS: tuple[str, ...] = (
    ActionRuleKind.CREATE_OBJECT.value,
    ActionRuleKind.MODIFY_OBJECT.value,
)


def get_rule(session: Session, rule_id: uuid.UUID) -> ActionRule:
    rule = session.get(ActionRule, rule_id)
    if rule is None:
        raise NotFoundError(f"action rule {rule_id} not found")
    return rule


def assignment_target_type(rule: ActionRule) -> uuid.UUID:
    """这条规则作用在哪个对象类型上——赋值能不能改某个属性，由它说了算。"""
    if rule.rule_kind not in ASSIGNABLE_RULE_KINDS:
        raise InvariantViolationError(
            f"action rule {rule.id} ({rule.rule_kind}) does not take field assignments"
        )
    if rule.target_object_type_id is None:
        raise InvariantViolationError(f"action rule {rule.id} has no target object type")
    return rule.target_object_type_id


def ensure_object_parameter(
    session: Session,
    *,
    parameter_id: uuid.UUID,
    action_type_id: uuid.UUID,
    object_type_id: uuid.UUID,
    where: str,
    role: str,
) -> ActionParameter:
    parameter = get_parameter(session, parameter_id)
    if parameter.action_type_id != action_type_id:
        raise InvariantViolationError(f"{where}: the {role} parameter belongs to another action")
    if parameter.kind != ActionParameterKind.OBJECT_REF.value:
        raise InvariantViolationError(
            f"{where}: the {role} parameter must be an object reference parameter"
        )
    if parameter.is_array:
        raise InvariantViolationError(
            f"{where}: an array parameter cannot stand for a single object yet"
        )
    if parameter.target_object_type_id != object_type_id:
        raise InvariantViolationError(
            f"{where}: the {role} parameter points at another object type"
        )
    return parameter


def ensure_value_parameter(
    session: Session, *, parameter_id: uuid.UUID, action_type_id: uuid.UUID, where: str
) -> ActionParameter:
    parameter = get_parameter(session, parameter_id)
    if parameter.action_type_id != action_type_id:
        raise InvariantViolationError(f"{where}: the value parameter belongs to another action")
    if parameter.kind != ActionParameterKind.VALUE.value:
        raise InvariantViolationError(f"{where}: expected a value parameter")
    return parameter


def ensure_same_datatype(
    *,
    datatype: str,
    enum_values: Sequence[str] | None,
    target_property: Property,
    where: str,
) -> None:
    if datatype != target_property.datatype:
        raise InvariantViolationError(
            f"{where}: datatype {datatype!r} does not fit property {target_property.name!r} "
            f"({target_property.datatype!r})"
        )
    if datatype == "enum" and not value_types.same_enum_values(
        enum_values, target_property.enum_values
    ):
        raise InvariantViolationError(
            f"{where}: enum values must match property {target_property.name!r} exactly"
        )


def ensure_property_assigned_once(
    session: Session,
    *,
    rule_id: uuid.UUID,
    target_property_id: uuid.UUID,
    exclude_ids: Collection[uuid.UUID] = (),
) -> None:
    """同一条规则里，一个属性只能被赋值一次，否则「改完是什么」没有唯一答案。"""
    stmt = select(ActionRuleAssignment).where(
        ActionRuleAssignment.rule_id == rule_id,
        ActionRuleAssignment.target_property_id == target_property_id,
    )
    if exclude_ids:
        stmt = stmt.where(ActionRuleAssignment.id.not_in(list(exclude_ids)))
    if session.scalar(stmt) is not None:
        raise InvariantViolationError(
            f"action rule {rule_id} already assigns property {target_property_id}"
        )


def relation_endpoints(session: Session, relation_id: uuid.UUID) -> Relation:
    return relation_service.get_relation(session, relation_id)
