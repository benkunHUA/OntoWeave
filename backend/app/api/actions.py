from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.deps import get_current_user_id, get_session
from app.schemas.action import (
    ActionAssignmentRead,
    ActionCriteriaNodeRead,
    ActionDetailRead,
    ActionOperandRead,
    ActionParameterRead,
    ActionRuleRead,
    ActionTypeRead,
)
from app.services import action, action_detail, criteria

router = APIRouter(tags=["actions"])


@router.get("/actions", response_model=list[ActionTypeRead])
def list_actions(
    q: str | None = Query(None, description="按名称 / 描述做子串匹配"),
    status: str | None = Query(None, description="只看某个状态的动作"),
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    session: Session = Depends(get_session),
) -> list[ActionTypeRead]:
    actions = action.list_action_types(
        session,
        owner_id=get_current_user_id(),
        query=q,
        status_value=status,
        limit=limit,
        offset=offset,
    )
    return [ActionTypeRead.model_validate(item) for item in actions]


@router.get("/actions/{action_type_id}", response_model=ActionDetailRead)
def get_action(
    action_type_id: uuid.UUID, session: Session = Depends(get_session)
) -> ActionDetailRead:
    detail = action_detail.load_action_detail(
        session, action_type_id, owner_id=get_current_user_id()
    )
    action_type = detail.action
    return ActionDetailRead(
        id=action_type.id,
        name=action_type.name,
        description=action_type.description,
        status=action_type.status,
        parameters=[ActionParameterRead.model_validate(item) for item in detail.parameters],
        rules=[_rule_read(item) for item in detail.rules],
        criteria=[_criteria_read(node) for node in detail.criteria],
    )


def _rule_read(detail: action_detail.RuleDetail) -> ActionRuleRead:
    rule = detail.rule
    return ActionRuleRead(
        id=rule.id,
        rule_kind=rule.rule_kind,
        target_object_type_id=rule.target_object_type_id,
        target_relation_id=rule.target_relation_id,
        source_parameter_id=rule.source_parameter_id,
        target_parameter_id=rule.target_parameter_id,
        order_index=rule.order_index,
        status=rule.status,
        assignments=[
            ActionAssignmentRead.model_validate(assignment) for assignment in detail.assignments
        ],
    )


def _criteria_read(node: criteria.NodeRead) -> ActionCriteriaNodeRead:
    return ActionCriteriaNodeRead(
        id=node.id,
        node_kind=node.node_kind,
        logic=node.logic,
        operator=node.operator,
        failure_message=node.failure_message,
        order_index=node.order_index,
        left=_operand_read(node.left),
        right=_operand_read(node.right),
        children=[_criteria_read(child) for child in node.children],
    )


def _operand_read(spec: criteria.OperandSpec | None) -> ActionOperandRead | None:
    if spec is None:
        return None
    return ActionOperandRead(
        side=spec.side,
        kind=spec.kind,
        value_parameter_id=spec.value_parameter_id,
        object_parameter_id=spec.object_parameter_id,
        object_property_id=spec.object_property_id,
        static_value=spec.static_value,
    )
