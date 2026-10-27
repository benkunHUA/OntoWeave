"""元素种类名与登记项结构。"""

from __future__ import annotations

import uuid
from collections.abc import Callable, Collection, Sequence
from dataclasses import dataclass
from typing import Any

from sqlalchemy.orm import Session

# 元素种类名。改动集 op 的 payload、编辑操作的常量、删除保护的分派都用这些值。
OBJECT_TYPE = "object_type"
PROPERTY = "property"
RELATION = "relation"
ACTION_TYPE = "action_type"
ACTION_PARAMETER = "action_parameter"
ACTION_RULE = "action_rule"
ACTION_RULE_ASSIGNMENT = "action_rule_assignment"
ACTION_CRITERIA_NODE = "action_criteria_node"
ACTION_CRITERIA_OPERAND = "action_criteria_operand"

# 元素种类给人看的叫法。审批、回执等用户可见文案统一从这里取，避免各处各写一套。
ELEMENT_KIND_LABELS: dict[str, str] = {
    OBJECT_TYPE: "对象类型",
    PROPERTY: "属性",
    RELATION: "关系",
    ACTION_TYPE: "动作",
    ACTION_PARAMETER: "动作参数",
    ACTION_RULE: "动作规则",
    ACTION_RULE_ASSIGNMENT: "字段赋值",
    ACTION_CRITERIA_NODE: "提交校验条件",
    ACTION_CRITERIA_OPERAND: "条件操作数",
}


@dataclass(frozen=True)
class ElementKind:
    model: type
    restorable_fields: frozenset[str]
    delete_phase: int
    load_owner_id: Callable[[Session, Any], uuid.UUID | None]
    promote: Callable[..., None]
    #: 更新校验：第四项是本改动集里已标删、还没落地的同种元素——草稿阶段的删除是延后的，
    #: 它们不该挡住新值（比如删掉「备注」再建一个同名参数）。不需要的种类直接忽略。
    validate_patch: Callable[[Session, uuid.UUID, dict, Collection[uuid.UUID]], None] | None = None
    #: 同一档位内的额外排序（自引用树要子先父后）；不给就按入参顺序。
    order_for_delete: Callable[[Session, Sequence[Any]], Sequence[Any]] | None = None
    #: 删这个元素时还要连带删掉的子元素（数据库级联带不起来的那部分）。
    dependents: Callable[[Session, uuid.UUID], Sequence[tuple[str, uuid.UUID]]] | None = None
