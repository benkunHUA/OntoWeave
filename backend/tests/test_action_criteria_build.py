"""`test_action_criteria.py` 中 build 相关测试。"""

import pytest
from sqlalchemy.orm import Session

from app.services import criteria
from tests.support.action_criteria_fixtures import _Fixture, _replace, build_fixture


@pytest.fixture
def fixture(session):
    return build_fixture(session)


def test_replace_criteria_wraps_conditions_in_one_and_root(session: Session, fixture: _Fixture):
    _replace(
        session,
        fixture,
        [
            {
                "operator": ">",
                "failure_message": "金额需大于 1000",
                "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                "right": {"kind": "static", "static_value": 1000},
            },
            {
                "operator": "is",
                "failure_message": "工单必须为未关闭",
                "left": {
                    "kind": "object_property",
                    "object_parameter_id": fixture.ticket_param.id,
                    "object_property_id": fixture.status_prop.id,
                },
                "right": {"kind": "static", "static_value": "open"},
            },
        ],
    )

    roots = criteria.load_tree(session, fixture.action_type_id)
    assert len(roots) == 1
    root = roots[0]
    assert (root.node_kind, root.logic) == ("group", "and")
    assert [child.operator for child in root.children] == [">", "is"]
    assert root.children[0].left.kind == "parameter"
    assert root.children[0].right.static_value == 1000
    assert root.children[1].left.object_property_id == fixture.status_prop.id
    assert root.children[1].failure_message == "工单必须为未关闭"


def test_replace_criteria_clears_the_old_tree_first(session: Session, fixture: _Fixture):
    _replace(
        session,
        fixture,
        [
            {
                "operator": "is null",
                "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
            }
        ],
    )
    first = criteria.list_nodes(session, fixture.action_type_id)

    _replace(
        session,
        fixture,
        [
            {
                "operator": "is null",
                "left": {"kind": "parameter", "value_parameter_id": fixture.status_param.id},
            }
        ],
    )

    nodes = criteria.list_nodes(session, fixture.action_type_id)
    assert {node.id for node in nodes}.isdisjoint({node.id for node in first})
    assert criteria.list_operands(session, [node.id for node in first]) == []

    _replace(session, fixture, [])
    assert criteria.list_nodes(session, fixture.action_type_id) == []


def test_empty_tree_is_fine(session: Session, fixture: _Fixture):
    criteria.ensure_tree_shape(session, action_type_id=fixture.action_type_id, where="test")
