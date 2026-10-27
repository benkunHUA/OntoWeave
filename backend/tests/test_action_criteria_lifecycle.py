"""`test_action_criteria.py` 中 lifecycle 相关测试。"""

import uuid
import warnings

import pytest
from sqlalchemy.exc import SAWarning
from sqlalchemy.orm import Session

from app.models import ActionCriteriaNode, ActionType, ObjectType
from app.services import action, changeset, criteria, element_edit, graph
from tests.support.action_criteria_fixtures import _Fixture, build_fixture


@pytest.fixture
def fixture(session):
    return build_fixture(session)


def test_nodes_by_depth_desc_puts_children_first(session: Session, fixture: _Fixture):
    root = criteria.create_group(session, action_type_id=fixture.action_type_id)
    middle = criteria.create_group(
        session, action_type_id=fixture.action_type_id, parent_id=root.id
    )
    leaf = criteria.create_condition(
        session, action_type_id=fixture.action_type_id, operator="is null", parent_id=middle.id
    )

    ordered = criteria.nodes_by_depth_desc([root, middle, leaf])

    assert [node.id for node in ordered] == [leaf.id, middle.id, root.id]


def test_confirming_deletes_criteria_nodes_child_first(session: Session, fixture: _Fixture):
    """同一档位里的自引用节点：删除顺序必须是子先父后。"""
    owner = fixture.owner
    g = graph.create_graph(session, owner_id=owner, name="测试图")
    root = criteria.create_group(session, action_type_id=fixture.action_type_id)
    middle = criteria.create_group(
        session, action_type_id=fixture.action_type_id, parent_id=root.id
    )
    leaf = criteria.create_condition(
        session, action_type_id=fixture.action_type_id, operator="is null", parent_id=middle.id
    )

    cs = element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[
            element_edit.ElementDelete("action_criteria_node", root.id),
            element_edit.ElementDelete("action_criteria_node", middle.id),
            element_edit.ElementDelete("action_criteria_node", leaf.id),
        ],
        owner_id=owner,
    )
    with warnings.catch_warnings():
        warnings.simplefilter("error", SAWarning)
        changeset.confirm_changeset(session, cs.id, owner_id=owner)

    for node_id in (root.id, middle.id, leaf.id):
        assert session.get(ActionCriteriaNode, node_id) is None
    assert session.get(ActionType, fixture.action_type_id) is not None


def test_revert_removes_a_freshly_built_action_tree(session: Session, fixture: _Fixture):
    owner = uuid.uuid4()
    g = graph.create_graph(session, owner_id=owner, name="测试图")

    cs = element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[element_edit.ActionTypeCreate(name="新建动作")],
        owner_id=owner,
    )
    action_type_id = changeset.list_ops(session, cs.id)[0].target_id
    parameter = action.create_parameter(
        session, action_type_id=action_type_id, name="金额", datatype="decimal"
    )
    criteria.create_group(session, action_type_id=action_type_id)
    changeset.record_op(
        session,
        changeset_id=cs.id,
        op=changeset.OP_CREATE,
        target_id=parameter.id,
        payload={"kind": element_edit.ACTION_PARAMETER},
    )

    with warnings.catch_warnings():
        warnings.simplefilter("error", SAWarning)
        changeset.reject_changeset(session, cs.id, owner_id=owner)

    assert session.get(ActionType, action_type_id) is None
    assert criteria.list_nodes(session, action_type_id) == []
    assert session.get(ObjectType, fixture.ticket.id) is not None
