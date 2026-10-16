"""提交校验的整棵替换：交一份完整条件列表，服务层 diff 出要删、要留、要建的。

界面与工具层都提交「完整的条件列表」，落库却是逐个节点：值没变的条件原地留着，不再
出现的标记删除，新出现的建出来。diff 在 `criteria.replacement_plan`，这里只负责把它
落成草稿行并登记进改动集。

草稿阶段的删除是延后的（标删的节点还在库里），所以算计划时要把这个改动集里已经标删的
节点排除掉，否则第二次保存又会把它们当成现有条件留下。根分组只建一次、不参与 diff：
树永远得有一个根，删了再建会让库里同时出现两个根。
"""

from __future__ import annotations

import uuid
from collections.abc import Mapping, Sequence
from typing import Any

from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import ActionCriteriaNode
from app.services import changeset, criteria
from app.services.element_edit.ops import (
    ACTION_CRITERIA_NODE,
    ACTION_CRITERIA_OPERAND,
    ACTION_TYPE,
)


def replace_action_criteria(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    action_type_id: uuid.UUID,
    conditions: Sequence[Mapping[str, Any]] = (),
) -> list[ActionCriteriaNode]:
    """整棵替换，返回本次新建的条件节点（原地留下的不算）。"""
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    changeset.load_element(session, ACTION_TYPE, action_type_id, owner_id=owner_id)
    # 计划阶段就把每一条条件校验干净：形状不对时一行都还没写进去。
    plan = criteria.replacement_plan(
        session,
        action_type_id=action_type_id,
        conditions=conditions,
        ignore_node_ids=changeset.pending_delete_ids(session, changeset_id, ACTION_CRITERIA_NODE),
    )
    root = criteria.find_root(session, action_type_id) if plan.additions else None
    if plan.additions and root is None:
        root = criteria.create_group(session, action_type_id=action_type_id, order_index=0)
        _record_create(session, changeset_id, ACTION_CRITERIA_NODE, root.id)

    created: list[ActionCriteriaNode] = []
    for index, spec in enumerate(plan.additions):
        node = _create_condition(
            session,
            action_type_id=action_type_id,
            spec=spec,
            parent_id=_root_id(root),
            order_index=index,
            changeset_id=changeset_id,
        )
        created.append(node)
    for node_id in plan.deletions:
        changeset.record_op(
            session,
            changeset_id=changeset_id,
            op=changeset.OP_DELETE,
            target_id=node_id,
            payload={"kind": ACTION_CRITERIA_NODE},
        )
    return created


def _create_condition(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    spec: criteria.ConditionSpec,
    parent_id: uuid.UUID,
    order_index: int,
    changeset_id: uuid.UUID,
) -> ActionCriteriaNode:
    """把一个校验通过的条件规格落成节点 + 操作数，各记一条 create op。"""
    node = criteria.create_condition(
        session,
        action_type_id=action_type_id,
        operator=spec.operator,
        failure_message=spec.failure_message,
        parent_id=parent_id,
        order_index=order_index,
    )
    _record_create(session, changeset_id, ACTION_CRITERIA_NODE, node.id)
    for operand_spec in (spec.left, spec.right):
        if operand_spec is None:
            continue
        operand = criteria.create_operand(
            session,
            node_id=node.id,
            side=operand_spec.side,
            kind=operand_spec.kind,
            value_parameter_id=operand_spec.value_parameter_id,
            object_parameter_id=operand_spec.object_parameter_id,
            object_property_id=operand_spec.object_property_id,
            static_value=operand_spec.static_value,
        )
        _record_create(session, changeset_id, ACTION_CRITERIA_OPERAND, operand.id)
    return node


def _root_id(root: ActionCriteriaNode | None) -> uuid.UUID:
    """到这里根必然存在：有新增条件时要么复用旧根，要么刚建了一个。"""
    if root is None:
        raise InvariantViolationError("action criteria has no root group")
    return root.id


def _record_create(
    session: Session, changeset_id: uuid.UUID, element_kind: str, element_id: uuid.UUID
) -> None:
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=changeset.OP_CREATE,
        target_id=element_id,
        payload={"kind": element_kind},
    )
