"""动作摘要与完整详情的 JSON 投影。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.services.action.detail import ActionDetail, RuleDetail
from app.services.action.presentation.names import Names
from app.services.action.presentation.rendering import condition_text
from app.services.action.presentation.types import ActionSummary, ParameterSummary
from app.services.criteria.store import NodeRead, OperandSpec


def summary_to_dict(summary: ActionSummary) -> dict[str, Any]:
    return {
        "id": str(summary.id),
        "name": summary.name,
        "description": summary.description,
        "status": summary.status,
        "parameters": [parameter_dict(item) for item in summary.parameters],
        "effects": {
            "objects": [
                {
                    "object_type_id": str(item.object_type_id),
                    "object_type_name": item.object_type_name,
                    "rule_kinds": list(item.rule_kinds),
                }
                for item in summary.object_effects
            ],
            "relations": [
                {
                    "relation_id": str(item.relation_id),
                    "relation_name": item.relation_name,
                    "rule_kinds": list(item.rule_kinds),
                }
                for item in summary.relation_effects
            ],
        },
        "criteria": [
            {
                "condition_id": str(item.condition_id),
                "text": item.text,
                "failure_message": item.failure_message,
            }
            for item in summary.criteria
        ],
    }


def action_detail_to_dict(detail: ActionDetail, session: Session) -> dict[str, Any]:
    names = Names(session, detail.parameters)
    return {
        "id": str(detail.action.id),
        "name": detail.action.name,
        "description": detail.action.description,
        "status": detail.action.status,
        "parameters": [
            {
                **parameter_dict(_parameter_summary(item, names)),
                "order_index": item.order_index,
                "status": item.status,
            }
            for item in detail.parameters
        ],
        "rules": [_rule_dict(item, names) for item in detail.rules],
        "criteria": [_node_dict(item, names) for item in detail.criteria],
    }


def parameter_dict(parameter: ParameterSummary) -> dict[str, Any]:
    return {
        "id": str(parameter.id),
        "name": parameter.name,
        "description": parameter.description,
        "kind": parameter.kind,
        "datatype": parameter.datatype,
        "target_object_type_id": maybe_uuid(parameter.target_object_type_id),
        "target_object_type_name": parameter.target_object_type_name,
        "is_array": parameter.is_array,
        "is_required": parameter.is_required,
        "default_value": parameter.default_value,
        "enum_values": parameter.enum_values,
    }


def _parameter_summary(parameter: Any, names: Names) -> ParameterSummary:
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


def _rule_dict(detail: RuleDetail, names: Names) -> dict[str, Any]:
    rule = detail.rule
    return {
        "id": str(rule.id),
        "rule_kind": rule.rule_kind,
        "target_object_type_id": maybe_uuid(rule.target_object_type_id),
        "target_object_type_name": names.object_type_name(rule.target_object_type_id),
        "target_relation_id": maybe_uuid(rule.target_relation_id),
        "target_relation_name": names.relation_name(rule.target_relation_id),
        "source_parameter_id": maybe_uuid(rule.source_parameter_id),
        "source_parameter_name": names.parameter_name(rule.source_parameter_id),
        "target_parameter_id": maybe_uuid(rule.target_parameter_id),
        "target_parameter_name": names.parameter_name(rule.target_parameter_id),
        "order_index": rule.order_index,
        "status": rule.status,
        "assignments": [_assignment_dict(item, names) for item in detail.assignments],
    }


def _assignment_dict(assignment: Any, names: Names) -> dict[str, Any]:
    return {
        "id": str(assignment.id),
        "target_property_id": str(assignment.target_property_id),
        "target_property_name": names.property_name(assignment.target_property_id),
        "value_source": assignment.value_source,
        "value_parameter_id": maybe_uuid(assignment.value_parameter_id),
        "value_parameter_name": names.parameter_name(assignment.value_parameter_id),
        "object_parameter_id": maybe_uuid(assignment.object_parameter_id),
        "object_parameter_name": names.parameter_name(assignment.object_parameter_id),
        "object_property_id": maybe_uuid(assignment.object_property_id),
        "object_property_name": names.property_name(assignment.object_property_id),
        "static_value": assignment.static_value,
        "status": assignment.status,
    }


def _node_dict(node: NodeRead, names: Names) -> dict[str, Any]:
    return {
        "id": str(node.id),
        "node_kind": node.node_kind,
        "logic": node.logic,
        "operator": node.operator,
        "failure_message": node.failure_message,
        "order_index": node.order_index,
        "text": condition_text(node, names) if node.node_kind == "condition" else None,
        "left": _operand_dict(node.left, names),
        "right": _operand_dict(node.right, names),
        "children": [_node_dict(child, names) for child in node.children],
    }


def _operand_dict(operand: OperandSpec | None, names: Names) -> dict[str, Any] | None:
    if operand is None:
        return None
    return {
        "side": operand.side,
        "kind": operand.kind,
        "value_parameter_id": maybe_uuid(operand.value_parameter_id),
        "value_parameter_name": names.parameter_name(operand.value_parameter_id),
        "object_parameter_id": maybe_uuid(operand.object_parameter_id),
        "object_parameter_name": names.parameter_name(operand.object_parameter_id),
        "object_property_id": maybe_uuid(operand.object_property_id),
        "object_property_name": names.property_name(operand.object_property_id),
        "static_value": operand.static_value,
    }


def maybe_uuid(value: uuid.UUID | None) -> str | None:
    return str(value) if value is not None else None
