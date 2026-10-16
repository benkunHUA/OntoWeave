"""规则的列组合与字段赋值：按 rule_kind / 值来源分派的那套判断。"""

import uuid

import pytest
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.services import action, action_rules, ontology


class _Fixture:
    """一小组够用的本体：工单（金额 / 状态）、员工、标签，以及两条关系。"""

    def __init__(self, session: Session, owner: uuid.UUID) -> None:
        self.action_type = action.create_action_type(session, owner_id=owner, name="指派工单")
        self.ticket = ontology.create_object_type(session, owner_id=owner, name="工单")
        self.employee = ontology.create_object_type(session, owner_id=owner, name="员工")
        self.tag = ontology.create_object_type(session, owner_id=owner, name="标签")
        self.amount = ontology.create_property(
            session, owner_type_id=self.ticket.id, name="金额", datatype="decimal"
        )
        self.status_prop = ontology.create_property(
            session,
            owner_type_id=self.ticket.id,
            name="状态",
            datatype="enum",
            enum_values=["open", "closed"],
        )
        self.channel = ontology.create_property(
            session,
            owner_type_id=self.ticket.id,
            name="渠道",
            datatype="enum",
            enum_values=["电话", "在线"],
        )
        self.employee_name = ontology.create_property(
            session, owner_type_id=self.employee.id, name="姓名", datatype="text"
        )
        self.ticket_param = self._object_parameter(session, "工单", self.ticket.id)
        self.employee_param = self._object_parameter(session, "员工", self.employee.id)
        self.amount_param = action.create_parameter(
            session, action_type_id=self.action_type.id, name="金额", datatype="decimal"
        )
        self.status_param = action.create_parameter(
            session,
            action_type_id=self.action_type.id,
            name="状态",
            datatype="enum",
            enum_values=["open", "closed"],
        )
        self.labels = ontology.create_relation(
            session,
            owner_id=owner,
            source_type_id=self.ticket.id,
            target_type_id=self.tag.id,
            name="打标签",
            cardinality="many_to_many",
        )
        self.owns = ontology.create_relation(
            session,
            owner_id=owner,
            source_type_id=self.employee.id,
            target_type_id=self.ticket.id,
            name="负责",
            cardinality="one_to_many",
        )

    def _object_parameter(self, session: Session, name: str, object_type_id: uuid.UUID):
        return action.create_parameter(
            session,
            action_type_id=self.action_type.id,
            name=name,
            kind="object_ref",
            target_object_type_id=object_type_id,
        )


@pytest.fixture
def fixture(session: Session) -> _Fixture:
    return _Fixture(session, uuid.uuid4())


def _rule(session: Session, fixture: _Fixture, rule_kind: str, **kwargs):
    return action_rules.create_rule(
        session, action_type_id=fixture.action_type.id, rule_kind=rule_kind, **kwargs
    )


def test_modify_object_needs_an_object_parameter_of_that_type(session: Session, fixture: _Fixture):
    rule = _rule(
        session,
        fixture,
        "modify_object",
        target_object_type_id=fixture.ticket.id,
        source_parameter_id=fixture.ticket_param.id,
    )
    assert rule.source_parameter_id == fixture.ticket_param.id

    with pytest.raises(InvariantViolationError, match="needs the object parameter"):
        _rule(session, fixture, "modify_object", target_object_type_id=fixture.ticket.id)
    with pytest.raises(InvariantViolationError, match="must be an object reference parameter"):
        _rule(
            session,
            fixture,
            "modify_object",
            target_object_type_id=fixture.ticket.id,
            source_parameter_id=fixture.amount_param.id,
        )
    with pytest.raises(InvariantViolationError, match="points at another object type"):
        _rule(
            session,
            fixture,
            "modify_object",
            target_object_type_id=fixture.ticket.id,
            source_parameter_id=fixture.employee_param.id,
        )


def test_object_rule_refuses_array_parameters_for_now(session: Session, fixture: _Fixture):
    array_param = action.create_parameter(
        session,
        action_type_id=fixture.action_type.id,
        name="工单列表",
        kind="object_ref",
        target_object_type_id=fixture.ticket.id,
        is_array=True,
    )

    with pytest.raises(InvariantViolationError, match="array parameter cannot stand"):
        _rule(
            session,
            fixture,
            "delete_object",
            target_object_type_id=fixture.ticket.id,
            source_parameter_id=array_param.id,
        )


def test_create_object_takes_no_source_parameter(session: Session, fixture: _Fixture):
    rule = _rule(session, fixture, "create_object", target_object_type_id=fixture.ticket.id)
    assert rule.target_object_type_id == fixture.ticket.id

    with pytest.raises(InvariantViolationError, match="takes no source parameter"):
        _rule(
            session,
            fixture,
            "create_object",
            target_object_type_id=fixture.ticket.id,
            source_parameter_id=fixture.ticket_param.id,
        )


def test_object_rule_columns_are_exclusive(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="must not carry a relation"):
        _rule(
            session,
            fixture,
            "modify_object",
            target_object_type_id=fixture.ticket.id,
            source_parameter_id=fixture.ticket_param.id,
            target_parameter_id=fixture.employee_param.id,
        )
    with pytest.raises(InvariantViolationError, match="needs the object type"):
        _rule(session, fixture, "create_object")


def test_link_rule_needs_a_many_to_many_relation(session: Session, fixture: _Fixture):
    rule = _rule(
        session,
        fixture,
        "create_link",
        target_relation_id=fixture.labels.id,
        source_parameter_id=fixture.ticket_param.id,
        target_parameter_id=fixture._object_parameter(session, "标签", fixture.tag.id).id,
    )
    assert rule.target_relation_id == fixture.labels.id

    with pytest.raises(InvariantViolationError, match="only many-to-many relations use link rules"):
        _rule(
            session,
            fixture,
            "create_link",
            target_relation_id=fixture.owns.id,
            source_parameter_id=fixture.employee_param.id,
            target_parameter_id=fixture.ticket_param.id,
        )


def test_link_rule_needs_both_endpoints_and_they_must_differ(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="needs both endpoint parameters"):
        _rule(
            session,
            fixture,
            "delete_link",
            target_relation_id=fixture.labels.id,
            source_parameter_id=fixture.ticket_param.id,
        )
    with pytest.raises(InvariantViolationError, match="cannot be the same parameter"):
        _rule(
            session,
            fixture,
            "delete_link",
            target_relation_id=fixture.labels.id,
            source_parameter_id=fixture.ticket_param.id,
            target_parameter_id=fixture.ticket_param.id,
        )
    with pytest.raises(InvariantViolationError, match="target parameter points at another"):
        _rule(
            session,
            fixture,
            "delete_link",
            target_relation_id=fixture.labels.id,
            source_parameter_id=fixture.ticket_param.id,
            target_parameter_id=fixture.employee_param.id,
        )


def test_link_rule_must_not_carry_an_object_type(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="link rule must not carry an object type"):
        _rule(
            session,
            fixture,
            "create_link",
            target_object_type_id=fixture.ticket.id,
            target_relation_id=fixture.labels.id,
            source_parameter_id=fixture.ticket_param.id,
            target_parameter_id=fixture.employee_param.id,
        )


def test_rule_kind_cannot_change(session: Session, fixture: _Fixture):
    rule = _rule(session, fixture, "create_object", target_object_type_id=fixture.ticket.id)

    with pytest.raises(InvariantViolationError, match="cannot change its kind"):
        action_rules.validate_rule_patch(
            session, rule_id=rule.id, patch={"rule_kind": "delete_object"}
        )


def test_assignment_needs_the_rule_object_type(session: Session, fixture: _Fixture):
    rule = _rule(session, fixture, "create_object", target_object_type_id=fixture.ticket.id)
    action_rules.create_assignment(
        session,
        rule_id=rule.id,
        target_property_id=fixture.amount.id,
        value_source="parameter",
        value_parameter_id=fixture.amount_param.id,
    )

    with pytest.raises(InvariantViolationError, match="already assigns property"):
        action_rules.create_assignment(
            session,
            rule_id=rule.id,
            target_property_id=fixture.amount.id,
            value_source="static",
            static_value=1,
        )

    other_rule = _rule(session, fixture, "create_object", target_object_type_id=fixture.employee.id)
    with pytest.raises(InvariantViolationError, match="does not belong to the rule's object type"):
        action_rules.create_assignment(
            session,
            rule_id=other_rule.id,
            target_property_id=fixture.amount.id,
            value_source="static",
            static_value=1,
        )


def test_delete_object_takes_no_assignments(session: Session, fixture: _Fixture):
    rule = _rule(
        session,
        fixture,
        "delete_object",
        target_object_type_id=fixture.ticket.id,
        source_parameter_id=fixture.ticket_param.id,
    )

    with pytest.raises(InvariantViolationError, match="does not take field assignments"):
        action_rules.create_assignment(
            session,
            rule_id=rule.id,
            target_property_id=fixture.amount.id,
            value_source="static",
            static_value=1,
        )


def test_value_sources_map_to_exactly_one_column(session: Session, fixture: _Fixture):
    rule = _rule(session, fixture, "create_object", target_object_type_id=fixture.ticket.id)

    with pytest.raises(InvariantViolationError, match="needs the value parameter"):
        action_rules.create_assignment(
            session, rule_id=rule.id, target_property_id=fixture.amount.id, value_source="parameter"
        )
    with pytest.raises(InvariantViolationError, match="must not carry object columns"):
        action_rules.create_assignment(
            session,
            rule_id=rule.id,
            target_property_id=fixture.amount.id,
            value_source="parameter",
            value_parameter_id=fixture.amount_param.id,
            object_parameter_id=fixture.ticket_param.id,
        )
    with pytest.raises(InvariantViolationError, match="static source needs the value itself"):
        action_rules.create_assignment(
            session, rule_id=rule.id, target_property_id=fixture.amount.id, value_source="static"
        )
    with pytest.raises(InvariantViolationError, match="unsupported value source"):
        action_rules.create_assignment(
            session, rule_id=rule.id, target_property_id=fixture.amount.id, value_source="magic"
        )


def test_assignment_types_must_line_up(session: Session, fixture: _Fixture):
    rule = _rule(session, fixture, "create_object", target_object_type_id=fixture.ticket.id)

    with pytest.raises(InvariantViolationError, match="does not fit property"):
        action_rules.create_assignment(
            session,
            rule_id=rule.id,
            target_property_id=fixture.status_prop.id,
            value_source="parameter",
            value_parameter_id=fixture.amount_param.id,
        )
    with pytest.raises(InvariantViolationError, match="does not fit datatype"):
        action_rules.create_assignment(
            session,
            rule_id=rule.id,
            target_property_id=fixture.amount.id,
            value_source="static",
            static_value="很多",
        )


def test_enum_assignments_must_share_the_candidates(session: Session, fixture: _Fixture):
    rule = _rule(
        session,
        fixture,
        "modify_object",
        target_object_type_id=fixture.ticket.id,
        source_parameter_id=fixture.ticket_param.id,
    )
    action_rules.create_assignment(
        session,
        rule_id=rule.id,
        target_property_id=fixture.status_prop.id,
        value_source="parameter",
        value_parameter_id=fixture.status_param.id,
    )

    with pytest.raises(InvariantViolationError, match="enum values must match property"):
        action_rules.create_assignment(
            session,
            rule_id=rule.id,
            target_property_id=fixture.channel.id,
            value_source="parameter",
            value_parameter_id=fixture.status_param.id,
        )


def test_object_property_source_reads_from_the_object_parameter(
    session: Session, fixture: _Fixture
):
    rule = _rule(
        session,
        fixture,
        "modify_object",
        target_object_type_id=fixture.ticket.id,
        source_parameter_id=fixture.ticket_param.id,
    )
    assignment = action_rules.create_assignment(
        session,
        rule_id=rule.id,
        target_property_id=fixture.amount.id,
        value_source="object_property",
        object_parameter_id=fixture.ticket_param.id,
        object_property_id=fixture.amount.id,
    )
    assert assignment.object_property_id == fixture.amount.id

    with pytest.raises(InvariantViolationError, match="must not carry a value parameter"):
        action_rules.create_assignment(
            session,
            rule_id=rule.id,
            target_property_id=fixture.status_prop.id,
            value_source="object_property",
            object_parameter_id=fixture.ticket_param.id,
            object_property_id=fixture.status_prop.id,
            static_value="open",
        )
    with pytest.raises(InvariantViolationError, match="source parameter must be an object ref"):
        action_rules.create_assignment(
            session,
            rule_id=rule.id,
            target_property_id=fixture.status_prop.id,
            value_source="object_property",
            object_parameter_id=fixture.amount_param.id,
            object_property_id=fixture.status_prop.id,
        )


def test_object_property_source_must_live_on_the_parameter_type(
    session: Session, fixture: _Fixture
):
    rule = _rule(
        session,
        fixture,
        "modify_object",
        target_object_type_id=fixture.ticket.id,
        source_parameter_id=fixture.ticket_param.id,
    )

    with pytest.raises(InvariantViolationError, match="does not belong to the parameter's type"):
        action_rules.create_assignment(
            session,
            rule_id=rule.id,
            target_property_id=fixture.amount.id,
            value_source="object_property",
            object_parameter_id=fixture.ticket_param.id,
            object_property_id=fixture.employee_name.id,
        )


def test_assignment_parameter_must_belong_to_the_same_action(session: Session, fixture: _Fixture):
    other_action = action.create_action_type(session, owner_id=uuid.uuid4(), name="别的动作")
    foreign_parameter = action.create_parameter(
        session,
        action_type_id=other_action.id,
        name="金额",
        datatype="decimal",
    )
    rule = _rule(session, fixture, "create_object", target_object_type_id=fixture.ticket.id)

    with pytest.raises(InvariantViolationError, match="belongs to another action"):
        action_rules.create_assignment(
            session,
            rule_id=rule.id,
            target_property_id=fixture.amount.id,
            value_source="parameter",
            value_parameter_id=foreign_parameter.id,
        )
