"""条件树的建构：节点、操作数，以及整棵替换。

一次只碰一棵树：先把旧树按深度倒序清掉，再按扁平条件列表重建，内部包一个 `and` 根。
入参形状就是界面与工具层的形状——每条条件是 `{operator, failure_message, left, right}`，
操作数形如 `{kind: 'parameter', value_parameter_id: ...}`。放宽成嵌套树时只改这里。

树的形状约束在 `tree`，单条条件的形状在 `shape`。
"""

from __future__ import annotations

import uuid
from collections.abc import Mapping, Sequence
from typing import Any

from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import ActionCriteriaNode, ActionCriteriaOperand
from app.services import action, status
from app.services.criteria import shape, store, tree
from app.vocabulary import (
    ACTION_CRITERIA_NODE_KINDS,
    ACTION_CRITERIA_OPERATORS,
    ActionCriteriaLogic,
    ActionCriteriaNodeKind,
)

GROUP = ActionCriteriaNodeKind.GROUP.value
CONDITION = ActionCriteriaNodeKind.CONDITION.value
DEFAULT_LOGIC = ActionCriteriaLogic.AND.value


def create_group(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    logic: str = DEFAULT_LOGIC,
    parent_id: uuid.UUID | None = None,
    order_index: int | None = None,
) -> ActionCriteriaNode:
    if logic != DEFAULT_LOGIC:
        raise InvariantViolationError(
            f"action criteria group: unsupported logic {logic!r}; {tree.V1_SHAPE_NOTE}"
        )
    return _create_node(
        session,
        action_type_id=action_type_id,
        node_kind=GROUP,
        logic=logic,
        operator=None,
        failure_message=None,
        parent_id=parent_id,
        order_index=order_index,
    )


def create_condition(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    operator: str,
    failure_message: str | None = None,
    parent_id: uuid.UUID | None = None,
    order_index: int | None = None,
) -> ActionCriteriaNode:
    if operator not in ACTION_CRITERIA_OPERATORS:
        raise InvariantViolationError(
            f"action criteria condition: unsupported operator {operator!r}; "
            f"expected one of {list(ACTION_CRITERIA_OPERATORS)}"
        )
    return _create_node(
        session,
        action_type_id=action_type_id,
        node_kind=CONDITION,
        logic=None,
        operator=operator,
        failure_message=failure_message,
        parent_id=parent_id,
        order_index=order_index,
    )


def _create_node(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    node_kind: str,
    logic: str | None,
    operator: str | None,
    failure_message: str | None,
    parent_id: uuid.UUID | None,
    order_index: int | None,
) -> ActionCriteriaNode:
    action.get_action_type(session, action_type_id)
    if node_kind not in ACTION_CRITERIA_NODE_KINDS:
        raise InvariantViolationError(f"unsupported criteria node kind {node_kind!r}")
    if parent_id is not None:
        parent = store.get_node(session, parent_id)
        if parent.action_type_id != action_type_id:
            raise InvariantViolationError("criteria node parent belongs to another action")
    if order_index is None:
        position = _next_child_index(session, action_type_id, parent_id)
    else:
        position = order_index
    node = ActionCriteriaNode(
        action_type_id=action_type_id,
        parent_id=parent_id,
        order_index=position,
        node_kind=node_kind,
        logic=logic,
        operator=operator,
        failure_message=failure_message,
        status=status.DRAFT,
    )
    session.add(node)
    session.flush()
    return node


def _next_child_index(
    session: Session, action_type_id: uuid.UUID, parent_id: uuid.UUID | None
) -> int:
    siblings = [
        node.order_index
        for node in store.list_nodes(session, action_type_id)
        if node.parent_id == parent_id
    ]
    return max(siblings, default=-1) + 1


def create_operand(
    session: Session,
    *,
    node_id: uuid.UUID,
    side: str,
    kind: str,
    value_parameter_id: uuid.UUID | None = None,
    object_parameter_id: uuid.UUID | None = None,
    object_property_id: uuid.UUID | None = None,
    static_value: Any | None = None,
) -> Any:
    node = store.get_node(session, node_id)
    spec = store.OperandSpec(
        side=side,
        kind=kind,
        value_parameter_id=value_parameter_id,
        object_parameter_id=object_parameter_id,
        object_property_id=object_property_id,
        static_value=static_value,
    )
    shape.ensure_operand_shape(
        session,
        action_type_id=node.action_type_id,
        operand=spec,
        where=f"action criteria {side} operand",
    )
    operand = ActionCriteriaOperand(
        node_id=node.id,
        side=side,
        kind=kind,
        value_parameter_id=value_parameter_id,
        object_parameter_id=object_parameter_id,
        object_property_id=object_property_id,
        static_value=static_value,
        status=status.DRAFT,
    )
    session.add(operand)
    session.flush()
    return operand


def replace_criteria(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    conditions: Sequence[Mapping[str, Any]],
) -> list[ActionCriteriaNode]:
    """整棵替换：清掉旧树，按扁平条件列表重建，内部包一个 `and` 根。

    入参形状就是界面与工具层的形状——每条条件是 `{operator, failure_message, left, right}`，
    操作数形如 `{kind: 'parameter', value_parameter_id: ...}`。放宽成嵌套树时只改这里。
    """
    action.get_action_type(session, action_type_id)
    # 逐个删、逐个 flush：一批删的话 ORM 会把同表的 DELETE 打包成 executemany，
    # 父行先落地就会把子行级联带走，剩下的删除变成 0 匹配（SAWarning）。
    for node in store.nodes_by_depth_desc(store.list_nodes(session, action_type_id)):
        session.delete(node)
        session.flush()
    if not conditions:
        return []

    root = create_group(session, action_type_id=action_type_id, order_index=0)
    created = [root]
    for index, spec in enumerate(conditions):
        where = f"action criteria condition #{index}"
        operator = spec.get("operator")
        if not isinstance(operator, str):
            raise InvariantViolationError(f"{where}: needs an operator")
        node = create_condition(
            session,
            action_type_id=action_type_id,
            operator=operator,
            failure_message=spec.get("failure_message"),
            parent_id=root.id,
            order_index=index,
        )
        created.append(node)
        left = spec.get("left")
        if left is None:
            raise InvariantViolationError(f"{where}: needs a left operand")
        _create_operand(session, node=node, side=store.LEFT, spec=left, where=where)
        right = spec.get("right")
        if right is not None:
            _create_operand(session, node=node, side=store.RIGHT, spec=right, where=where)
        shape.ensure_condition_shape(session, node=node, where=where)

    tree.ensure_tree_shape(session, action_type_id=action_type_id, where="action criteria")
    return created


def _create_operand(
    session: Session, *, node: ActionCriteriaNode, side: str, spec: Any, where: str
) -> Any:
    if not isinstance(spec, Mapping):
        raise InvariantViolationError(f"{where}: {side} operand must be an object")
    kind = spec.get("kind")
    if not isinstance(kind, str):
        raise InvariantViolationError(f"{where}: {side} operand needs a kind")
    return create_operand(
        session,
        node_id=node.id,
        side=side,
        kind=kind,
        value_parameter_id=spec.get("value_parameter_id"),
        object_parameter_id=spec.get("object_parameter_id"),
        object_property_id=spec.get("object_property_id"),
        static_value=spec.get("static_value"),
    )
