"""动作工具的入参模型。

工具的 description 与这里的 `Field(description=...)` 是给模型看的全部说明，别处再抄一份
就会走样。id 一律用 str：转换在绑定时做，模型编个坏 id 会被 `ToolContext.invoke` 兜成
结构化错误，而不是掀翻整轮。
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field

from app.vocabulary import (
    ACTION_RULE_KINDS,
    ACTION_VALUE_SOURCES,
    PROPERTY_DATATYPES,
    ActionParameterKind,
)


class SearchActionsArgs(BaseModel):
    query: str | None = Field(default=None, description="按语义找动作；不传就列出全部（含草稿）")
    limit: int = Field(default=10, description="最多返回几条")


class GetActionArgs(BaseModel):
    action_type_id: str = Field(description="动作类型 ID；取自 search_actions")


class GetObjectTypeArgs(BaseModel):
    object_type_id: str = Field(description="对象类型 ID；取自 get_subgraph")


class ProposeActionArgs(BaseModel):
    name: str = Field(description="动作名，业务语言，如「指派工单」")
    description: str | None = Field(default=None, description="这个动作做什么；可以后补")


class ReuseActionArgs(BaseModel):
    action_type_id: str = Field(description="要复用的动作类型 ID；取自 search_actions 的候选")


class ProposeActionParameterArgs(BaseModel):
    action_type_id: str = Field(description="挂在哪个动作上；取自 propose_action")
    name: str = Field(description="参数名，业务语言，如「工单」「新金额」")
    kind: str = Field(
        default=ActionParameterKind.VALUE.value,
        description="value（值参数，用户填一个值）或 object_ref（对象引用参数，用户选一个对象）",
    )
    description: str | None = Field(default=None, description="给人看的说明：这个参数该填什么")
    datatype: str | None = Field(
        default=None,
        description="值参数的类型：" + " / ".join(PROPERTY_DATATYPES) + "；对象引用参数不填",
    )
    target_object_type_id: str | None = Field(
        default=None, description="对象引用参数指向的对象类型；值参数不填"
    )
    is_array: bool = Field(default=False, description="是否多值；对象引用参数暂时不支持多值")
    is_required: bool = False
    default_value: Any | None = Field(default=None, description="用户不填时用的默认值")
    enum_values: list[str] | None = Field(
        default=None, description="datatype=enum 时的候选值，至少两个"
    )


class AssignmentArgs(BaseModel):
    target_property_id: str = Field(description="要写哪个属性；取自 get_object_type")
    value_source: str = Field(
        description="值从哪来：" + " / ".join(ACTION_VALUE_SOURCES) + "（parameter=参数、"
        "object_property=另一个对象参数的属性、static=固定值）"
    )
    value_parameter_id: str | None = Field(default=None, description="value_source=parameter 时填")
    object_parameter_id: str | None = Field(
        default=None, description="value_source=object_property 时填：从哪个对象参数取属性"
    )
    object_property_id: str | None = Field(
        default=None, description="value_source=object_property 时填：取哪个属性"
    )
    static_value: Any | None = Field(default=None, description="value_source=static 时填")


class ProposeActionRuleArgs(BaseModel):
    action_type_id: str = Field(description="挂在哪个动作上；取自 propose_action")
    rule_kind: str = Field(
        description="规则种类：" + " / ".join(ACTION_RULE_KINDS) + "。改已有对象用 modify_object，"
        "一对多 / 一对一改的是外键属性；只有多对多才用 create_link / delete_link"
    )
    target_object_type_id: str | None = Field(
        default=None, description="对象规则（增 / 改 / 删）作用的对象类型"
    )
    target_relation_id: str | None = Field(default=None, description="链接规则作用的那个关系")
    source_parameter_id: str | None = Field(
        default=None, description="规则作用的对象参数：对象规则指「改哪个对象」，链接规则指源端"
    )
    target_parameter_id: str | None = Field(default=None, description="链接规则的目标端对象参数")
    assignments: list[AssignmentArgs] = Field(
        default_factory=list,
        description="字段赋值，只有 create_object / modify_object 用；改哪个属性 ← 哪个值来源",
    )


class OperandArgs(BaseModel):
    kind: str = Field(
        description="这一侧是什么：parameter（值参数）/ object_property（对象参数的属性）"
        " / static（固定值）"
    )
    value_parameter_id: str | None = Field(default=None, description="kind=parameter 时填")
    object_parameter_id: str | None = Field(
        default=None, description="kind=object_property 时填：从哪个对象参数取属性"
    )
    object_property_id: str | None = Field(
        default=None, description="kind=object_property 时填：取哪个属性"
    )
    static_value: Any | None = Field(default=None, description="kind=static 时填")


class ConditionArgs(BaseModel):
    operator: str = Field(
        description="运算符：is / is not / is null / is not null / < / <= / > / >=。"
        "比大小只对 integer / decimal / date / timestamp 开放；判空不填右侧"
    )
    failure_message: str | None = Field(default=None, description="不满足时给用户看的话")
    left: OperandArgs = Field(description="左侧：必须是参数或对象的属性")
    right: OperandArgs | None = Field(default=None, description="右侧：判空类运算符没有")


class SetActionCriteriaArgs(BaseModel):
    action_type_id: str = Field(description="挂在哪个动作上；取自 propose_action")
    conditions: list[ConditionArgs] = Field(
        default_factory=list, description="完整的条件列表：留哪些写哪些，不传表示不设校验"
    )
