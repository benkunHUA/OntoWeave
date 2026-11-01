"""行动定义的可发布 / 可执行状态判定。"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import ActionCriteriaNode, ActionCriteriaOperand
from app.services import status
from app.services.action.detail import ActionDetail

PUBLISHABLE_STATUSES = frozenset({status.CONFIRMED, status.APPROVED})


def unpublished_elements(session: Session, detail: ActionDetail) -> list[str]:
    """返回未确认的行动子元素标签；空列表表示整份定义已经确认。"""
    labels: list[str] = []
    action = detail.action
    if action.status not in PUBLISHABLE_STATUSES:
        labels.append(f"action {action.name!r}")
    labels.extend(
        f"parameter {item.name!r}"
        for item in detail.parameters
        if item.status not in PUBLISHABLE_STATUSES
    )
    for item in detail.rules:
        if item.rule.status not in PUBLISHABLE_STATUSES:
            labels.append(f"rule {item.rule.rule_kind!r} of action {action.name!r}")
        labels.extend(
            f"assignment of action {action.name!r}"
            for assignment in item.assignments
            if assignment.status not in PUBLISHABLE_STATUSES
        )
    bad_node = session.scalar(
        select(ActionCriteriaNode.id)
        .where(
            ActionCriteriaNode.action_type_id == action.id,
            ~ActionCriteriaNode.status.in_(PUBLISHABLE_STATUSES),
        )
        .limit(1)
    )
    if bad_node is not None:
        labels.append(f"criteria of action {action.name!r}")
    bad_operand = session.scalar(
        select(ActionCriteriaOperand.id)
        .join(ActionCriteriaNode, ActionCriteriaNode.id == ActionCriteriaOperand.node_id)
        .where(
            ActionCriteriaNode.action_type_id == action.id,
            ~ActionCriteriaOperand.status.in_(PUBLISHABLE_STATUSES),
        )
        .limit(1)
    )
    if bad_operand is not None:
        labels.append(f"criteria operand of action {action.name!r}")
    return labels
