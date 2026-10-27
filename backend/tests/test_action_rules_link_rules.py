"""`test_action_rules.py` 中 link_rules 相关测试。"""

import pytest
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.services.action import rules as action_rules
from tests.support.action_rules_fixtures import _Fixture, _rule, build_fixture


@pytest.fixture
def fixture(session):
    return build_fixture(session)


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
