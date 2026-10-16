"""动作类型的读写模型。

读：画布只要 brief，详情给全量定义（参数 / 规则 + 赋值 / 条件树）。
写：编辑走 `POST /graphs/{id}/edits`，所以这里只有嵌在编辑 op 里的入参模型
（`AssignmentIn` / `ConditionIn` / `OperandIn`）——它们是与工具层共用的形状。
"""

from __future__ import annotations

import uuid
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ActionTypeRead(BaseModel):
    """列表与画布用的基本信息。"""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    description: str | None
    status: str


class ActionParameterRead(BaseModel):
    """一个参数；`kind` 决定 datatype / target_object_type_id 哪边有值。"""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    description: str | None
    kind: str
    datatype: str | None
    target_object_type_id: uuid.UUID | None
    is_array: bool
    is_required: bool
    default_value: Any | None
    enum_values: list[str] | None
    order_index: int
    status: str


class ActionAssignmentRead(BaseModel):
    """规则里的一条字段赋值：目标属性 ← 一个值来源。"""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    target_property_id: uuid.UUID
    value_source: str
    value_parameter_id: uuid.UUID | None
    object_parameter_id: uuid.UUID | None
    object_property_id: uuid.UUID | None
    static_value: Any | None
    status: str


class ActionRuleRead(BaseModel):
    """一条规则与它的字段赋值。"""

    id: uuid.UUID
    rule_kind: str
    target_object_type_id: uuid.UUID | None
    target_relation_id: uuid.UUID | None
    source_parameter_id: uuid.UUID | None
    target_parameter_id: uuid.UUID | None
    order_index: int
    status: str
    assignments: list[ActionAssignmentRead] = Field(default_factory=list)


class ActionOperandRead(BaseModel):
    """条件的一侧操作数。"""

    side: str
    kind: str
    value_parameter_id: uuid.UUID | None
    object_parameter_id: uuid.UUID | None
    object_property_id: uuid.UUID | None
    static_value: Any | None


class ActionCriteriaNodeRead(BaseModel):
    """条件树的一个节点。v1 只长一层：一个 `and` 根 + 扁平的条件。"""

    id: uuid.UUID
    node_kind: str
    logic: str | None
    operator: str | None
    failure_message: str | None
    order_index: int
    left: ActionOperandRead | None
    right: ActionOperandRead | None
    children: list[ActionCriteriaNodeRead] = Field(default_factory=list)


class ActionDetailRead(ActionTypeRead):
    """动作详情：基本信息 + 参数 + 规则 + 提交校验树（根节点列表）。"""

    parameters: list[ActionParameterRead] = Field(default_factory=list)
    rules: list[ActionRuleRead] = Field(default_factory=list)
    criteria: list[ActionCriteriaNodeRead] = Field(default_factory=list)


class OperandIn(BaseModel):
    """条件一侧的入参：`kind` 决定哪几列有效，具体形状由服务层校验。"""

    kind: str
    value_parameter_id: uuid.UUID | None = None
    object_parameter_id: uuid.UUID | None = None
    object_property_id: uuid.UUID | None = None
    static_value: Any | None = None


class ConditionIn(BaseModel):
    """一条提交校验条件；判空类运算符没有右操作数。"""

    operator: str
    failure_message: str | None = None
    left: OperandIn
    right: OperandIn | None = None


class AssignmentIn(BaseModel):
    """一条字段赋值的入参。"""

    target_property_id: uuid.UUID
    value_source: str
    value_parameter_id: uuid.UUID | None = None
    object_parameter_id: uuid.UUID | None = None
    object_property_id: uuid.UUID | None = None
    static_value: Any | None = None
