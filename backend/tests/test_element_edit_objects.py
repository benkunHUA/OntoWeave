"""`test_element_edit.py` 中 objects 相关测试。"""

import uuid

import pytest

from app.errors import InvalidTransitionError, InvariantViolationError
from app.services import changeset, element_edit, graph, ontology
from tests.support.element_edit_fixtures import _graph


def test_create_object_type_records_op_and_joins_the_graph(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    cs = changeset.create_changeset(session, graph_id=g.id, owner_id=owner)

    obj = element_edit.create_object_type(
        session, changeset_id=cs.id, owner_id=owner, name="客户", description="下订单的一方"
    )

    assert obj.status == "draft"
    assert obj.home_graph_id == g.id
    assert obj.id in graph.list_graph_objects(session, g.id)
    ops = changeset.list_ops(session, cs.id)
    assert [(op.op, op.payload["kind"], op.target_id) for op in ops] == [
        ("create", "object_type", obj.id)
    ]


def test_create_object_type_refuses_a_closed_changeset(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    cs = changeset.create_changeset(session, graph_id=g.id, owner_id=owner)
    changeset.confirm_changeset(session, cs.id, owner_id=owner)

    with pytest.raises(InvalidTransitionError):
        element_edit.create_object_type(session, changeset_id=cs.id, owner_id=owner, name="客户")

    # 半成品由单元事务丢弃：请求级依赖（get_session）与流式运行都会回滚。
    session.rollback()
    assert ontology.list_object_types(session, owner_id=owner) == []


def test_reuse_object_type_joins_the_graph_without_an_op(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    cs = changeset.create_changeset(session, graph_id=g.id, owner_id=owner)
    pool_type = ontology.create_object_type(session, owner_id=owner, name="客户")
    ontology.transition_object_type(session, pool_type.id, "confirmed", owner_id=owner)

    reused = element_edit.reuse_object_type(
        session, changeset_id=cs.id, owner_id=owner, object_type_id=pool_type.id
    )

    assert reused.id == pool_type.id
    assert pool_type.id in graph.list_graph_objects(session, g.id)
    assert changeset.list_ops(session, cs.id) == []


def test_reuse_object_type_rejects_a_draft(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    cs = changeset.create_changeset(session, graph_id=g.id, owner_id=owner)
    draft = element_edit.create_object_type(
        session, changeset_id=cs.id, owner_id=owner, name="客户"
    )

    with pytest.raises(InvariantViolationError):
        element_edit.reuse_object_type(
            session, changeset_id=cs.id, owner_id=owner, object_type_id=draft.id
        )


def test_apply_edits_reuse_refuses_a_draft(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    draft = ontology.create_object_type(session, owner_id=owner, name="还没定稿")

    with pytest.raises(InvariantViolationError):
        element_edit.apply_edits(
            session,
            graph_id=g.id,
            ops=[element_edit.ObjectTypeReuse(object_type_id=draft.id)],
            owner_id=owner,
        )
