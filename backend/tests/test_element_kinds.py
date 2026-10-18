"""元素种类注册表：删除顺序的阶梯，以及同批删除的实际行为。

删除顺序是整批一次定的，规则只有一条「引用方先删」。测试锁的是「谁必须早于谁」，
不是具体档位数字——以后往阶梯里插新档位，这些断言不用改。
"""

import uuid
import warnings

from sqlalchemy.exc import SAWarning
from sqlalchemy.orm import Session

from app.models import ActionType, ObjectType, Property, Relation
from app.services import (
    action,
    action_rules,
    changeset,
    conversation,
    element_edit,
    element_kinds,
    ontology,
)


def _phase(kind: str) -> int:
    return element_kinds.get(kind).delete_phase


def test_children_are_deleted_before_their_parents():
    assert _phase("property") < _phase("object_type")
    assert _phase("relation") < _phase("object_type")


def test_object_type_phases_reserved_for_action_elements_are_free():
    """动作元素（赋值/操作数=0、规则/条件节点=1、参数=2）以后要比本体元素先删。

    本体元素不能占掉这三档，否则动作规则和它的赋值分不开顺序。
    """
    ontology_phases = {_phase(kind) for kind in ("object_type", "property", "relation")}
    assert ontology_phases.isdisjoint({0, 1, 2})


def test_deleting_type_with_its_property_and_relation_in_one_batch(session: Session):
    """一批里同时删对象类型、它的属性和以它做外键端的关系，确认后全部干净消失。

    这里同时压到三档顺序（属性/关系 → 对象类型）和关系外键指向的属性被删的场景：
    外键是 ON DELETE SET NULL，属性先删不会挡住关系，关系先删也不会挡住对象类型。
    """
    owner = uuid.uuid4()
    graph_id = conversation.create_conversation(session, owner_id=owner).resource_id
    trip = ontology.create_object_type(session, owner_id=owner, name="行程")
    spot = ontology.create_object_type(session, owner_id=owner, name="景点")
    spot_code = ontology.create_property(session, owner_type_id=spot.id, name="景点编号")
    rel = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=trip.id,
        target_type_id=spot.id,
        name="包含",
        cardinality="one_to_many",
        foreign_key_property_id=spot_code.id,
    )

    cs = element_edit.apply_edits(
        session,
        graph_id=graph_id,
        ops=[
            element_edit.ElementDelete("object_type", spot.id),
            element_edit.ElementDelete("property", spot_code.id),
            element_edit.ElementDelete("relation", rel.id),
        ],
        owner_id=owner,
    )
    with warnings.catch_warnings():
        warnings.simplefilter("error", SAWarning)
        changeset.confirm_changeset(session, cs.id, owner_id=owner)

    assert session.get(ObjectType, spot.id) is None
    assert session.get(Property, spot_code.id) is None
    assert session.get(Relation, rel.id) is None
    assert session.get(ObjectType, trip.id) is not None


def test_action_type_dependents_cover_every_child(session: Session):
    """动作类型的子元素必须一条条列出来：赋值 / 操作数对参数是 RESTRICT，级联带不动。"""
    owner = uuid.uuid4()
    action_type = action.create_action_type(session, owner_id=owner, name="指派工单")
    ticket = ontology.create_object_type(session, owner_id=owner, name="工单")
    amount = ontology.create_property(
        session, owner_type_id=ticket.id, name="金额", datatype="decimal"
    )
    parameter = action.create_parameter(
        session, action_type_id=action_type.id, name="金额", datatype="decimal"
    )
    rule = action_rules.create_rule(
        session,
        action_type_id=action_type.id,
        rule_kind="create_object",
        target_object_type_id=ticket.id,
    )
    action_rules.create_assignment(
        session,
        rule_id=rule.id,
        target_property_id=amount.id,
        value_source="parameter",
        value_parameter_id=parameter.id,
    )

    dependents = element_kinds.get("action_type").dependents
    assert dependents is not None

    kinds = {kind for kind, _ in dependents(session, action_type.id)}
    assert kinds == {"action_parameter", "action_rule", "action_rule_assignment"}


def test_deleting_an_action_type_takes_its_children_along(session: Session):
    """确认删动作类型：参数、规则、赋值一起走，且不会撞上参数那侧的 RESTRICT 外键。"""
    owner = uuid.uuid4()
    graph_id = conversation.create_conversation(session, owner_id=owner).resource_id
    action_type = action.create_action_type(session, owner_id=owner, name="指派工单")
    ticket = ontology.create_object_type(session, owner_id=owner, name="工单")
    amount = ontology.create_property(
        session, owner_type_id=ticket.id, name="金额", datatype="decimal"
    )
    parameter = action.create_parameter(
        session, action_type_id=action_type.id, name="金额", datatype="decimal"
    )
    rule = action_rules.create_rule(
        session,
        action_type_id=action_type.id,
        rule_kind="create_object",
        target_object_type_id=ticket.id,
    )
    assignment = action_rules.create_assignment(
        session,
        rule_id=rule.id,
        target_property_id=amount.id,
        value_source="parameter",
        value_parameter_id=parameter.id,
    )

    cs = element_edit.apply_edits(
        session,
        graph_id=graph_id,
        ops=[element_edit.ElementDelete("action_type", action_type.id)],
        owner_id=owner,
    )
    with warnings.catch_warnings():
        warnings.simplefilter("error", SAWarning)
        changeset.confirm_changeset(session, cs.id, owner_id=owner)

    assert session.get(ActionType, action_type.id) is None
    assert session.get(type(parameter), parameter.id) is None
    assert session.get(type(rule), rule.id) is None
    assert session.get(type(assignment), assignment.id) is None
    assert session.get(ObjectType, ticket.id) is not None
