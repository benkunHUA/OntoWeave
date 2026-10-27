"""动作规则的字段赋值：目标属性 ← 一个值来源。"""

from __future__ import annotations

import uuid
from collections.abc import Collection
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import ActionRule, ActionRuleAssignment
from app.services import property as property_service
from app.services import status, value_types
from app.services.action.parameters import get_parameter
from app.services.action.rules.common import (
    assignment_target_type as _assignment_target_type,
)
from app.services.action.rules.common import (
    ensure_property_assigned_once,
    ensure_same_datatype,
    ensure_value_parameter,
    get_rule,
)
from app.vocabulary import ACTION_VALUE_SOURCES, ActionParameterKind, ActionValueSource


def get_assignment(session: Session, assignment_id: uuid.UUID) -> ActionRuleAssignment:
    assignment = session.get(ActionRuleAssignment, assignment_id)
    if assignment is None:
        raise NotFoundError(f"action rule assignment {assignment_id} not found")
    return assignment


def list_assignments(session: Session, rule_id: uuid.UUID) -> list[ActionRuleAssignment]:
    return list(
        session.scalars(
            select(ActionRuleAssignment)
            .where(ActionRuleAssignment.rule_id == rule_id)
            .order_by(ActionRuleAssignment.target_property_id)
        )
    )


def assignment_target_type(session: Session, rule: ActionRule) -> uuid.UUID:
    del session
    return _assignment_target_type(rule)


def create_assignment(
    session: Session,
    *,
    rule_id: uuid.UUID,
    target_property_id: uuid.UUID,
    value_source: str,
    value_parameter_id: uuid.UUID | None = None,
    object_parameter_id: uuid.UUID | None = None,
    object_property_id: uuid.UUID | None = None,
    static_value: Any | None = None,
    exclude_ids: Collection[uuid.UUID] = (),
) -> ActionRuleAssignment:
    """给对象规则加一条赋值：目标属性 ← 一个值来源。

    `exclude_ids` 是本改动集里已标删、还没落地的赋值：删掉一条再补一条同属性的，
    不该被那行还没真删的旧记录挡住。
    """
    rule = get_rule(session, rule_id)
    target_type_id = _assignment_target_type(rule)
    where = f"action rule {rule.rule_kind!r} assignment"
    shape = ensure_assignment_shape(
        session,
        action_type_id=rule.action_type_id,
        target_type_id=target_type_id,
        target_property_id=target_property_id,
        value_source=value_source,
        value_parameter_id=value_parameter_id,
        object_parameter_id=object_parameter_id,
        object_property_id=object_property_id,
        static_value=static_value,
        where=where,
    )
    ensure_property_assigned_once(
        session,
        rule_id=rule.id,
        target_property_id=target_property_id,
        exclude_ids=exclude_ids,
    )
    assignment = ActionRuleAssignment(
        rule_id=rule.id,
        target_property_id=target_property_id,
        value_source=shape.value_source,
        value_parameter_id=shape.value_parameter_id,
        object_parameter_id=shape.object_parameter_id,
        object_property_id=shape.object_property_id,
        static_value=shape.static_value,
        status=status.DRAFT,
    )
    session.add(assignment)
    session.flush()
    return assignment


def validate_assignment_patch(
    session: Session,
    *,
    assignment_id: uuid.UUID,
    patch: dict,
    exclude_ids: Collection[uuid.UUID] = (),
) -> None:
    """改赋值前校验：改完仍然只对应一个值来源，且目标属性仍在作用对象上。

    `exclude_ids` 是本改动集里已标删的赋值：把目标属性挪到刚删掉的那条上要放行。
    """
    assignment = get_assignment(session, assignment_id)
    rule = get_rule(session, assignment.rule_id)
    target_type_id = _assignment_target_type(rule)
    target_property_id = patch.get("target_property_id", assignment.target_property_id)
    ensure_assignment_shape(
        session,
        action_type_id=rule.action_type_id,
        target_type_id=target_type_id,
        target_property_id=target_property_id,
        value_source=patch.get("value_source", assignment.value_source),
        value_parameter_id=patch.get("value_parameter_id", assignment.value_parameter_id),
        object_parameter_id=patch.get("object_parameter_id", assignment.object_parameter_id),
        object_property_id=patch.get("object_property_id", assignment.object_property_id),
        static_value=patch.get("static_value", assignment.static_value),
        where=f"action rule {rule.rule_kind!r} assignment",
    )
    if "target_property_id" in patch:
        ensure_property_assigned_once(
            session,
            rule_id=rule.id,
            target_property_id=target_property_id,
            exclude_ids=[assignment.id, *exclude_ids],
        )


class _AssignmentShape:
    """值来源决定了哪几列有值，其余一律清空。"""

    __slots__ = (
        "value_source",
        "value_parameter_id",
        "object_parameter_id",
        "object_property_id",
        "static_value",
    )

    def __init__(
        self,
        value_source: str,
        value_parameter_id: uuid.UUID | None,
        object_parameter_id: uuid.UUID | None,
        object_property_id: uuid.UUID | None,
        static_value: Any | None,
    ) -> None:
        self.value_source = value_source
        self.value_parameter_id = value_parameter_id
        self.object_parameter_id = object_parameter_id
        self.object_property_id = object_property_id
        self.static_value = static_value


def ensure_assignment_shape(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    target_type_id: uuid.UUID,
    target_property_id: uuid.UUID,
    value_source: str,
    value_parameter_id: uuid.UUID | None,
    object_parameter_id: uuid.UUID | None,
    object_property_id: uuid.UUID | None,
    static_value: Any | None,
    where: str,
) -> _AssignmentShape:
    target_property = property_service.get_property(session, target_property_id)
    if target_property.owner_type_id != target_type_id:
        raise InvariantViolationError(
            f"{where}: property {target_property_id} does not belong to the rule's object type"
        )
    if value_source not in ACTION_VALUE_SOURCES:
        raise InvariantViolationError(
            f"{where}: unsupported value source {value_source!r}; "
            f"expected one of {list(ACTION_VALUE_SOURCES)}"
        )

    if value_source == ActionValueSource.PARAMETER.value:
        if object_parameter_id is not None or object_property_id is not None:
            raise InvariantViolationError(
                f"{where}: parameter source must not carry object columns"
            )
        if value_parameter_id is None:
            raise InvariantViolationError(f"{where}: parameter source needs the value parameter")
        parameter = ensure_value_parameter(
            session, parameter_id=value_parameter_id, action_type_id=action_type_id, where=where
        )
        datatype = parameter.datatype
        if datatype is None:
            raise InvariantViolationError(f"{where}: the value parameter has no datatype")
        ensure_same_datatype(
            datatype=datatype,
            enum_values=parameter.enum_values,
            target_property=target_property,
            where=where,
        )
        return _AssignmentShape(value_source, value_parameter_id, None, None, None)

    if value_source == ActionValueSource.OBJECT_PROPERTY.value:
        if value_parameter_id is not None or static_value is not None:
            raise InvariantViolationError(
                f"{where}: object_property source must not carry "
                "a value parameter or a static value"
            )
        if object_parameter_id is None or object_property_id is None:
            raise InvariantViolationError(
                f"{where}: object_property source needs both the object parameter and the property"
            )
        parameter = get_parameter(session, object_parameter_id)
        if parameter.action_type_id != action_type_id:
            raise InvariantViolationError(
                f"{where}: the object parameter belongs to another action"
            )
        if parameter.kind != ActionParameterKind.OBJECT_REF.value:
            raise InvariantViolationError(f"{where}: source parameter must be an object reference")
        if parameter.is_array:
            raise InvariantViolationError(
                f"{where}: an array parameter cannot feed a property value yet"
            )
        source_property = property_service.get_property(session, object_property_id)
        if source_property.owner_type_id != parameter.target_object_type_id:
            raise InvariantViolationError(
                f"{where}: property {object_property_id} does not belong to the parameter's type"
            )
        ensure_same_datatype(
            datatype=source_property.datatype,
            enum_values=source_property.enum_values,
            target_property=target_property,
            where=where,
        )
        return _AssignmentShape(value_source, None, object_parameter_id, object_property_id, None)

    if (
        value_parameter_id is not None
        or object_parameter_id is not None
        or object_property_id is not None
    ):
        raise InvariantViolationError(f"{where}: static source must not carry parameter columns")
    if static_value is None:
        raise InvariantViolationError(f"{where}: static source needs the value itself")
    value_types.ensure_value_matches(
        target_property.datatype,
        static_value,
        where=where,
        is_array=target_property.is_array,
        enum_values=target_property.enum_values,
    )
    return _AssignmentShape(value_source, None, None, None, static_value)
