"""动作规则与字段赋值。

规则的列组合按 `rule_kind` 分派，赋值按值来源分派——都是「哪种形态必须给哪几列」的
规则。集中在这里，是因为这些约束互相牵制（规则定了作用对象，赋值才知道能改哪些属性）。

提交校验的条件树在 `criteria`，删除保护在 `action_references`。
"""

from __future__ import annotations

import uuid
from collections.abc import Collection, Mapping, Sequence
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import (
    ActionParameter,
    ActionRule,
    ActionRuleAssignment,
    Property,
    Relation,
)
from app.services import (
    action,
    object_type,
    status,
    value_types,
)
from app.services import (
    property as property_service,
)
from app.services import (
    relation as relation_service,
)
from app.vocabulary import (
    ACTION_RULE_KINDS,
    ACTION_VALUE_SOURCES,
    ActionParameterKind,
    ActionRuleKind,
    ActionValueSource,
    RelationCardinality,
)

_OBJECT_RULE_KINDS: tuple[str, ...] = (
    ActionRuleKind.CREATE_OBJECT.value,
    ActionRuleKind.MODIFY_OBJECT.value,
    ActionRuleKind.DELETE_OBJECT.value,
)
_LINK_RULE_KINDS: tuple[str, ...] = (
    ActionRuleKind.CREATE_LINK.value,
    ActionRuleKind.DELETE_LINK.value,
)
#: 能带字段赋值的规则：只改已有对象的属性。
_ASSIGNABLE_RULE_KINDS: tuple[str, ...] = (
    ActionRuleKind.CREATE_OBJECT.value,
    ActionRuleKind.MODIFY_OBJECT.value,
)


def get_rule(session: Session, rule_id: uuid.UUID) -> ActionRule:
    rule = session.get(ActionRule, rule_id)
    if rule is None:
        raise NotFoundError(f"action rule {rule_id} not found")
    return rule


def list_rules(session: Session, action_type_id: uuid.UUID) -> list[ActionRule]:
    return list(
        session.scalars(
            select(ActionRule)
            .where(ActionRule.action_type_id == action_type_id)
            .order_by(ActionRule.order_index, ActionRule.rule_kind)
        )
    )


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


def next_order_index(session: Session, action_type_id: uuid.UUID) -> int:
    indexes = session.scalars(
        select(ActionRule.order_index).where(ActionRule.action_type_id == action_type_id)
    )
    return max(indexes, default=-1) + 1


def create_rule(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    rule_kind: str,
    target_object_type_id: uuid.UUID | None = None,
    target_relation_id: uuid.UUID | None = None,
    source_parameter_id: uuid.UUID | None = None,
    target_parameter_id: uuid.UUID | None = None,
    order_index: int | None = None,
    assignments: Sequence[Mapping[str, Any]] = (),
) -> ActionRule:
    """加一条规则；`assignments` 内联落库，省得调用方再补一轮校验。"""
    action.get_action_type(session, action_type_id)
    where = f"action rule {rule_kind!r}"
    ensure_rule_shape(
        session,
        action_type_id=action_type_id,
        rule_kind=rule_kind,
        target_object_type_id=target_object_type_id,
        target_relation_id=target_relation_id,
        source_parameter_id=source_parameter_id,
        target_parameter_id=target_parameter_id,
        where=where,
    )
    rule = ActionRule(
        action_type_id=action_type_id,
        rule_kind=rule_kind,
        target_object_type_id=target_object_type_id,
        target_relation_id=target_relation_id,
        source_parameter_id=source_parameter_id,
        target_parameter_id=target_parameter_id,
        order_index=(
            next_order_index(session, action_type_id) if order_index is None else order_index
        ),
        status=status.DRAFT,
    )
    session.add(rule)
    session.flush()
    for spec in assignments:
        create_assignment(session, rule_id=rule.id, **dict(spec))
    return rule


def validate_rule_patch(
    session: Session,
    *,
    rule_id: uuid.UUID,
    patch: dict,
    exclude_ids: Collection[uuid.UUID] = (),
) -> None:
    """改规则前校验。种类不可改：换种类等于换语义，请删掉重建。

    规则本身没有跨行唯一约束，`exclude_ids` 只是让更新校验的入参保持统一。
    """
    rule = get_rule(session, rule_id)
    if "rule_kind" in patch and patch["rule_kind"] != rule.rule_kind:
        raise InvariantViolationError(
            f"action rule {rule_id} cannot change its kind; delete it and add a new rule"
        )
    ensure_rule_shape(
        session,
        action_type_id=rule.action_type_id,
        rule_kind=rule.rule_kind,
        target_object_type_id=patch.get("target_object_type_id", rule.target_object_type_id),
        target_relation_id=patch.get("target_relation_id", rule.target_relation_id),
        source_parameter_id=patch.get("source_parameter_id", rule.source_parameter_id),
        target_parameter_id=patch.get("target_parameter_id", rule.target_parameter_id),
        where=f"action rule {rule.rule_kind!r}",
    )


def ensure_rule_shape(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    rule_kind: str,
    target_object_type_id: uuid.UUID | None,
    target_relation_id: uuid.UUID | None,
    source_parameter_id: uuid.UUID | None,
    target_parameter_id: uuid.UUID | None,
    where: str,
) -> None:
    """每种规则要哪些列、不要哪些列，只有这一份判断。"""
    if rule_kind not in ACTION_RULE_KINDS:
        raise InvariantViolationError(
            f"{where}: unsupported rule kind {rule_kind!r}; "
            f"expected one of {list(ACTION_RULE_KINDS)}"
        )

    if rule_kind in _OBJECT_RULE_KINDS:
        if target_relation_id is not None or target_parameter_id is not None:
            raise InvariantViolationError(
                f"{where}: object rule must not carry a relation or a target parameter"
            )
        if target_object_type_id is None:
            raise InvariantViolationError(f"{where}: object rule needs the object type")
        object_type.get_object_type(session, target_object_type_id)
        if rule_kind == ActionRuleKind.CREATE_OBJECT.value:
            if source_parameter_id is not None:
                raise InvariantViolationError(
                    f"{where}: create_object builds a new object, it takes no source parameter"
                )
            return
        if source_parameter_id is None:
            raise InvariantViolationError(
                f"{where}: {rule_kind} needs the object parameter it works on"
            )
        _ensure_object_parameter(
            session,
            parameter_id=source_parameter_id,
            action_type_id=action_type_id,
            object_type_id=target_object_type_id,
            where=where,
            role="source",
        )
        return

    if target_object_type_id is not None:
        raise InvariantViolationError(f"{where}: link rule must not carry an object type")
    if target_relation_id is None:
        raise InvariantViolationError(f"{where}: link rule needs the relation it works on")
    relation = relation_service.get_relation(session, target_relation_id)
    if relation.cardinality != RelationCardinality.MANY_TO_MANY.value:
        raise InvariantViolationError(
            f"{where}: only many-to-many relations use link rules; "
            "one-to-one / one-to-many change the foreign key property with modify_object"
        )
    if source_parameter_id is None or target_parameter_id is None:
        raise InvariantViolationError(f"{where}: link rule needs both endpoint parameters")
    if source_parameter_id == target_parameter_id:
        raise InvariantViolationError(f"{where}: the two endpoints cannot be the same parameter")
    _ensure_object_parameter(
        session,
        parameter_id=source_parameter_id,
        action_type_id=action_type_id,
        object_type_id=relation.source_type_id,
        where=where,
        role="source",
    )
    _ensure_object_parameter(
        session,
        parameter_id=target_parameter_id,
        action_type_id=action_type_id,
        object_type_id=relation.target_type_id,
        where=where,
        role="target",
    )


def _ensure_object_parameter(
    session: Session,
    *,
    parameter_id: uuid.UUID,
    action_type_id: uuid.UUID,
    object_type_id: uuid.UUID,
    where: str,
    role: str,
) -> ActionParameter:
    parameter = action.get_parameter(session, parameter_id)
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


def assignment_target_type(session: Session, rule: ActionRule) -> uuid.UUID:
    """这条规则作用在哪个对象类型上——赋值能不能改某个属性，由它说了算。"""
    if rule.rule_kind not in _ASSIGNABLE_RULE_KINDS:
        raise InvariantViolationError(
            f"action rule {rule.id} ({rule.rule_kind}) does not take field assignments"
        )
    if rule.target_object_type_id is None:
        raise InvariantViolationError(f"action rule {rule.id} has no target object type")
    return rule.target_object_type_id


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
    target_type_id = assignment_target_type(session, rule)
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
    _ensure_property_assigned_once(
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
    target_type_id = assignment_target_type(session, rule)
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
        _ensure_property_assigned_once(
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
        parameter = _ensure_value_parameter(
            session, parameter_id=value_parameter_id, action_type_id=action_type_id, where=where
        )
        datatype = parameter.datatype
        if datatype is None:
            raise InvariantViolationError(f"{where}: the value parameter has no datatype")
        _ensure_same_datatype(
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
        parameter = action.get_parameter(session, object_parameter_id)
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
        _ensure_same_datatype(
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


def _ensure_value_parameter(
    session: Session, *, parameter_id: uuid.UUID, action_type_id: uuid.UUID, where: str
) -> ActionParameter:
    parameter = action.get_parameter(session, parameter_id)
    if parameter.action_type_id != action_type_id:
        raise InvariantViolationError(f"{where}: the value parameter belongs to another action")
    if parameter.kind != ActionParameterKind.VALUE.value:
        raise InvariantViolationError(f"{where}: expected a value parameter")
    return parameter


def _ensure_same_datatype(
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


def _ensure_property_assigned_once(
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
