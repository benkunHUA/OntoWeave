"""动作工具对模型暴露的读模型。"""

from __future__ import annotations

from collections.abc import Sequence
from typing import Any

from app.models import ActionParameter, ActionRule, ActionRuleAssignment
from app.services.criteria import CONDITION, NodeRead, OperandSpec


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
