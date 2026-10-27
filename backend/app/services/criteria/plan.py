"""提交校验的整棵替换计划。

界面与工具层提交的是「完整的扁平条件列表」，落库却是逐个元素：值没变的条件原地留着，
不再出现的标记删除，新出现的建出来。这份 diff 收在这里——调用方只管交一份完整列表。

草稿阶段的删除是延后的（标记删除的行还在库里），所以计划要把「已经被标记删除的节点」
排除在外，否则第二次保存会把上一轮已经标删的节点又当成现有条件留下。
"""

from __future__ import annotations

import json
import uuid
from collections import defaultdict, deque
from collections.abc import Collection, Mapping, Sequence
from dataclasses import dataclass
from typing import Any

from sqlalchemy.orm import Session

import app.services.criteria.shape as shape
from app.errors import InvariantViolationError
from app.services.criteria.store import (
    LEFT,
    RIGHT,
    OperandSpec,
    list_nodes,
    list_operands,
    operand_spec,
)


@dataclass(frozen=True)
class ConditionSpec:
    """一条校验条件：运算符 + 两侧操作数（右操作数可以没有，判空就是这样）。"""

    operator: str
    failure_message: str | None
    left: OperandSpec
    right: OperandSpec | None


@dataclass(frozen=True)
class ReplacementPlan:
    """一次整棵替换要做的三件事：删掉的、原地留着的、新建的。"""

    deletions: tuple[uuid.UUID, ...]
    kept: tuple[uuid.UUID, ...]
    additions: tuple[ConditionSpec, ...]


def parse_condition(
    session: Session, *, action_type_id: uuid.UUID, spec: Mapping[str, Any], where: str
) -> ConditionSpec:
    """把请求里的一条条件转成形状，并当场校验（运算符、两侧类型、列是否配套）。"""
    if not isinstance(spec, Mapping):
        raise InvariantViolationError(f"{where}: a condition must be an object")
    operator = spec.get("operator")
    if not isinstance(operator, str):
        raise InvariantViolationError(f"{where}: needs an operator")
    failure_message = spec.get("failure_message")
    if failure_message is not None and not isinstance(failure_message, str):
        raise InvariantViolationError(f"{where}: failure_message must be text")

    left_where = f"{where} left operand"
    left = _operand_spec(spec.get("left"), side=LEFT, where=left_where)
    if left is None:
        raise InvariantViolationError(f"{left_where}: needs an operand")
    right = _operand_spec(spec.get("right"), side=RIGHT, where=f"{where} right operand")
    shape.ensure_operand_shape(
        session, action_type_id=action_type_id, operand=left, where=f"{where} left operand"
    )
    if right is not None:
        shape.ensure_operand_shape(
            session, action_type_id=action_type_id, operand=right, where=f"{where} right operand"
        )
    shape.ensure_condition_sides(
        session,
        action_type_id=action_type_id,
        operator=operator,
        left=left,
        right=right,
        where=where,
    )
    return ConditionSpec(operator=operator, failure_message=failure_message, left=left, right=right)


def replacement_plan(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    conditions: Sequence[Mapping[str, Any]],
    ignore_node_ids: Collection[uuid.UUID] = (),
) -> ReplacementPlan:
    """把「想要的完整条件列表」和库里现有的条件对一下，算出要删、要留、要建的。"""
    specs = tuple(
        parse_condition(
            session,
            action_type_id=action_type_id,
            spec=spec,
            where=f"action criteria condition #{index}",
        )
        for index, spec in enumerate(conditions)
    )
    ignored = set(ignore_node_ids)
    existing = [
        node
        for node in list_nodes(session, action_type_id)
        if node.id not in ignored and node.node_kind == "condition"
    ]
    buckets = _existing_buckets(session, existing)

    kept: list[uuid.UUID] = []
    additions: list[ConditionSpec] = []
    for spec in specs:
        key = _condition_key(spec.operator, spec.failure_message, spec.left, spec.right)
        bucket = buckets.get(key)
        if bucket:
            kept.append(bucket.popleft())
        else:
            additions.append(spec)
    deletions = tuple(node_id for bucket in buckets.values() for node_id in bucket)
    return ReplacementPlan(deletions=deletions, kept=tuple(kept), additions=tuple(additions))


def _operand_spec(raw: Any, *, side: str, where: str) -> OperandSpec | None:
    """把请求里的一个操作数转成形状；缺省返回 None，缺不缺由调用方约束。"""
    if raw is None:
        return None
    if not isinstance(raw, Mapping):
        raise InvariantViolationError(f"{where}: an operand must be an object")
    kind = raw.get("kind")
    if not isinstance(kind, str):
        raise InvariantViolationError(f"{where}: needs a kind")
    return OperandSpec(
        side=side,
        kind=kind,
        value_parameter_id=raw.get("value_parameter_id"),
        object_parameter_id=raw.get("object_parameter_id"),
        object_property_id=raw.get("object_property_id"),
        static_value=raw.get("static_value"),
    )


def _existing_buckets(session: Session, nodes: Sequence[Any]) -> dict[tuple, deque[uuid.UUID]]:
    """现有条件的值指纹分桶。值相同的条件可能不止一条，所以一个指纹一个队列。"""
    buckets: dict[tuple, deque[uuid.UUID]] = defaultdict(deque)
    sides: dict[uuid.UUID, dict[str, OperandSpec]] = defaultdict(dict)
    for operand in list_operands(session, [node.id for node in nodes]):
        sides[operand.node_id][operand.side] = operand_spec(operand)
    for node in nodes:
        own = sides.get(node.id, {})
        key = _condition_key(node.operator, node.failure_message, own.get(LEFT), own.get(RIGHT))
        buckets[key].append(node.id)
    return buckets


def _condition_key(
    operator: str | None,
    failure_message: str | None,
    left: OperandSpec | None,
    right: OperandSpec | None,
) -> tuple:
    return (operator, failure_message, _side_key(left), _side_key(right))


def _side_key(spec: OperandSpec | None) -> tuple | None:
    if spec is None:
        return None
    return (
        spec.kind,
        spec.value_parameter_id,
        spec.object_parameter_id,
        spec.object_property_id,
        _freeze(spec.static_value),
    )


def _freeze(value: Any) -> str | None:
    """静态值可能是对象或列表，压成可比较的字符串再进指纹。"""
    if value is None:
        return None
    return json.dumps(value, sort_keys=True, default=str)
