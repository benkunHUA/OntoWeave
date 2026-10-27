"""删除保护：被动作定义引用着的共享元素不许删。

对象类型 / 关系 / 属性 / 动作参数一旦被规则、赋值或条件引用，删掉就会让动作定义变成
半截。数据库外键是 RESTRICT，但只报一句 IntegrityError，没人看得懂，所以在**记录删除
时**就拦住，并把引用方一条条列出来。

级联也算引用：对象类型一删，指向它的关系、它自己的属性都会跟着走，所以这些身上的
引用一并算进来。
"""

from __future__ import annotations

import uuid
from collections.abc import Collection, Sequence
from dataclasses import dataclass

from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import (
    ActionCriteriaNode,
    ActionCriteriaOperand,
    ActionParameter,
    ActionRule,
    ActionRuleAssignment,
    ActionType,
    Property,
    Relation,
)
from app.services.element_kinds.types import (
    ACTION_CRITERIA_OPERAND,
    ACTION_PARAMETER,
    ACTION_RULE,
    ACTION_RULE_ASSIGNMENT,
    OBJECT_TYPE,
    PROPERTY,
    RELATION,
)

#: 报错时最多列几条引用，剩下的只报个数。
_MAX_REPORTED = 5


@dataclass(frozen=True)
class Reference:
    """一条「谁在引用它」：引用方种类、所属动作、人可读的一行说明。"""

    kind: str
    action_type_id: uuid.UUID
    action_name: str
    label: str


def find_references(
    session: Session, *, element_kind: str, element_id: uuid.UUID
) -> list[Reference]:
    """列出所有引用这个元素的动作定义位置；没有就返回空表。"""
    if element_kind == OBJECT_TYPE:
        return _object_type_references(session, [element_id])
    if element_kind == PROPERTY:
        return _property_references(session, [element_id])
    if element_kind == RELATION:
        return _relation_references(session, [element_id])
    if element_kind == ACTION_PARAMETER:
        return _parameter_references(session, [element_id])
    return []


def ensure_deletable(session: Session, *, element_kind: str, element_id: uuid.UUID) -> None:
    """有引用就拒绝删除，错误信息带上引用方清单。"""
    references = find_references(session, element_kind=element_kind, element_id=element_id)
    if not references:
        return
    raise InvariantViolationError(
        f"{element_kind} {element_id} is still used by action definitions: "
        + _summarize(references)
    )


def _summarize(references: Sequence[Reference]) -> str:
    listed = "; ".join(
        f"action {reference.action_name!r} {reference.label}"
        for reference in references[:_MAX_REPORTED]
    )
    if len(references) > _MAX_REPORTED:
        listed += f"; and {len(references) - _MAX_REPORTED} more"
    return listed


def _object_type_references(
    session: Session, object_type_ids: Collection[uuid.UUID]
) -> list[Reference]:
    ids = list(object_type_ids)
    references = [
        _parameter_reference(session, parameter, role="as its object type")
        for parameter in session.scalars(
            select(ActionParameter).where(ActionParameter.target_object_type_id.in_(ids))
        )
    ]
    references += [
        _rule_reference(session, rule, role="as its object type")
        for rule in session.scalars(
            select(ActionRule).where(ActionRule.target_object_type_id.in_(ids))
        )
    ]
    # 级联带走的：指向这个类型的关系、它自己的属性。它们的引用方一样会挡住删除。
    relation_ids = list(
        session.scalars(
            select(Relation.id).where(
                or_(Relation.source_type_id.in_(ids), Relation.target_type_id.in_(ids))
            )
        )
    )
    if relation_ids:
        references += _relation_references(session, relation_ids)
    property_ids = list(session.scalars(select(Property.id).where(Property.owner_type_id.in_(ids))))
    if property_ids:
        references += _property_references(session, property_ids)
    return references


def _relation_references(session: Session, relation_ids: Collection[uuid.UUID]) -> list[Reference]:
    return [
        _rule_reference(session, rule, role="as the relation it works on")
        for rule in session.scalars(
            select(ActionRule).where(ActionRule.target_relation_id.in_(list(relation_ids)))
        )
    ]


def _property_references(session: Session, property_ids: Collection[uuid.UUID]) -> list[Reference]:
    ids = list(property_ids)
    references = [
        _assignment_reference(session, assignment, role="as the property it assigns")
        for assignment in session.scalars(
            select(ActionRuleAssignment).where(ActionRuleAssignment.target_property_id.in_(ids))
        )
    ]
    references += [
        _assignment_reference(session, assignment, role="as the property it reads")
        for assignment in session.scalars(
            select(ActionRuleAssignment).where(ActionRuleAssignment.object_property_id.in_(ids))
        )
    ]
    references += [
        _operand_reference(session, operand, role="as the property it tests")
        for operand in session.scalars(
            select(ActionCriteriaOperand).where(ActionCriteriaOperand.object_property_id.in_(ids))
        )
    ]
    return references


def _parameter_references(
    session: Session, parameter_ids: Collection[uuid.UUID]
) -> list[Reference]:
    ids = list(parameter_ids)
    references = [
        _rule_reference(session, rule, role="as an endpoint parameter")
        for rule in session.scalars(
            select(ActionRule).where(
                or_(
                    ActionRule.source_parameter_id.in_(ids),
                    ActionRule.target_parameter_id.in_(ids),
                )
            )
        )
    ]
    references += [
        _assignment_reference(session, assignment, role="as a value source")
        for assignment in session.scalars(
            select(ActionRuleAssignment).where(
                or_(
                    ActionRuleAssignment.value_parameter_id.in_(ids),
                    ActionRuleAssignment.object_parameter_id.in_(ids),
                )
            )
        )
    ]
    references += [
        _operand_reference(session, operand, role="as a criteria operand")
        for operand in session.scalars(
            select(ActionCriteriaOperand).where(
                or_(
                    ActionCriteriaOperand.value_parameter_id.in_(ids),
                    ActionCriteriaOperand.object_parameter_id.in_(ids),
                )
            )
        )
    ]
    return references


def _parameter_reference(session: Session, parameter: ActionParameter, *, role: str) -> Reference:
    return _reference(
        session,
        kind=ACTION_PARAMETER,
        action_type_id=parameter.action_type_id,
        label=f"parameter {parameter.name!r} (used {role})",
    )


def _rule_reference(session: Session, rule: ActionRule, *, role: str) -> Reference:
    return _reference(
        session,
        kind=ACTION_RULE,
        action_type_id=rule.action_type_id,
        label=f"the {rule.rule_kind} rule (used {role})",
    )


def _assignment_reference(
    session: Session, assignment: ActionRuleAssignment, *, role: str
) -> Reference:
    rule = session.get(ActionRule, assignment.rule_id)
    if rule is None:
        return Reference(
            kind=ACTION_RULE_ASSIGNMENT,
            action_type_id=uuid.UUID(int=0),
            action_name="?",
            label=f"an assignment ({role})",
        )
    return _reference(
        session,
        kind=ACTION_RULE_ASSIGNMENT,
        action_type_id=rule.action_type_id,
        label=f"an assignment of the {rule.rule_kind} rule (used {role})",
    )


def _operand_reference(session: Session, operand: ActionCriteriaOperand, *, role: str) -> Reference:
    node = session.get(ActionCriteriaNode, operand.node_id)
    action_type_id = node.action_type_id if node is not None else uuid.UUID(int=0)
    return _reference(
        session,
        kind=ACTION_CRITERIA_OPERAND,
        action_type_id=action_type_id,
        label=f"a criteria condition (used {role})",
    )


def _reference(session: Session, *, kind: str, action_type_id: uuid.UUID, label: str) -> Reference:
    action_type = session.get(ActionType, action_type_id)
    name = action_type.name if action_type is not None else str(action_type_id)
    return Reference(kind=kind, action_type_id=action_type_id, action_name=name, label=label)


__all__ = ["Reference", "ensure_deletable", "find_references"]
