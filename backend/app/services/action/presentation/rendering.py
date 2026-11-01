"""把动作投影成图卡文本与一行条件文本。"""

from __future__ import annotations

from typing import Any

from app.services.action.presentation.names import Names
from app.services.action.presentation.types import (
    DATATYPE_LABELS,
    OPERATOR_LABELS,
    RULE_VERBS,
    ActionSummary,
    ParameterSummary,
)
from app.services.criteria.store import NodeRead, OperandSpec
from app.vocabulary import ActionOperandKind, ActionParameterKind


def render_action_card_section(summaries: list[ActionSummary]) -> list[str]:
    if not summaries:
        return []
    lines = ["可执行动作："]
    for summary in summaries:
        lines.append(f"- {summary.name}（id={summary.id}）：{summary.description or '无描述'}")
        if summary.parameters:
            parameters = "、".join(parameter_text(item) for item in summary.parameters)
            lines.append(f"  参数：{parameters}")
        effects = effect_text(summary)
        if effects:
            lines.append(f"  影响：{effects}")
        if summary.criteria:
            lines.append("  校验：" + "；".join(item.text for item in summary.criteria))
    return lines


def parameter_text(parameter: ParameterSummary) -> str:
    if parameter.kind == ActionParameterKind.OBJECT_REF.value:
        shape = f"对象：{parameter.target_object_type_name or '未知类型'}"
    else:
        shape = DATATYPE_LABELS.get(parameter.datatype or "", parameter.datatype or "值")
        if parameter.enum_values:
            shape += "（" + "/".join(parameter.enum_values) + "）"
    required = "必填" if parameter.is_required else "可选"
    return f"{parameter.name}（{shape}，{required}）"


def effect_text(summary: ActionSummary) -> str:
    parts = [
        f"{RULE_VERBS.get(kind, kind)} "
        f"{summary.object_effects[index].object_type_name or '未知对象'}"
        for index in range(len(summary.object_effects))
        for kind in summary.object_effects[index].rule_kinds
    ]
    parts += [
        f"{RULE_VERBS.get(kind, kind)} "
        f"{summary.relation_effects[index].relation_name or '未知关系'}"
        for index in range(len(summary.relation_effects))
        for kind in summary.relation_effects[index].rule_kinds
    ]
    return "；".join(parts)


def condition_text(node: NodeRead, names: Names) -> str:
    left = operand_text(node.left, names)
    operator = OPERATOR_LABELS.get(node.operator or "", node.operator or "")
    right = operand_text(node.right, names)
    return f"{left} {operator} {right}".rstrip()


def operand_text(operand: OperandSpec | None, names: Names) -> str:
    if operand is None:
        return ""
    if operand.kind == ActionOperandKind.PARAMETER.value:
        return names.parameter_name(operand.value_parameter_id) or "?"
    if operand.kind == ActionOperandKind.OBJECT_PROPERTY.value:
        parameter = names.parameter_name(operand.object_parameter_id) or "?"
        prop = names.property_name(operand.object_property_id) or "?"
        return f"{parameter}.{prop}"
    if operand.kind == ActionOperandKind.STATIC.value:
        return format_value(operand.static_value)
    if operand.kind == ActionOperandKind.NULL.value:
        return "空"
    return "?"


def format_value(value: Any) -> str:
    if value is None:
        return "空"
    if isinstance(value, bool):
        return "是" if value else "否"
    return str(value)
