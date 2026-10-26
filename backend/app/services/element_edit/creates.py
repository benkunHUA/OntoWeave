"""本体元素的「建草稿 + 登记改动集」实现。

每个函数负责一件元素的诞生：校验归属、落成草稿、挂进图、记一条 create op。它们同时
被界面直改（`apply`）和 agent 工具调用，所以只认关键词参数，不认 HTTP 或工具形状。

草稿不写向量：只有确认后才会在改动集定稿时补上，检索因此只认已确认的元素。
"""

from __future__ import annotations

import uuid
from collections.abc import Mapping, Sequence
from typing import Any

from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import (
    ActionParameter,
    ActionRule,
    ActionRuleAssignment,
    ActionType,
    ObjectType,
    Property,
    Relation,
)
from app.services import action, action_rules, changeset, english_names, graph, ontology, status
from app.services.element_edit.ops import (
    ACTION_PARAMETER,
    ACTION_RULE,
    ACTION_RULE_ASSIGNMENT,
    ACTION_TYPE,
    OBJECT_TYPE,
    PROPERTY,
    RELATION,
)
from app.vocabulary import DEFAULT_CARDINALITY, DEFAULT_DATATYPE, ActionParameterKind


def create_object_type(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    name: str,
    name_en: str | None = None,
    description: str | None = None,
    name_generator: english_names.EnglishNameGenerator | None = None,
) -> ObjectType:
    """新建草稿对象类型，并把它挂进当前图。"""
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    obj = ontology.create_object_type(
        session,
        owner_id=owner_id,
        name=name,
        name_en=name_en,
        description=description,
        home_graph_id=cs.graph_id,
        name_generator=name_generator,
    )
    graph.add_object_to_graph(session, cs.graph_id, obj.id, owner_id=owner_id)
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=changeset.OP_CREATE,
        target_id=obj.id,
        payload={"kind": OBJECT_TYPE},
    )
    return obj


def reuse_object_type(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    object_type_id: uuid.UUID,
) -> ObjectType:
    """把共享池里已确认的对象类型并进当前图。

    只允许复用已确认的类型（confirmed / approved）：draft 还没被用户确认过，
    把它并入新模型等于带进一件没定稿的东西，所以这里直接拒绝。
    复用不改本体（没有新建/修改），只是把类型挂到这张图上，因此不记录改动集操作
    ——它跟着图成员关系走，不属于本轮待确认的本体改动。
    """
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    obj = changeset.load_element(session, OBJECT_TYPE, object_type_id, owner_id=owner_id)
    if obj.status == status.DRAFT:
        raise InvariantViolationError(
            f"object type {object_type_id} is still a draft and cannot be reused"
        )
    graph.add_object_to_graph(session, cs.graph_id, obj.id, owner_id=owner_id)
    return obj


def create_relation(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    source_type_id: uuid.UUID,
    target_type_id: uuid.UUID,
    name: str,
    direction: str = "forward",
    cardinality: str = DEFAULT_CARDINALITY,
    forward_label: str | None = None,
    reverse_label: str | None = None,
    foreign_key_property_id: uuid.UUID | None = None,
) -> Relation:
    """新建草稿关系，并把两端与关系本身挂进当前图。"""
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    # 两端都必须已在共享池里且属于同一 owner，否则等于凭空连到别人的类型上。
    changeset.load_element(session, OBJECT_TYPE, source_type_id, owner_id=owner_id)
    changeset.load_element(session, OBJECT_TYPE, target_type_id, owner_id=owner_id)
    graph.add_object_to_graph(session, cs.graph_id, source_type_id, owner_id=owner_id)
    graph.add_object_to_graph(session, cs.graph_id, target_type_id, owner_id=owner_id)
    rel = ontology.create_relation(
        session,
        owner_id=owner_id,
        source_type_id=source_type_id,
        target_type_id=target_type_id,
        name=name,
        direction=direction,
        cardinality=cardinality,
        forward_label=forward_label,
        reverse_label=reverse_label,
        foreign_key_property_id=foreign_key_property_id,
        home_graph_id=cs.graph_id,
    )
    graph.add_relation_to_graph(session, cs.graph_id, rel.id, owner_id=owner_id)
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=changeset.OP_CREATE,
        target_id=rel.id,
        payload={"kind": RELATION},
    )
    return rel


def create_property(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID | None,
    owner_type_id: uuid.UUID,
    name: str,
    name_en: str | None = None,
    datatype: str = DEFAULT_DATATYPE,
    required: bool = False,
    is_array: bool = False,
    is_primary_key: bool = False,
    is_title_key: bool = False,
    enum_values: list[str] | None = None,
) -> Property:
    """新建草稿字段并记进改动集。先校验对象归属，避免往别人的类型上加字段。"""
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    changeset.load_element(session, OBJECT_TYPE, owner_type_id, owner_id=owner_id)
    prop = ontology.create_property(
        session,
        owner_type_id=owner_type_id,
        name=name,
        name_en=name_en,
        datatype=datatype,
        required=required,
        is_array=is_array,
        is_primary_key=is_primary_key,
        is_title_key=is_title_key,
        enum_values=enum_values,
    )
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=changeset.OP_CREATE,
        target_id=prop.id,
        payload={"kind": PROPERTY},
    )
    return prop


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
