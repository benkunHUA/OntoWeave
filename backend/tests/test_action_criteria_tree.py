"""`test_action_criteria.py` 中 tree 相关测试。"""

import pytest
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.services import criteria
from tests.support.action_criteria_fixtures import _Fixture, _replace, build_fixture


@pytest.fixture
def fixture(session):
    return build_fixture(session)


def test_ensure_tree_shape_rejects_a_second_root(session: Session, fixture: _Fixture):
    criteria.create_group(session, action_type_id=fixture.action_type_id)
    criteria.create_group(session, action_type_id=fixture.action_type_id)

    with pytest.raises(InvariantViolationError, match="exactly one root"):
        criteria.ensure_tree_shape(session, action_type_id=fixture.action_type_id, where="test")


def test_ensure_tree_shape_rejects_nested_groups(session: Session, fixture: _Fixture):
    root = criteria.create_group(session, action_type_id=fixture.action_type_id)
    criteria.create_group(session, action_type_id=fixture.action_type_id, parent_id=root.id)

    with pytest.raises(InvariantViolationError, match="not a flat condition under the root"):
        criteria.ensure_tree_shape(session, action_type_id=fixture.action_type_id, where="test")


def test_ensure_tree_shape_rejects_a_cycle(session: Session, fixture: _Fixture):
    root = criteria.create_group(session, action_type_id=fixture.action_type_id)
    child = criteria.create_group(session, action_type_id=fixture.action_type_id)
    root.parent_id = child.id
    child.parent_id = root.id
    session.flush()

    with pytest.raises(InvariantViolationError, match="sits on a cycle"):
        criteria.ensure_tree_shape(session, action_type_id=fixture.action_type_id, where="test")


def test_node_patch_refuses_kind_changes(session: Session, fixture: _Fixture):
    root = criteria.create_group(session, action_type_id=fixture.action_type_id)

    with pytest.raises(InvariantViolationError, match="cannot change its kind"):
        criteria.validate_node_patch(session, node_id=root.id, patch={"node_kind": "condition"})
    with pytest.raises(InvariantViolationError, match="cannot be its own parent"):
        criteria.validate_node_patch(session, node_id=root.id, patch={"parent_id": root.id})


def test_operand_patch_is_checked_against_the_condition(session: Session, fixture: _Fixture):
    _replace(
        session,
        fixture,
        [
            {
                "operator": ">",
                "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                "right": {"kind": "static", "static_value": 1000},
            }
        ],
    )
    node = criteria.list_nodes(session, fixture.action_type_id)[1]
    right = [item for item in criteria.list_operands(session, [node.id]) if item.side == "right"][0]

    with pytest.raises(InvariantViolationError, match="does not fit datatype"):
        criteria.validate_operand_patch(
            session, operand_id=right.id, patch={"static_value": "很多"}
        )
    criteria.validate_operand_patch(session, operand_id=right.id, patch={"static_value": 2000})
