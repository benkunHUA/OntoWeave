"""模型视角的动作操作：读动作、写动作草稿的对外形状。

领域规则在 service 层（`element_edit` / `action_detail`），这里只做两件事：把参数转过去，
把结果转成模型看得懂的字段。所以它不需要 LangChain，可以直接单测。

读出来的形状就是写进去的形状：`get_action` 给的条件列表可以原样交回 `set_action_criteria`。
"""

from __future__ import annotations

import uuid
from collections.abc import Mapping, Sequence
from typing import Any

from sqlalchemy.orm import Session

from app.agent.tools.graph_read import (
    load_membership,
    object_type_brief,
    property_brief,
    relation_brief,
)
from app.errors import NotFoundError
from app.models import ActionParameter, ActionRule, ActionRuleAssignment, ObjectType
from app.services import (
    action,
    action_detail,
    action_rules,
    criteria,
    element_edit,
    embedding,
    ontology,
)
from app.services.criteria import CONDITION, NodeRead, OperandSpec
from app.vocabulary import ActionParameterKind


def action_type_brief(action_type: Any) -> dict:
    """模型看到的动作摘要：够它判断「这是不是我要的那个」，不带内部字段。"""
    return {
        "id": str(action_type.id),
        "name": action_type.name,
        "description": action_type.description,
        "status": action_type.status,
    }


def parameter_brief(parameter: ActionParameter) -> dict:
    return {
        "id": str(parameter.id),
        "name": parameter.name,
        "description": parameter.description,
        "kind": parameter.kind,
        "datatype": parameter.datatype,
        "target_object_type_id": (
            str(parameter.target_object_type_id) if parameter.target_object_type_id else None
        ),
        "is_array": parameter.is_array,
        "is_required": parameter.is_required,
        "default_value": parameter.default_value,
        "enum_values": parameter.enum_values,
    }


def assignment_brief(assignment: ActionRuleAssignment) -> dict:
    return {
        "target_property_id": str(assignment.target_property_id),
        "value_source": assignment.value_source,
        "value_parameter_id": (
            str(assignment.value_parameter_id) if assignment.value_parameter_id else None
        ),
        "object_parameter_id": (
            str(assignment.object_parameter_id) if assignment.object_parameter_id else None
        ),
        "object_property_id": (
            str(assignment.object_property_id) if assignment.object_property_id else None
        ),
        "static_value": assignment.static_value,
    }


def rule_brief(rule: ActionRule, assignments: Sequence[ActionRuleAssignment]) -> dict:
    return {
        "id": str(rule.id),
        "rule_kind": rule.rule_kind,
        "target_object_type_id": (
            str(rule.target_object_type_id) if rule.target_object_type_id else None
        ),
        "target_relation_id": str(rule.target_relation_id) if rule.target_relation_id else None,
        "source_parameter_id": str(rule.source_parameter_id) if rule.source_parameter_id else None,
        "target_parameter_id": str(rule.target_parameter_id) if rule.target_parameter_id else None,
        "assignments": [assignment_brief(assignment) for assignment in assignments],
    }


def operand_brief(operand: OperandSpec | None) -> dict | None:
    if operand is None:
        return None
    return {
        "kind": operand.kind,
        "value_parameter_id": (
            str(operand.value_parameter_id) if operand.value_parameter_id else None
        ),
        "object_parameter_id": (
            str(operand.object_parameter_id) if operand.object_parameter_id else None
        ),
        "object_property_id": (
            str(operand.object_property_id) if operand.object_property_id else None
        ),
        "static_value": operand.static_value,
    }


def conditions_from_tree(nodes: Sequence[NodeRead]) -> list[dict]:
    """把条件树压成扁平的条件列表：v1 只有一个 `and` 根，分组层对模型没有意义。"""
    conditions: list[dict] = []
    for node in nodes:
        if node.node_kind == CONDITION:
            conditions.append(
                {
                    "operator": node.operator,
                    "failure_message": node.failure_message,
                    "left": operand_brief(node.left),
                    "right": operand_brief(node.right),
                }
            )
        conditions.extend(conditions_from_tree(node.children))
    return conditions


def search_actions(
    session: Session,
    *,
    query: str | None,
    embedder: embedding.Embedder,
    owner_id: uuid.UUID,
    limit: int = 10,
) -> list[dict]:
    """按语义找已有动作；不给 query 就按名称列出全部（含草稿，看 status）。"""
    if query is None:
        return [
            action_type_brief(item)
            for item in action.list_action_types(session, owner_id=owner_id, limit=limit)
        ]
    hits = embedding.search_action_types(
        session, query=query, embedder=embedder, owner_id=owner_id, limit=limit
    )
    return [{**action_type_brief(item), "similarity": round(score, 4)} for item, score in hits]


def get_action(session: Session, *, action_type_id: uuid.UUID, owner_id: uuid.UUID) -> dict:
    """一个动作的全量定义：参数、规则（含赋值）、提交校验。"""
    detail = action_detail.load_action_detail(session, action_type_id, owner_id=owner_id)
    return {
        **action_type_brief(detail.action),
        "parameters": [parameter_brief(parameter) for parameter in detail.parameters],
        "rules": [rule_brief(item.rule, item.assignments) for item in detail.rules],
        "criteria": conditions_from_tree(detail.criteria),
    }


def get_object_type(
    session: Session, *, graph_id: uuid.UUID, object_type_id: uuid.UUID, owner_id: uuid.UUID
) -> dict:
    """一个对象类型的全貌：属性 + 它在这张图里的关系。

    写规则、赋值与校验条件时都要用这些 id：光看名称没法落到 `target_property_id`。
    """
    membership = load_membership(session, graph_id=graph_id, owner_id=owner_id)
    if object_type_id not in membership.node_ids:
        raise NotFoundError(f"object type {object_type_id} is not in graph {graph_id}")
    obj = session.get(ObjectType, object_type_id)
    if obj is None:
        raise NotFoundError(f"object type {object_type_id} not found")
    return {
        **object_type_brief(obj),
        "properties": [
            property_brief(prop) for prop in ontology.list_properties(session, object_type_id)
        ],
        "relations": [
            relation_brief(relation)
            for relation in membership.relations
            if object_type_id in (relation.source_type_id, relation.target_type_id)
        ],
    }


def propose_action(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    name: str,
    description: str | None = None,
) -> dict:
    """新建草稿动作类型并挂进当前图。要不要复用已有的，先 `search_actions` 再定。"""
    created = element_edit.create_action_type(
        session,
        changeset_id=changeset_id,
        owner_id=owner_id,
        name=name,
        description=description,
    )
    return action_type_brief(created)


def reuse_action(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    action_type_id: uuid.UUID,
) -> dict:
    """把共享池里已确认的动作并进当前图，不新建。"""
    reused = element_edit.reuse_action_type(
        session,
        changeset_id=changeset_id,
        owner_id=owner_id,
        action_type_id=action_type_id,
    )
    return {**action_type_brief(reused), "reused": True}


def propose_action_parameter(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
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
) -> dict:
    parameter = element_edit.create_action_parameter(
        session,
        changeset_id=changeset_id,
        owner_id=owner_id,
        action_type_id=action_type_id,
        name=name,
        kind=kind,
        description=description,
        datatype=datatype,
        target_object_type_id=target_object_type_id,
        is_array=is_array,
        is_required=is_required,
        default_value=default_value,
        enum_values=enum_values,
    )
    return parameter_brief(parameter)


def propose_action_rule(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    action_type_id: uuid.UUID,
    rule_kind: str,
    target_object_type_id: uuid.UUID | None = None,
    target_relation_id: uuid.UUID | None = None,
    source_parameter_id: uuid.UUID | None = None,
    target_parameter_id: uuid.UUID | None = None,
    assignments: Sequence[Mapping[str, Any]] = (),
) -> dict:
    """加一条规则；字段赋值内联，省得模型再补一轮。"""
    rule = element_edit.create_action_rule(
        session,
        changeset_id=changeset_id,
        owner_id=owner_id,
        action_type_id=action_type_id,
        rule_kind=rule_kind,
        target_object_type_id=target_object_type_id,
        target_relation_id=target_relation_id,
        source_parameter_id=source_parameter_id,
        target_parameter_id=target_parameter_id,
        assignments=assignments,
    )
    return rule_brief(rule, action_rules.list_assignments(session, rule.id))


def set_action_criteria(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    action_type_id: uuid.UUID,
    conditions: Sequence[Mapping[str, Any]] = (),
) -> dict:
    """整棵替换提交校验；返回落库后的完整条件列表。"""
    element_edit.replace_action_criteria(
        session,
        changeset_id=changeset_id,
        owner_id=owner_id,
        action_type_id=action_type_id,
        conditions=conditions,
    )
    return {"criteria": conditions_from_tree(criteria.load_tree(session, action_type_id))}
