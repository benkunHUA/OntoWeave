"""动作展示层的数据形状与文案常量。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

OPERATOR_LABELS = {
    "is": "是",
    "is not": "不是",
    "is null": "为空",
    "is not null": "不为空",
    "<": "小于",
    "<=": "小于等于",
    ">": "大于",
    ">=": "大于等于",
}
DATATYPE_LABELS = {
    "text": "文本",
    "integer": "整数",
    "decimal": "数值",
    "boolean": "布尔",
    "date": "日期",
    "timestamp": "时间",
    "enum": "枚举",
}
RULE_VERBS = {
    "create_object": "新增",
    "modify_object": "修改",
    "delete_object": "删除",
    "create_link": "新增",
    "delete_link": "删除",
}


@dataclass(frozen=True)
class ParameterSummary:
    id: uuid.UUID
    name: str
    description: str | None
    kind: str
    datatype: str | None
    target_object_type_id: uuid.UUID | None
    target_object_type_name: str | None
    is_array: bool
    is_required: bool
    default_value: Any | None
    enum_values: list[str] | None


@dataclass(frozen=True)
class ObjectEffect:
    object_type_id: uuid.UUID
    object_type_name: str | None
    rule_kinds: tuple[str, ...]


@dataclass(frozen=True)
class RelationEffect:
    relation_id: uuid.UUID
    relation_name: str | None
    rule_kinds: tuple[str, ...]


@dataclass(frozen=True)
class CriteriaSummary:
    condition_id: uuid.UUID
    text: str
    failure_message: str | None


@dataclass(frozen=True)
class ActionSummary:
    id: uuid.UUID
    name: str
    description: str | None
    status: str
    parameters: tuple[ParameterSummary, ...]
    object_effects: tuple[ObjectEffect, ...]
    relation_effects: tuple[RelationEffect, ...]
    criteria: tuple[CriteriaSummary, ...]
