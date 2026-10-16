"""动作详情的读模型：基本信息 + 参数 + 规则（含字段赋值）+ 提交校验树。

详情接口与动作工具都要这份东西，组装收在这里——它们各自再拼一遍就会走样。
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass

from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import ActionParameter, ActionRule, ActionRuleAssignment, ActionType
from app.services import action, action_rules, criteria


@dataclass(frozen=True)
class RuleDetail:
    """一条规则与它的字段赋值：赋值是规则的子元素，读的时候跟着一起给。"""

    rule: ActionRule
    assignments: list[ActionRuleAssignment]


@dataclass(frozen=True)
class ActionDetail:
    """一个动作类型的全量定义。`criteria` 是条件树的根节点列表。"""

    action: ActionType
    parameters: list[ActionParameter]
    rules: list[RuleDetail]
    criteria: list[criteria.NodeRead]


def load_action_detail(
    session: Session, action_type_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> ActionDetail:
    action_type = action.get_action_type(session, action_type_id)
    if owner_id is not None and action_type.owner_id != owner_id:
        raise NotFoundError(f"action type {action_type_id} not found")
    return ActionDetail(
        action=action_type,
        parameters=action.list_parameters(session, action_type_id),
        rules=[
            RuleDetail(rule=rule, assignments=action_rules.list_assignments(session, rule.id))
            for rule in action_rules.list_rules(session, action_type_id)
        ],
        criteria=criteria.load_tree(session, action_type_id),
    )
