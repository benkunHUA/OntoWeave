from __future__ import annotations

import uuid
from typing import Annotated, Any, Literal

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.action import AssignmentIn, ConditionIn
from app.vocabulary import (
    DEFAULT_CARDINALITY,
    DEFAULT_DATATYPE,
    ActionParameterKind,
)


class ChangesetOpCreate(BaseModel):
    op: Literal["create", "update", "delete"]
    target_id: uuid.UUID | None = None
    payload: dict = Field(default_factory=dict)


class ChangesetOpRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    op: str
    target_id: uuid.UUID | None
    payload: dict


class ChangesetRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    graph_id: uuid.UUID
    status: str


class ChangesetDetail(ChangesetRead):
    ops: list[ChangesetOpRead] = Field(default_factory=list)


# 抽屉里能直接改的元素类型：本体元素 + 动作层的全部子元素。与
# `services/element_kinds.REGISTRY` 的键一一对应（有测试兜住）。
ElementKind = Literal[
    "object_type",
    "property",
    "relation",
    "action_type",
    "action_parameter",
    "action_rule",
    "action_rule_assignment",
    "action_criteria_node",
    "action_criteria_operand",
]


class UpdateEdit(BaseModel):
    """改已有元素：只提交 patch，服务层负责采集 before。"""

    op: Literal["update"] = "update"
    element_kind: ElementKind
    element_id: uuid.UUID
    patch: dict = Field(default_factory=dict)


class CreatePropertyEdit(BaseModel):
    """给对象类型新增一个草稿字段。"""

    op: Literal["create_property"] = "create_property"
    owner_type_id: uuid.UUID
    name: str
    datatype: str = DEFAULT_DATATYPE
    required: bool = False
    is_array: bool = False
    is_primary_key: bool = False
    is_title_key: bool = False
    enum_values: list[str] | None = None


class CreateObjectTypeEdit(BaseModel):
    """新建一个草稿对象类型，并挂进当前图。"""

    op: Literal["create_object_type"] = "create_object_type"
    name: str
    description: str | None = None


class ReuseObjectTypeEdit(BaseModel):
    """把共享池里已确认的类型并进当前图（未定稿的不允许复用）。"""

    op: Literal["reuse_object_type"] = "reuse_object_type"
    object_type_id: uuid.UUID


class CreateRelationEdit(BaseModel):
    """新建一条草稿关系；两端都在共享池里，不存在时会挂上。"""

    op: Literal["create_relation"] = "create_relation"
    source_type_id: uuid.UUID
    target_type_id: uuid.UUID
    name: str
    direction: str = "forward"
    cardinality: str = DEFAULT_CARDINALITY
    forward_label: str | None = None
    reverse_label: str | None = None
    foreign_key_property_id: uuid.UUID | None = None


class DeleteEdit(BaseModel):
    """标记删除：草稿阶段不真删，定稿时才删。"""

    op: Literal["delete"] = "delete"
    element_kind: ElementKind
    element_id: uuid.UUID


class CreateActionTypeEdit(BaseModel):
    """新建一个草稿动作类型；描述可以后补。"""

    op: Literal["create_action_type"] = "create_action_type"
    name: str
    description: str | None = None


class AddActionParameterEdit(BaseModel):
    """给动作类型加一个草稿参数。"""

    op: Literal["add_action_parameter"] = "add_action_parameter"
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


class AddActionRuleEdit(BaseModel):
    """给动作类型加一条草稿规则；字段赋值内联，省得再补一轮。"""

    op: Literal["add_action_rule"] = "add_action_rule"
    action_type_id: uuid.UUID
    rule_kind: str
    target_object_type_id: uuid.UUID | None = None
    target_relation_id: uuid.UUID | None = None
    source_parameter_id: uuid.UUID | None = None
    target_parameter_id: uuid.UUID | None = None
    assignments: list[AssignmentIn] = Field(default_factory=list)


class AddActionRuleAssignmentEdit(BaseModel):
    """给已有规则补一条字段赋值。新规则走 `add_action_rule` 内联赋值。"""

    op: Literal["add_action_rule_assignment"] = "add_action_rule_assignment"
    rule_id: uuid.UUID
    target_property_id: uuid.UUID
    value_source: str
    value_parameter_id: uuid.UUID | None = None
    object_parameter_id: uuid.UUID | None = None
    object_property_id: uuid.UUID | None = None
    static_value: Any | None = None


class SetActionCriteriaEdit(BaseModel):
    """整棵替换提交校验：交一份完整条件列表，空列表就是「不设校验」。"""

    op: Literal["set_action_criteria"] = "set_action_criteria"
    action_type_id: uuid.UUID
    conditions: list[ConditionIn] = Field(default_factory=list)


GraphEditOp = Annotated[
    UpdateEdit
    | CreatePropertyEdit
    | CreateObjectTypeEdit
    | ReuseObjectTypeEdit
    | CreateRelationEdit
    | CreateActionTypeEdit
    | AddActionParameterEdit
    | AddActionRuleEdit
    | AddActionRuleAssignmentEdit
    | SetActionCriteriaEdit
    | DeleteEdit,
    Field(discriminator="op"),
]


class GraphEditsCreate(BaseModel):
    """一次保存里的全部编辑，整批落在同一个改动集上。"""

    edits: list[GraphEditOp] = Field(min_length=1)
