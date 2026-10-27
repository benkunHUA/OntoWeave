"""整棵树的形状与补丁校验。

节点与操作数一个个长出来，但约束是**整棵树**层面的事：恰好一个根、根是 `and` 分组、
v1 只放行一层扁平条件、无环。建树路径与补丁路径共用同一份判断。
"""

from __future__ import annotations

import uuid
from collections.abc import Sequence
from dataclasses import replace

from sqlalchemy.orm import Session

import app.services.criteria.shape as shape
from app.errors import InvariantViolationError
from app.models import ActionCriteriaNode
from app.services.criteria.store import (
    LEFT,
    RIGHT,
    get_node,
    get_operand,
    list_nodes,
    list_operands,
    operand_spec,
)
from app.vocabulary import (
    ActionCriteriaLogic,
    ActionCriteriaNodeKind,
)

GROUP = ActionCriteriaNodeKind.GROUP.value
CONDITION = ActionCriteriaNodeKind.CONDITION.value
DEFAULT_LOGIC = ActionCriteriaLogic.AND.value

#: v1 放行的形状：一个 `and` 根 + 一层扁平的 condition。
V1_SHAPE_NOTE = "v1 only supports one 'and' root with flat conditions"


def ensure_tree_shape(session: Session, *, action_type_id: uuid.UUID, where: str) -> None:
    """整棵树的形状校验。空树合法——没有提交校验就是「无条件允许」。"""
    nodes = list_nodes(session, action_type_id)
    if not nodes:
        return
    _ensure_no_cycles(nodes, where=where)
    root = _ensure_single_root(nodes, where=where)
    if root.node_kind != GROUP or root.logic != DEFAULT_LOGIC:
        raise InvariantViolationError(f"{where}: the root must be an 'and' group; {V1_SHAPE_NOTE}")
    for node in nodes:
        if node.id == root.id:
            continue
        if node.node_kind != CONDITION or node.parent_id != root.id:
            raise InvariantViolationError(
                f"{where}: node {node.id} is not a flat condition under the root; {V1_SHAPE_NOTE}"
            )
        shape.ensure_condition_shape(session, node=node, where=f"{where} condition {node.id}")


def _ensure_single_root(nodes: Sequence[ActionCriteriaNode], *, where: str) -> ActionCriteriaNode:
    roots = [node for node in nodes if node.parent_id is None]
    if len(roots) != 1:
        raise InvariantViolationError(
            f"{where}: expected exactly one root node, found {len(roots)}"
        )
    return roots[0]


def _ensure_no_cycles(nodes: Sequence[ActionCriteriaNode], *, where: str) -> None:
    parents = {node.id: node.parent_id for node in nodes}
    for node in nodes:
        seen = {node.id}
        current = parents.get(node.id)
        while current is not None:
            if current in seen:
                raise InvariantViolationError(f"{where}: node {node.id} sits on a cycle")
            seen.add(current)
            current = parents.get(current)


def validate_node_patch(session: Session, *, node_id: uuid.UUID, patch: dict) -> None:
    """改节点前校验：种类不可改（换种类等于换语义），改完整棵树仍然立得住。"""
    node = get_node(session, node_id)
    if "node_kind" in patch and patch["node_kind"] != node.node_kind:
        raise InvariantViolationError(
            f"criteria node {node_id} cannot change its kind; delete it and add a new one"
        )
    if patch.get("parent_id") == node.id:
        raise InvariantViolationError(f"criteria node {node_id} cannot be its own parent")
    ensure_tree_shape(session, action_type_id=node.action_type_id, where="action criteria")


def validate_operand_patch(session: Session, *, operand_id: uuid.UUID, patch: dict) -> None:
    """改操作数前校验：改完仍只对应一个值来源，且所在条件的两侧仍然合法。"""
    operand = get_operand(session, operand_id)
    node = get_node(session, operand.node_id)
    merged = replace(operand_spec(operand), **patch)
    shape.ensure_operand_shape(
        session,
        action_type_id=node.action_type_id,
        operand=merged,
        where=f"action criteria {merged.side} operand",
    )
    sides = {
        spec.side: spec
        for spec in (
            operand_spec(row) for row in list_operands(session, [node.id]) if row.id != operand.id
        )
    }
    sides[merged.side] = merged
    shape.ensure_condition_sides(
        session,
        action_type_id=node.action_type_id,
        operator=node.operator,
        left=sides.get(LEFT),
        right=sides.get(RIGHT),
        where=f"action criteria condition {node.id}",
    )
