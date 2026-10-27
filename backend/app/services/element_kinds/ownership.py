"""元素归属、确认流转与连带删除。"""

from __future__ import annotations

import uuid
from collections.abc import Callable, Sequence
from typing import Any

from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import ActionCriteriaNode, ActionRule, ActionType, ObjectType
from app.services import object_type, property, relation, status
from app.services.action.parameters import list_parameters
from app.services.action.rules.assignments import list_assignments
from app.services.action.rules.rules import list_rules
from app.services.action.types import transition_action_type
from app.services.criteria.store import list_nodes, list_operands, nodes_by_depth_desc
from app.services.element_kinds.types import (
    ACTION_CRITERIA_NODE,
    ACTION_CRITERIA_OPERAND,
    ACTION_PARAMETER,
    ACTION_RULE,
    ACTION_RULE_ASSIGNMENT,
)


def owner_id_attribute(session: Session, element: Any) -> uuid.UUID | None:
    """元素自带 owner_id 时直接取（对象类型、关系、动作类型都是共享池元素）。"""
    del session
    return getattr(element, "owner_id", None)


def property_owner_id(session: Session, element: Any) -> uuid.UUID | None:
    """属性的归属看它所属的对象类型。"""
    owner_type = session.get(ObjectType, element.owner_type_id)
    return owner_type.owner_id if owner_type is not None else None


def action_type_owner_id(session: Session, element: Any) -> uuid.UUID | None:
    """动作子元素的归属看它所属的动作类型。"""
    action_type = session.get(ActionType, element.action_type_id)
    return action_type.owner_id if action_type is not None else None


def assignment_owner_id(session: Session, element: Any) -> uuid.UUID | None:
    """赋值挂在规则上，归属要顺着规则往上找。"""
    rule = session.get(ActionRule, element.rule_id)
    return action_type_owner_id(session, rule) if rule is not None else None


def operand_owner_id(session: Session, element: Any) -> uuid.UUID | None:
    """条件操作数挂在条件节点上，归属再往上找一层。"""
    node = session.get(ActionCriteriaNode, element.node_id)
    return action_type_owner_id(session, node) if node is not None else None


def promote_object_type(session: Session, element_id: uuid.UUID, *, owner_id=None) -> None:
    object_type.transition_object_type(session, element_id, status.CONFIRMED, owner_id=owner_id)


def promote_property(session: Session, element_id: uuid.UUID, *, owner_id=None) -> None:
    property.transition_property(session, element_id, status.CONFIRMED, owner_id=owner_id)


def promote_relation(session: Session, element_id: uuid.UUID, *, owner_id=None) -> None:
    relation.transition_relation(session, element_id, status.CONFIRMED, owner_id=owner_id)


def promote_action_type(session: Session, element_id: uuid.UUID, *, owner_id=None) -> None:
    transition_action_type(session, element_id, status.CONFIRMED, owner_id=owner_id)


def promote_plain(
    kind: str, model: type, load_owner_id: Callable[[Session, Any], uuid.UUID | None]
) -> Callable[..., None]:
    """动作层子元素的确认：只翻状态，没有别的副作用。"""

    def promote(session: Session, element_id: uuid.UUID, *, owner_id=None) -> None:
        element = session.get(model, element_id)
        if element is None:
            raise NotFoundError(f"{kind} {element_id} not found")
        if owner_id is not None and load_owner_id(session, element) != owner_id:
            raise NotFoundError(f"{kind} {element_id} not found")
        status.ensure_transition(element.status, status.CONFIRMED, f"{kind} {element_id}")
        element.status = status.CONFIRMED
        session.flush()

    return promote


def action_type_dependents(
    session: Session, action_type_id: uuid.UUID
) -> Sequence[tuple[str, uuid.UUID]]:
    """删动作类型时要一并删掉、又没法靠数据库级联一次带走的子元素。

    子元素对父元素是 ON DELETE CASCADE，但赋值 / 操作数对参数是 RESTRICT：一条
    DELETE 直接删动作类型会在删参数那一步撞外键。所以子元素逐个列出来，各记一条
    删除 op，由 delete_phase 排出「赋值 → 规则 → 参数 → 动作类型」的顺序。
    """
    dependents: list[tuple[str, uuid.UUID]] = []
    for rule in list_rules(session, action_type_id):
        dependents.append((ACTION_RULE, rule.id))
        dependents.extend(
            (ACTION_RULE_ASSIGNMENT, assignment.id)
            for assignment in list_assignments(session, rule.id)
        )
    nodes = list_nodes(session, action_type_id)
    dependents.extend((ACTION_CRITERIA_NODE, node.id) for node in nodes)
    dependents.extend(
        (ACTION_CRITERIA_OPERAND, operand.id)
        for operand in list_operands(session, [node.id for node in nodes])
    )
    dependents.extend(
        (ACTION_PARAMETER, parameter.id) for parameter in list_parameters(session, action_type_id)
    )
    return dependents


def order_criteria_nodes_by_depth(session: Session, elements: Sequence[Any]) -> Sequence[Any]:
    """条件节点是自引用邻接表：同一档位里必须子先父后。"""
    del session
    return nodes_by_depth_desc(elements)
