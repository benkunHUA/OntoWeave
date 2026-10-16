"""条件树的读取：节点与操作数、读模型，以及自引用表的删除顺序。

`action_criteria_node` 是邻接表，`parent_id` 自引用成树；操作数挂在节点上，用 `side`
区分左右。整棵树一次查完在内存里拼，避免按层级发一堆查询。
"""

from __future__ import annotations

import uuid
from collections.abc import Sequence
from dataclasses import dataclass
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import ActionCriteriaNode, ActionCriteriaOperand

LEFT = "left"
RIGHT = "right"


@dataclass(frozen=True)
class OperandSpec:
    """一个操作数的六个字段。

    读出来是它，写进去也先收敛成它——建树、补丁校验因此共用同一套形状判断。
    """

    side: str
    kind: str
    value_parameter_id: uuid.UUID | None = None
    object_parameter_id: uuid.UUID | None = None
    object_property_id: uuid.UUID | None = None
    static_value: Any | None = None


@dataclass(frozen=True)
class NodeRead:
    """读模型：一个节点，children 按 `order_index` 排好。"""

    id: uuid.UUID
    node_kind: str
    logic: str | None
    operator: str | None
    failure_message: str | None
    order_index: int
    left: OperandSpec | None
    right: OperandSpec | None
    children: tuple[NodeRead, ...]


def get_node(session: Session, node_id: uuid.UUID) -> ActionCriteriaNode:
    node = session.get(ActionCriteriaNode, node_id)
    if node is None:
        raise NotFoundError(f"action criteria node {node_id} not found")
    return node


def list_nodes(session: Session, action_type_id: uuid.UUID) -> list[ActionCriteriaNode]:
    """根节点排在最前，其余按同级顺序——树按 parent_id 组装，这个顺序只保证可复现。"""
    return list(
        session.scalars(
            select(ActionCriteriaNode)
            .where(ActionCriteriaNode.action_type_id == action_type_id)
            .order_by(
                ActionCriteriaNode.parent_id.nullsfirst(),
                ActionCriteriaNode.order_index,
                ActionCriteriaNode.id,
            )
        )
    )


def find_root(session: Session, action_type_id: uuid.UUID) -> ActionCriteriaNode | None:
    """条件树的根分组；还没建过树时没有根。"""
    for node in list_nodes(session, action_type_id):
        if node.parent_id is None:
            return node
    return None


def get_operand(session: Session, operand_id: uuid.UUID) -> ActionCriteriaOperand:
    operand = session.get(ActionCriteriaOperand, operand_id)
    if operand is None:
        raise NotFoundError(f"action criteria operand {operand_id} not found")
    return operand


def list_operands(session: Session, node_ids: Sequence[uuid.UUID]) -> list[ActionCriteriaOperand]:
    if not node_ids:
        return []
    return list(
        session.scalars(
            select(ActionCriteriaOperand)
            .where(ActionCriteriaOperand.node_id.in_(list(node_ids)))
            .order_by(ActionCriteriaOperand.node_id, ActionCriteriaOperand.side)
        )
    )


def nodes_by_depth_desc(nodes: Sequence[ActionCriteriaNode]) -> list[ActionCriteriaNode]:
    """深的排前面，同深度保持入参顺序——自引用表按这个顺序删才不会踩到父子外键。"""
    depths = _depths(nodes)
    return sorted(nodes, key=lambda node: -depths[node.id])


def _depths(nodes: Sequence[ActionCriteriaNode]) -> dict[uuid.UUID, int]:
    """每个节点到根的深度。碰到环就地打住，环本身由 `ensure_tree_shape` 报错。"""
    parents = {node.id: node.parent_id for node in nodes}
    depths: dict[uuid.UUID, int] = {}
    for node in nodes:
        chain: list[uuid.UUID] = []
        seen: set[uuid.UUID] = set()
        current: uuid.UUID | None = node.id
        while current is not None and current not in depths and current not in seen:
            seen.add(current)
            chain.append(current)
            current = parents.get(current)
        base = -1 if current is None else depths.get(current, -1)
        for node_id in reversed(chain):
            base += 1
            depths[node_id] = base
    return depths


def operand_spec(operand: ActionCriteriaOperand) -> OperandSpec:
    """把一行操作数收敛成形状（补丁可以照着它改一个字段）。"""
    return OperandSpec(
        side=operand.side,
        kind=operand.kind,
        value_parameter_id=operand.value_parameter_id,
        object_parameter_id=operand.object_parameter_id,
        object_property_id=operand.object_property_id,
        static_value=operand.static_value,
    )


def load_tree(session: Session, action_type_id: uuid.UUID) -> list[NodeRead]:
    """整棵树一次读出来：一次查节点、一次查操作数，在内存里按 parent_id 拼。"""
    nodes = list_nodes(session, action_type_id)
    if not nodes:
        return []
    sides: dict[uuid.UUID, dict[str, OperandSpec]] = {node.id: {} for node in nodes}
    for operand in list_operands(session, [node.id for node in nodes]):
        sides[operand.node_id][operand.side] = operand_spec(operand)

    children: dict[uuid.UUID, list[ActionCriteriaNode]] = {node.id: [] for node in nodes}
    roots: list[ActionCriteriaNode] = []
    for node in nodes:
        if node.parent_id is None:
            roots.append(node)
        else:
            children.setdefault(node.parent_id, []).append(node)

    def build(node: ActionCriteriaNode) -> NodeRead:
        sides_of_node = sides[node.id]
        return NodeRead(
            id=node.id,
            node_kind=node.node_kind,
            logic=node.logic,
            operator=node.operator,
            failure_message=node.failure_message,
            order_index=node.order_index,
            left=sides_of_node.get(LEFT),
            right=sides_of_node.get(RIGHT),
            children=tuple(build(child) for child in children.get(node.id, [])),
        )

    return [build(root) for root in roots]
