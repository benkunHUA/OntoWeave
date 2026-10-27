"""动作类型、参数、规则与字段赋值的草稿创建。"""

from __future__ import annotations

import uuid
from collections.abc import Mapping, Sequence
from typing import Any

from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import ActionParameter, ActionRule, ActionRuleAssignment, ActionType
from app.services import action, changeset, status
from app.services.action import rules as action_rules
from app.services.element_edit.ops import (
    ACTION_PARAMETER,
    ACTION_RULE,
    ACTION_RULE_ASSIGNMENT,
    ACTION_TYPE,
)
from app.vocabulary import ActionParameterKind


def create_action_type(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    name: str,
    description: str | None = None,
) -> ActionType:
    """新建草稿动作类型，并把它挂进当前图。"""
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    action_type = action.create_action_type(
        session, owner_id=owner_id, name=name, description=description, home_graph_id=cs.graph_id
    )
    action.add_action_to_graph(session, cs.graph_id, action_type.id, owner_id=owner_id)
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=changeset.OP_CREATE,
        target_id=action_type.id,
        payload={"kind": ACTION_TYPE},
    )
    return action_type


def reuse_action_type(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    action_type_id: uuid.UUID,
) -> ActionType:
    """把共享池里已确认的动作类型并进当前图。

    和复用对象类型同一条规矩：只允许已确认的（draft 还没被用户确认过），而且
    复用不改本体，只是把动作挂到这张图上，因此不记改动集操作。
    """
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    action_type = changeset.load_element(session, ACTION_TYPE, action_type_id, owner_id=owner_id)
    if action_type.status == status.DRAFT:
        raise InvariantViolationError(
            f"action type {action_type_id} is still a draft and cannot be reused"
        )
    action.add_action_to_graph(session, cs.graph_id, action_type.id, owner_id=owner_id)
    return action_type


def create_action_parameter(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
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
) -> ActionParameter:
    """给动作类型加一个草稿参数。先校验动作归属，别往别人的动作上挂参数。"""
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    changeset.load_element(session, ACTION_TYPE, action_type_id, owner_id=owner_id)
    parameter = action.create_parameter(
        session,
        action_type_id=action_type_id,
        name=name,
        kind=kind,
        description=description,
        datatype=datatype,
        target_object_type_id=target_object_type_id,
        is_array=is_array,
        is_required=is_required,
        default_value=default_value,
        enum_values=enum_values,
        # 本改动集里已标删的参数不再占名字：删掉「备注」再建一个同名参数是常规操作。
        exclude_ids=changeset.pending_delete_ids(session, changeset_id, ACTION_PARAMETER),
    )
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=changeset.OP_CREATE,
        target_id=parameter.id,
        payload={"kind": ACTION_PARAMETER},
    )
    return parameter


def create_action_rule_assignment(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    rule_id: uuid.UUID,
    target_property_id: uuid.UUID,
    value_source: str,
    value_parameter_id: uuid.UUID | None = None,
    object_parameter_id: uuid.UUID | None = None,
    object_property_id: uuid.UUID | None = None,
    static_value: Any | None = None,
) -> ActionRuleAssignment:
    """给已存在的规则补一条字段赋值。归属顺着规则往上的动作类型校验。"""
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    changeset.load_element(session, ACTION_RULE, rule_id, owner_id=owner_id)
    assignment = action_rules.create_assignment(
        session,
        rule_id=rule_id,
        target_property_id=target_property_id,
        value_source=value_source,
        value_parameter_id=value_parameter_id,
        object_parameter_id=object_parameter_id,
        object_property_id=object_property_id,
        static_value=static_value,
        # 已标删的赋值不再占着属性：删掉一条再补一条同属性的该放行。
        exclude_ids=changeset.pending_delete_ids(session, changeset_id, ACTION_RULE_ASSIGNMENT),
    )
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=changeset.OP_CREATE,
        target_id=assignment.id,
        payload={"kind": ACTION_RULE_ASSIGNMENT},
    )
    return assignment


def create_action_rule(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    action_type_id: uuid.UUID,
    rule_kind: str,
    target_object_type_id: uuid.UUID | None = None,
    target_relation_id: uuid.UUID | None = None,
    source_parameter_id: uuid.UUID | None = None,
    target_parameter_id: uuid.UUID | None = None,
    assignments: Sequence[Mapping[str, Any]] = (),
) -> ActionRule:
    """给动作类型加一条草稿规则，内联的字段赋值各记一条 create op。

    赋值是规则的子元素，撤销时要逐条还回去，所以不能只记规则那一条——否则「撤销」
    会留下半条规则。
    """
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    changeset.load_element(session, ACTION_TYPE, action_type_id, owner_id=owner_id)
    rule = action_rules.create_rule(
        session,
        action_type_id=action_type_id,
        rule_kind=rule_kind,
        target_object_type_id=target_object_type_id,
        target_relation_id=target_relation_id,
        source_parameter_id=source_parameter_id,
        target_parameter_id=target_parameter_id,
        assignments=assignments,
    )
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=changeset.OP_CREATE,
        target_id=rule.id,
        payload={"kind": ACTION_RULE},
    )
    for assignment in action_rules.list_assignments(session, rule.id):
        changeset.record_op(
            session,
            changeset_id=changeset_id,
            op=changeset.OP_CREATE,
            target_id=assignment.id,
            payload={"kind": ACTION_RULE_ASSIGNMENT},
        )
    return rule
