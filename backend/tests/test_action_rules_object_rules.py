"""`test_action_rules.py` 中 object_rules 相关测试。"""

import pytest
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.services import action
from app.services.action import rules as action_rules
from tests.support.action_rules_fixtures import _Fixture, _rule, build_fixture


@pytest.fixture
def fixture(session):
    return build_fixture(session)


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


def test_rule_kind_cannot_change(session: Session, fixture: _Fixture):
    rule = _rule(session, fixture, "create_object", target_object_type_id=fixture.ticket.id)

    with pytest.raises(InvariantViolationError, match="cannot change its kind"):
        action_rules.validate_rule_patch(
            session, rule_id=rule.id, patch={"rule_kind": "delete_object"}
        )
