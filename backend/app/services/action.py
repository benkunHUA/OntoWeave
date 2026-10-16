"""动作类型与它的参数：读写、状态流转，以及参数的不变量。

动作类型是共享池元素，与对象类型同构；参数是它的子元素，只在这个模块里长出来。
规则与提交校验在 `action_rules`，删除保护在 `action_references`，本模块不掺这两件事。
"""

from __future__ import annotations

import uuid
from collections.abc import Collection
from typing import Any

from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import ActionParameter, ActionType, GraphAction
from app.services import enum_values as enum_values_rules
from app.services import graph, object_type, status, value_types
from app.vocabulary import (
    ACTION_PARAMETER_KINDS,
    ActionParameterKind,
    PropertyDatatype,
)


def create_action_type(
    session: Session,
    *,
    owner_id: uuid.UUID,
    name: str,
    description: str | None = None,
    home_graph_id: uuid.UUID | None = None,
) -> ActionType:
    """建一个草稿动作类型。草稿不写向量，确认时才补，检索因此只认已确认的动作。"""
    action = ActionType(
        owner_id=owner_id,
        name=name,
        description=description,
        status=status.DRAFT,
        home_graph_id=home_graph_id,
    )
    session.add(action)
    session.flush()
    return action


def get_action_type(session: Session, action_type_id: uuid.UUID) -> ActionType:
    action = session.get(ActionType, action_type_id)
    if action is None:
        raise NotFoundError(f"action type {action_type_id} not found")
    return action


def list_action_types(
    session: Session,
    *,
    owner_id: uuid.UUID | None = None,
    query: str | None = None,
    status_value: str | None = None,
    limit: int = 50,
    offset: int = 0,
) -> list[ActionType]:
    """按名称排序分页；加 id 作次级排序键，翻页时顺序稳定。

    `query` 按名称 / 描述做子串匹配——接口层的检索与调试用它，面向智能体的语义检索
    走 `embedding.search_action_types`。
    """
    stmt = select(ActionType)
    if owner_id is not None:
        stmt = stmt.where(ActionType.owner_id == owner_id)
    if query:
        pattern = f"%{query}%"
        stmt = stmt.where(
            or_(ActionType.name.ilike(pattern), ActionType.description.ilike(pattern))
        )
    if status_value is not None:
        stmt = stmt.where(ActionType.status == status_value)
    return list(
        session.scalars(stmt.order_by(ActionType.name, ActionType.id).limit(limit).offset(offset))
    )


def transition_action_type(
    session: Session,
    action_type_id: uuid.UUID,
    target: str,
    *,
    owner_id: uuid.UUID | None = None,
) -> ActionType:
    action = get_action_type(session, action_type_id)
    if owner_id is not None and action.owner_id != owner_id:
        raise NotFoundError(f"action type {action_type_id} not found")
    status.ensure_transition(action.status, target, f"action type {action_type_id}")
    action.status = target
    session.flush()
    return action


def add_action_to_graph(
    session: Session,
    graph_id: uuid.UUID,
    action_type_id: uuid.UUID,
    *,
    owner_id: uuid.UUID | None = None,
) -> None:
    """把动作类型挂进图。动作类型是共享池元素，图成员表只记「这张图用了它」。"""
    graph.get_graph(session, graph_id, owner_id=owner_id)
    get_action_type(session, action_type_id)
    if session.get(GraphAction, (graph_id, action_type_id)) is not None:
        return
    session.add(GraphAction(graph_id=graph_id, action_type_id=action_type_id))
    session.flush()


def list_graph_actions(session: Session, graph_id: uuid.UUID) -> list[uuid.UUID]:
    rows = session.scalars(
        select(GraphAction.action_type_id)
        .where(GraphAction.graph_id == graph_id)
        .order_by(GraphAction.added_at, GraphAction.action_type_id)
    )
    return list(rows)


def get_parameter(session: Session, parameter_id: uuid.UUID) -> ActionParameter:
    parameter = session.get(ActionParameter, parameter_id)
    if parameter is None:
        raise NotFoundError(f"action parameter {parameter_id} not found")
    return parameter


def list_parameters(session: Session, action_type_id: uuid.UUID) -> list[ActionParameter]:
    return list(
        session.scalars(
            select(ActionParameter)
            .where(ActionParameter.action_type_id == action_type_id)
            .order_by(ActionParameter.order_index, ActionParameter.name)
        )
    )


def ensure_slot_free(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    name: str | None = None,
    order_index: int | None = None,
    exclude_ids: Collection[uuid.UUID] = (),
) -> None:
    """同一个动作里，参数名与顺序位各占一个。

    `exclude_ids` 让调用方把「本次已经决定不要的参数」排除掉：删掉一个参数、再建一个
    同名参数是常见操作，不该被还没落地的删除挡住。
    """
    stmt = select(ActionParameter).where(ActionParameter.action_type_id == action_type_id)
    if exclude_ids:
        stmt = stmt.where(ActionParameter.id.not_in(list(exclude_ids)))
    for existing in session.scalars(stmt):
        if name is not None and existing.name == name:
            raise InvariantViolationError(
                f"action type {action_type_id} already has a parameter named {name!r}"
            )
        if order_index is not None and existing.order_index == order_index:
            raise InvariantViolationError(
                f"action type {action_type_id} already has a parameter at position {order_index}"
            )


def next_order_index(session: Session, action_type_id: uuid.UUID) -> int:
    """新参数接在末尾。"""
    indexes = session.scalars(
        select(ActionParameter.order_index).where(ActionParameter.action_type_id == action_type_id)
    )
    return max(indexes, default=-1) + 1


def create_parameter(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    name: str,
    kind: str = ActionParameterKind.VALUE.value,
    description: str | None = None,
    datatype: str | None = None,
    target_object_type_id: uuid.UUID | None = None,
    is_array: bool = False,
    is_required: bool = False,
    default_value: Any | None = None,
    enum_values: list[str] | None = None,
    order_index: int | None = None,
    exclude_ids: Collection[uuid.UUID] = (),
) -> ActionParameter:
    """给动作加一个参数。两组列（值参数 / 对象引用参数）必须配套，不能混着给。"""
    action = get_action_type(session, action_type_id)
    if not name.strip():
        raise InvariantViolationError("action parameter name must not be empty")
    shape = _parameter_shape(
        kind=kind,
        datatype=datatype,
        target_object_type_id=target_object_type_id,
        enum_values=enum_values,
        default_value=default_value,
        is_array=is_array,
        where=f"action parameter {name!r}",
    )
    if shape.target_object_type_id is not None:
        object_type.get_object_type(session, shape.target_object_type_id)
    position = next_order_index(session, action_type_id) if order_index is None else order_index
    ensure_slot_free(
        session,
        action_type_id=action.id,
        name=name.strip(),
        order_index=position,
        exclude_ids=exclude_ids,
    )
    parameter = ActionParameter(
        action_type_id=action.id,
        name=name.strip(),
        description=description,
        kind=shape.kind,
        datatype=shape.datatype,
        target_object_type_id=shape.target_object_type_id,
        is_array=is_array,
        is_required=is_required,
        default_value=shape.default_value,
        enum_values=shape.enum_values,
        order_index=position,
        status=status.DRAFT,
    )
    session.add(parameter)
    session.flush()
    return parameter


def validate_parameter_patch(
    session: Session,
    *,
    parameter_id: uuid.UUID,
    patch: dict,
    exclude_ids: Collection[uuid.UUID] = (),
) -> None:
    """改参数前校验：名字 / 顺序位不撞，两组列改完仍然配套。

    `exclude_ids` 是本改动集里已经标删、还没落地的参数：把「说明」改成刚删掉的「备注」
    这种改名，不该被那行还没真删的旧记录挡住。
    """
    parameter = get_parameter(session, parameter_id)
    where = f"action parameter {parameter.name!r}"
    shape = _parameter_shape(
        kind=patch.get("kind", parameter.kind),
        datatype=patch.get("datatype", parameter.datatype),
        target_object_type_id=patch.get("target_object_type_id", parameter.target_object_type_id),
        enum_values=patch.get("enum_values", parameter.enum_values),
        default_value=patch.get("default_value", parameter.default_value),
        is_array=patch.get("is_array", parameter.is_array),
        where=where,
    )
    if "target_object_type_id" in patch and shape.target_object_type_id is not None:
        object_type.get_object_type(session, shape.target_object_type_id)

    name = patch.get("name")
    if name is not None:
        if not str(name).strip():
            raise InvariantViolationError("action parameter name must not be empty")
        name = str(name).strip()
    ensure_slot_free(
        session,
        action_type_id=parameter.action_type_id,
        name=name,
        order_index=patch.get("order_index"),
        exclude_ids=[parameter.id, *exclude_ids],
    )


class _ParameterShape:
    """校验通过后的四个落库字段：kind 决定谁有值，其余一律为 None。"""

    __slots__ = ("kind", "datatype", "target_object_type_id", "default_value", "enum_values")

    def __init__(self, kind, datatype, target_object_type_id, default_value, enum_values) -> None:
        self.kind = kind
        self.datatype = datatype
        self.target_object_type_id = target_object_type_id
        self.default_value = default_value
        self.enum_values = enum_values


def _parameter_shape(
    *,
    kind: str,
    datatype: str | None,
    target_object_type_id: uuid.UUID | None,
    enum_values: list[str] | None,
    default_value: Any | None,
    is_array: bool,
    where: str,
) -> _ParameterShape:
    if kind not in ACTION_PARAMETER_KINDS:
        raise InvariantViolationError(
            f"{where}: unsupported parameter kind {kind!r}; "
            f"expected one of {list(ACTION_PARAMETER_KINDS)}"
        )

    if kind == ActionParameterKind.OBJECT_REF.value:
        if target_object_type_id is None:
            raise InvariantViolationError(f"{where}: object_ref parameter needs an object type")
        if datatype is not None or enum_values or default_value is not None:
            raise InvariantViolationError(
                f"{where}: object_ref parameter cannot carry datatype, enum_values or default_value"
            )
        return _ParameterShape(kind, None, target_object_type_id, None, None)

    if datatype is None:
        raise InvariantViolationError(f"{where}: value parameter needs a datatype")
    try:
        PropertyDatatype(datatype)
    except ValueError as exc:
        raise InvariantViolationError(f"{where}: unsupported datatype {datatype!r}") from exc
    if target_object_type_id is not None:
        raise InvariantViolationError(f"{where}: value parameter cannot point at an object type")

    stored_enum_values = _enum_values_for(datatype=datatype, values=enum_values, where=where)
    if default_value is not None:
        value_types.ensure_value_matches(
            datatype,
            default_value,
            where=f"{where} default_value",
            is_array=is_array,
            enum_values=stored_enum_values,
        )
    return _ParameterShape(kind, datatype, None, default_value, stored_enum_values)


def _enum_values_for(*, datatype: str, values: list[str] | None, where: str) -> list[str] | None:
    if datatype == PropertyDatatype.ENUM:
        if not values:
            raise InvariantViolationError(f"{where}: enum datatype requires enum_values")
        return enum_values_rules.ensure_valid(values, where=where)
    if values:
        raise InvariantViolationError(
            f"{where}: enum_values is only allowed when datatype is 'enum'"
        )
    return None
