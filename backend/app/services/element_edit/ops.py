"""编辑操作的数据形态。

一次「往改动集里写东西」的服务层入参：改已有元素、建新元素、标记删除。接口层与
工具层把 HTTP / 模型参数转成这里的 dataclass，`apply` 只认这些形状。

种类名不在这里重写：它们是 `element_kinds` 登记表的键，改动集 op payload 用的也是
同一份值。
"""

from __future__ import annotations

import uuid
from collections.abc import Mapping
from dataclasses import dataclass, field
from typing import Any

from app.services.element_kinds import (
    ACTION_CRITERIA_NODE,
    ACTION_CRITERIA_OPERAND,
    ACTION_PARAMETER,
    ACTION_RULE,
    ACTION_RULE_ASSIGNMENT,
    ACTION_TYPE,
    OBJECT_TYPE,
    PROPERTY,
    RELATION,
)
from app.vocabulary import (
    DEFAULT_CARDINALITY,
    DEFAULT_DATATYPE,
    ActionParameterKind,
)

__all__ = [
    "ACTION_CRITERIA_NODE",
    "ACTION_CRITERIA_OPERAND",
    "ACTION_PARAMETER",
    "ACTION_RULE",
    "ACTION_RULE_ASSIGNMENT",
    "ACTION_TYPE",
    "OBJECT_TYPE",
    "PROPERTY",
    "RELATION",
    "ActionCriteriaReplace",
    "ActionParameterCreate",
    "ActionRuleAssignmentCreate",
    "ActionRuleCreate",
    "ActionTypeCreate",
    "EditOp",
    "ElementDelete",
    "ElementUpdate",
    "ObjectTypeCreate",
    "ObjectTypeReuse",
    "PropertyCreate",
    "RelationCreate",
]


@dataclass(frozen=True)
class ElementUpdate:
    """改已有元素的字段：改哪个元素、改哪些字段。"""

    element_kind: str
    element_id: uuid.UUID
    patch: dict


@dataclass(frozen=True)
class ElementDelete:
    """把元素标记删除（草稿阶段不真删，定稿时才删）。"""

    element_kind: str
    element_id: uuid.UUID


@dataclass(frozen=True)
class ObjectTypeCreate:
    """新建一个草稿对象类型。"""

    name: str
    description: str | None = None


@dataclass(frozen=True)
class ObjectTypeReuse:
    """把共享池里已有的类型并进当前图。"""

    object_type_id: uuid.UUID


@dataclass(frozen=True)
class PropertyCreate:
    """给对象类型加一个草稿字段。"""

    owner_type_id: uuid.UUID
    name: str
    datatype: str = DEFAULT_DATATYPE
    required: bool = False
    is_array: bool = False
    is_primary_key: bool = False
    is_title_key: bool = False
    #: datatype = enum 时的候选值；其余类型不给。
    enum_values: list[str] | None = None


@dataclass(frozen=True)
class RelationCreate:
    """新建一条草稿关系。"""

    source_type_id: uuid.UUID
    target_type_id: uuid.UUID
    name: str
    direction: str = "forward"
    cardinality: str = DEFAULT_CARDINALITY
    forward_label: str | None = None
    reverse_label: str | None = None
    foreign_key_property_id: uuid.UUID | None = None


@dataclass(frozen=True)
class ActionTypeCreate:
    """新建一个草稿动作类型。"""

    name: str
    description: str | None = None


@dataclass(frozen=True)
class ActionParameterCreate:
    """给动作类型加一个草稿参数。"""

    action_type_id: uuid.UUID
    name: str
    kind: str = ActionParameterKind.VALUE.value
    description: str | None = None
    datatype: str | None = None
    target_object_type_id: uuid.UUID | None = None
    is_array: bool = False
    is_required: bool = False
    default_value: Any | None = None
    enum_values: list[str] | None = None


@dataclass(frozen=True)
class ActionRuleCreate:
    """给动作类型加一条草稿规则；字段赋值内联，省得调用方再补一轮。"""

    action_type_id: uuid.UUID
    rule_kind: str
    target_object_type_id: uuid.UUID | None = None
    target_relation_id: uuid.UUID | None = None
    source_parameter_id: uuid.UUID | None = None
    target_parameter_id: uuid.UUID | None = None
    assignments: tuple[Mapping[str, Any], ...] = field(default_factory=tuple)


@dataclass(frozen=True)
class ActionRuleAssignmentCreate:
    """给已存在的规则补一条字段赋值。新规则走 `ActionRuleCreate` 内联赋值。"""

    rule_id: uuid.UUID
    target_property_id: uuid.UUID
    value_source: str
    value_parameter_id: uuid.UUID | None = None
    object_parameter_id: uuid.UUID | None = None
    object_property_id: uuid.UUID | None = None
    static_value: Any | None = None


@dataclass(frozen=True)
class ActionCriteriaReplace:
    """整棵替换提交校验：交一份完整条件列表，服务层 diff 出要删、要留、要建的。"""

    action_type_id: uuid.UUID
    conditions: tuple[Mapping[str, Any], ...] = field(default_factory=tuple)


#: 能进改动集的编辑操作。除通用的 update / delete 外，每个 op 按 dataclass 字段直接喂给
#: 同名实现（新建、整棵替换都走这条路），所以字段名必须与那边函数的形参一致。
EditOp = (
    ElementUpdate
    | ElementDelete
    | ObjectTypeCreate
    | ObjectTypeReuse
    | PropertyCreate
    | RelationCreate
    | ActionTypeCreate
    | ActionParameterCreate
    | ActionRuleCreate
    | ActionRuleAssignmentCreate
    | ActionCriteriaReplace
)
