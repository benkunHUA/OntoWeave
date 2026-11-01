"""读取动作定义并生成摘要；这里只编排，不做 JSON 投影。"""

from __future__ import annotations

import uuid
from collections.abc import Sequence

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvalidRequestError, NotFoundError
from app.models import ActionParameter, ActionType, GraphAction
from app.services.action.detail import ActionDetail, load_action_detail
from app.services.action.presentation.names import Names
from app.services.action.presentation.rendering import condition_text
from app.services.action.presentation.types import (
    ActionSummary,
    CriteriaSummary,
    ObjectEffect,
    ParameterSummary,
    RelationEffect,
)
from app.services.criteria.store import NodeRead
from app.services.graph import core as graph
from app.vocabulary import ActionCriteriaNodeKind


class ActionNotInGraphError(InvalidRequestError):
    pass


def load_action_summary(
    session: Session,
    action_type_id: uuid.UUID,
    *,
    owner_id: uuid.UUID,
    graph_id: uuid.UUID,
) -> ActionSummary:
    detail = load_action_detail_in_graph(
        session, action_type_id, owner_id=owner_id, graph_id=graph_id
    )
    return summarize(session, detail)


def load_action_summaries(
    session: Session, graph_id: uuid.UUID, *, owner_id: uuid.UUID
) -> list[ActionSummary]:
    graph.get_graph(session, graph_id, owner_id=owner_id)
    actions = session.scalars(
        select(ActionType)
        .join(GraphAction, GraphAction.action_type_id == ActionType.id)
        .where(GraphAction.graph_id == graph_id, ActionType.owner_id == owner_id)
        .order_by(ActionType.name, ActionType.id)
    )
    return [
        summarize(session, load_action_detail(session, item.id, owner_id=owner_id))
        for item in actions
    ]


def load_action_detail_in_graph(
    session: Session,
    action_type_id: uuid.UUID,
    *,
    owner_id: uuid.UUID,
    graph_id: uuid.UUID,
) -> ActionDetail:
    graph.get_graph(session, graph_id, owner_id=owner_id)
    if session.get(GraphAction, (graph_id, action_type_id)) is None:
        raise ActionNotInGraphError("action is not in the selected graph")
    try:
        return load_action_detail(session, action_type_id, owner_id=owner_id)
    except NotFoundError as exc:
        raise NotFoundError(f"action type {action_type_id} not found") from exc


def summarize(session: Session, detail: ActionDetail) -> ActionSummary:
    names = Names(session, detail.parameters)
    object_rules: dict[uuid.UUID, list[str]] = {}
    relation_rules: dict[uuid.UUID, list[str]] = {}
    for item in detail.rules:
        rule = item.rule
        if rule.target_object_type_id is not None:
            _append_kind(object_rules, rule.target_object_type_id, rule.rule_kind)
        if rule.target_relation_id is not None:
            _append_kind(relation_rules, rule.target_relation_id, rule.rule_kind)
    return ActionSummary(
        id=detail.action.id,
        name=detail.action.name,
        description=detail.action.description,
        status=detail.action.status,
        parameters=tuple(_parameter_summary(item, names) for item in detail.parameters),
        object_effects=tuple(
            ObjectEffect(
                object_type_id=item_id,
                object_type_name=names.object_type_name(item_id),
                rule_kinds=tuple(kinds),
            )
            for item_id, kinds in object_rules.items()
        ),
        relation_effects=tuple(
            RelationEffect(
                relation_id=item_id,
                relation_name=names.relation_name(item_id),
                rule_kinds=tuple(kinds),
            )
            for item_id, kinds in relation_rules.items()
        ),
        criteria=tuple(_criteria_summaries(detail.criteria, names)),
    )


def _append_kind(grouped: dict[uuid.UUID, list[str]], item_id: uuid.UUID, kind: str) -> None:
    kinds = grouped.setdefault(item_id, [])
    if kind not in kinds:
        kinds.append(kind)


def _parameter_summary(parameter: ActionParameter, names: Names) -> ParameterSummary:
    return ParameterSummary(
        id=parameter.id,
        name=parameter.name,
        description=parameter.description,
        kind=parameter.kind,
        datatype=parameter.datatype,
        target_object_type_id=parameter.target_object_type_id,
        target_object_type_name=names.object_type_name(parameter.target_object_type_id),
        is_array=parameter.is_array,
        is_required=parameter.is_required,
        default_value=parameter.default_value,
        enum_values=parameter.enum_values,
    )


def _criteria_summaries(nodes: Sequence[NodeRead], names: Names) -> list[CriteriaSummary]:
    result: list[CriteriaSummary] = []
    for node in nodes:
        if node.node_kind == ActionCriteriaNodeKind.CONDITION.value:
            result.append(
                CriteriaSummary(
                    condition_id=node.id,
                    text=condition_text(node, names),
                    failure_message=node.failure_message,
                )
            )
        result.extend(_criteria_summaries(node.children, names))
    return result
