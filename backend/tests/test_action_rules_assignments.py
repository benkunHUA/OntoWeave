"""`test_action_rules.py` 中 assignments 相关测试。"""

import uuid

import pytest
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.services import action
from app.services.action import rules as action_rules
from tests.support.action_rules_fixtures import _Fixture, _rule, build_fixture


@pytest.fixture
def fixture(session):
    return build_fixture(session)


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
