"""`test_action_criteria.py` 中 shape 相关测试。"""

import pytest
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from tests.support.action_criteria_fixtures import _Fixture, _replace, build_fixture


@pytest.fixture
def fixture(session):
    return build_fixture(session)


def test_unary_operators_take_no_right_operand(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="takes no right operand"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is null",
                    "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                    "right": {"kind": "static", "static_value": 1},
                }
            ],
        )


def test_binary_operators_need_a_right_operand(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="needs a right operand"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is",
                    "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                }
            ],
        )


def test_a_condition_needs_a_left_operand(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="needs a left operand"):
        _replace(session, fixture, [{"operator": "is null"}])


def test_operator_matrix_follows_the_left_datatype(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="does not apply to datatype"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": ">",
                    "left": {"kind": "parameter", "value_parameter_id": fixture.status_param.id},
                    "right": {"kind": "static", "static_value": "open"},
                }
            ],
        )

    _replace(
        session,
        fixture,
        [
            {
                "operator": ">=",
                "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                "right": {"kind": "static", "static_value": 0},
            }
        ],
    )


def test_static_values_and_enum_sides_are_type_checked(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="does not fit datatype"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": ">",
                    "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                    "right": {"kind": "static", "static_value": "很多"},
                }
            ],
        )

    with pytest.raises(InvariantViolationError, match="must use the same enum values"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is",
                    "left": {"kind": "parameter", "value_parameter_id": fixture.status_param.id},
                    "right": {
                        "kind": "parameter",
                        "value_parameter_id": fixture.other_status_param.id,
                    },
                }
            ],
        )


def test_the_left_side_must_name_a_value(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="left operand must be a parameter"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is",
                    "left": {"kind": "static", "static_value": 1},
                    "right": {"kind": "static", "static_value": 1},
                }
            ],
        )


def test_operand_kinds_map_to_their_own_columns(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="must not carry other columns"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is",
                    "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                    "right": {
                        "kind": "parameter",
                        "value_parameter_id": fixture.amount_param.id,
                        "static_value": 1,
                    },
                }
            ],
        )

    with pytest.raises(InvariantViolationError, match="use 'is null' to test emptiness"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is",
                    "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                    "right": {"kind": "static"},
                }
            ],
        )

    with pytest.raises(InvariantViolationError, match="unsupported operand kind"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is null",
                    "left": {"kind": "magic", "static_value": 1},
                }
            ],
        )


def test_object_property_operands_must_live_on_the_parameter_type(
    session: Session, fixture: _Fixture
):
    message = "does not belong to the parameter's object type"
    with pytest.raises(InvariantViolationError, match=message):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is",
                    "left": {
                        "kind": "object_property",
                        "object_parameter_id": fixture.ticket_param.id,
                        "object_property_id": fixture.employee_name.id,
                    },
                    "right": {"kind": "static", "static_value": "张三"},
                }
            ],
        )
