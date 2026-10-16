"""画布上的动作：动作节点要的最小信息，以及它与本体元素之间的 `involves` 关系。

`involves` 是「这个动作会碰到哪些本体元素」，从参数与规则推导：对象引用参数指向的对象
类型、对象规则作用的对象类型、链接规则作用的连同它两端的对象类型。定义的全量内容走
详情接口，画布只要这些 id 来连线。
"""

from __future__ import annotations

import uuid
from collections import defaultdict
from collections.abc import Collection, Iterable
from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import ActionParameter, ActionRule, ActionType, GraphAction, Relation
from app.services import element_kinds
from app.vocabulary import ActionParameterKind


@dataclass(frozen=True)
class Involves:
    """动作碰到的一个本体元素：画布上据此连一条 `involves` 边。"""

    kind: str
    id: uuid.UUID


@dataclass(frozen=True)
class ActionBrief:
    """画布上的动作节点：基本信息 + 涉及的本体元素。"""

    action: ActionType
    involves: tuple[Involves, ...]


def list_graph_action_briefs(session: Session, graph_id: uuid.UUID) -> list[ActionBrief]:
    """图上挂着的动作，按名称排序（与对象类型、关系的排序一致）。"""
    actions = list(
        session.scalars(
            select(ActionType)
            .join(GraphAction, GraphAction.action_type_id == ActionType.id)
            .where(GraphAction.graph_id == graph_id)
            .order_by(ActionType.name, ActionType.id)
        )
    )
    involved = _involves_by_action(session, [action.id for action in actions])
    return [ActionBrief(action=action, involves=involved.get(action.id, ())) for action in actions]


def _involves_by_action(
    session: Session, action_type_ids: Collection[uuid.UUID]
) -> dict[uuid.UUID, tuple[Involves, ...]]:
    """一次把一批动作的参数与规则查完，避免画布按动作数发查询。"""
    if not action_type_ids:
        return {}
    ids = list(action_type_ids)
    object_types: dict[uuid.UUID, set[uuid.UUID]] = defaultdict(set)
    relation_ids: dict[uuid.UUID, set[uuid.UUID]] = defaultdict(set)

    for parameter in session.scalars(
        select(ActionParameter).where(
            ActionParameter.action_type_id.in_(ids),
            ActionParameter.kind == ActionParameterKind.OBJECT_REF.value,
        )
    ):
        if parameter.target_object_type_id is not None:
            object_types[parameter.action_type_id].add(parameter.target_object_type_id)

    rules = list(session.scalars(select(ActionRule).where(ActionRule.action_type_id.in_(ids))))
    for rule in rules:
        if rule.target_object_type_id is not None:
            object_types[rule.action_type_id].add(rule.target_object_type_id)
        if rule.target_relation_id is not None:
            relation_ids[rule.action_type_id].add(rule.target_relation_id)

    endpoints = _relation_endpoints(session, _flatten(relation_ids.values()))
    for action_type_id, ids_of_action in relation_ids.items():
        for relation_id in ids_of_action:
            object_types[action_type_id] |= endpoints.get(relation_id, set())

    return {
        action_type_id: _sorted_involves(object_types[action_type_id], relation_ids[action_type_id])
        for action_type_id in ids
    }


def _relation_endpoints(
    session: Session, relation_ids: Collection[uuid.UUID]
) -> dict[uuid.UUID, set[uuid.UUID]]:
    """一条关系两端各自是哪个对象类型。"""
    if not relation_ids:
        return {}
    rows = session.scalars(select(Relation).where(Relation.id.in_(list(relation_ids))))
    return {row.id: {row.source_type_id, row.target_type_id} for row in rows}


def _flatten(groups: Iterable[set[uuid.UUID]]) -> set[uuid.UUID]:
    return {element for group in groups for element in group}


def _sorted_involves(
    object_type_ids: Collection[uuid.UUID], relation_ids: Collection[uuid.UUID]
) -> tuple[Involves, ...]:
    """对象类型在前、关系在后；各自按 id 排序，保证同一份定义每次输出一致。"""
    return tuple(
        [Involves(kind=element_kinds.OBJECT_TYPE, id=item) for item in sorted(object_type_ids)]
        + [Involves(kind=element_kinds.RELATION, id=item) for item in sorted(relation_ids)]
    )
