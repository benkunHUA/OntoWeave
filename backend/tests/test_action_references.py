"""删除保护：动作定义引用着的共享元素删不掉，引用方要说得出口。"""

import uuid

import pytest
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.services import (
    action,
    changeset,
    criteria,
    element_edit,
    graph,
    ontology,
)
from app.services.action import references as action_references
from app.services.action import rules as action_rules


class _Fixture:
    def __init__(self, session: Session, owner: uuid.UUID) -> None:
        self.owner = owner
        self.graph = graph.create_graph(session, owner_id=owner, name="测试图")
        self.changeset = changeset.ensure_open_changeset(
            session, graph_id=self.graph.id, owner_id=owner
        )
        self.action_type = action.create_action_type(session, owner_id=owner, name="指派工单")
        self.ticket = ontology.create_object_type(session, owner_id=owner, name="工单")
        self.employee = ontology.create_object_type(session, owner_id=owner, name="员工")
        self.amount = ontology.create_property(
            session, owner_type_id=self.ticket.id, name="金额", datatype="decimal"
        )
        self.employee_name = ontology.create_property(
            session, owner_type_id=self.employee.id, name="姓名", datatype="text"
        )
        self.ticket_param = action.create_parameter(
            session,
            action_type_id=self.action_type.id,
            name="工单",
            kind="object_ref",
            target_object_type_id=self.ticket.id,
        )
        self.amount_param = action.create_parameter(
            session, action_type_id=self.action_type.id, name="金额", datatype="decimal"
        )
        self.rule = action_rules.create_rule(
            session,
            action_type_id=self.action_type.id,
            rule_kind="modify_object",
            target_object_type_id=self.ticket.id,
            source_parameter_id=self.ticket_param.id,
        )
        self.assignment = action_rules.create_assignment(
            session,
            rule_id=self.rule.id,
            target_property_id=self.amount.id,
            value_source="parameter",
            value_parameter_id=self.amount_param.id,
        )

    def remove(self, session: Session, kind: str, element_id: uuid.UUID) -> None:
        element_edit.remove_element(
            session,
            changeset_id=self.changeset.id,
            element_kind=kind,
            element_id=element_id,
            owner_id=self.owner,
        )


@pytest.fixture
def fixture(session: Session) -> _Fixture:
    return _Fixture(session, uuid.uuid4())


def test_object_type_references_name_every_site(session: Session, fixture: _Fixture):
    references = action_references.find_references(
        session, element_kind="object_type", element_id=fixture.ticket.id
    )

    labels = " | ".join(reference.label for reference in references)
    assert "parameter '工单' (used as its object type)" in labels
    assert "the modify_object rule (used as its object type)" in labels
    assert {reference.action_name for reference in references} == {"指派工单"}


def test_deleting_a_referenced_object_type_is_refused(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError) as error:
        fixture.remove(session, "object_type", fixture.ticket.id)

    message = str(error.value)
    assert "still used by action definitions" in message
    assert "'指派工单'" in message
    assert session.get(type(fixture.ticket), fixture.ticket.id) is not None


def test_property_and_parameter_references_are_refused(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="as the property it assigns"):
        fixture.remove(session, "property", fixture.amount.id)

    with pytest.raises(InvariantViolationError, match="as a value source"):
        fixture.remove(session, "action_parameter", fixture.amount_param.id)

    with pytest.raises(InvariantViolationError, match="as an endpoint parameter"):
        fixture.remove(session, "action_parameter", fixture.ticket_param.id)


def test_relation_references_come_from_link_rules(session: Session, fixture: _Fixture):
    tag = ontology.create_object_type(session, owner_id=fixture.owner, name="标签")
    tag_param = action.create_parameter(
        session,
        action_type_id=fixture.action_type.id,
        name="标签",
        kind="object_ref",
        target_object_type_id=tag.id,
    )
    labels = ontology.create_relation(
        session,
        owner_id=fixture.owner,
        source_type_id=fixture.ticket.id,
        target_type_id=tag.id,
        name="打标签",
        cardinality="many_to_many",
    )
    action_rules.create_rule(
        session,
        action_type_id=fixture.action_type.id,
        rule_kind="create_link",
        target_relation_id=labels.id,
        source_parameter_id=fixture.ticket_param.id,
        target_parameter_id=tag_param.id,
    )

    with pytest.raises(InvariantViolationError, match="as the relation it works on"):
        fixture.remove(session, "relation", labels.id)


def test_criteria_operands_count_as_references(session: Session, fixture: _Fixture):
    criteria.replace_criteria(
        session,
        action_type_id=fixture.action_type.id,
        conditions=[
            {
                "operator": ">",
                "left": {
                    "kind": "object_property",
                    "object_parameter_id": fixture.ticket_param.id,
                    "object_property_id": fixture.amount.id,
                },
                "right": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
            }
        ],
    )

    with pytest.raises(InvariantViolationError, match="as the property it tests"):
        fixture.remove(session, "property", fixture.amount.id)
    with pytest.raises(InvariantViolationError, match="as a criteria operand"):
        fixture.remove(session, "action_parameter", fixture.amount_param.id)


def test_cascades_count_as_references(session: Session, fixture: _Fixture):
    """对象类型一删，它的属性会跟着走，所以属性上的引用也算在它头上。"""
    with pytest.raises(InvariantViolationError, match="as the property it assigns"):
        fixture.remove(session, "object_type", fixture.ticket.id)


def test_elements_without_action_references_are_still_deletable(
    session: Session, fixture: _Fixture
):
    spare = ontology.create_object_type(session, owner_id=fixture.owner, name="无关类型")
    spare_prop = ontology.create_property(
        session, owner_type_id=spare.id, name="无关字段", datatype="text"
    )

    fixture.remove(session, "property", spare_prop.id)
    fixture.remove(session, "object_type", spare.id)

    kinds = [op.payload["kind"] for op in changeset.list_ops(session, fixture.changeset.id)]
    assert kinds.count("property") == 1
    assert kinds.count("object_type") == 1


def test_deleting_the_action_first_frees_the_object_type(session: Session, fixture: _Fixture):
    fixture.remove(session, "action_type", fixture.action_type.id)
    changeset.confirm_changeset(session, fixture.changeset.id, owner_id=fixture.owner)

    assert (
        action_references.find_references(
            session, element_kind="object_type", element_id=fixture.ticket.id
        )
        == []
    )
    fresh = changeset.ensure_open_changeset(
        session, graph_id=fixture.graph.id, owner_id=fixture.owner
    )
    element_edit.remove_element(
        session,
        changeset_id=fresh.id,
        element_kind="object_type",
        element_id=fixture.ticket.id,
        owner_id=fixture.owner,
    )
