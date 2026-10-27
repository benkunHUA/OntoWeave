"""`test_element_edit.py` 中 relations 相关测试。"""

import uuid

import pytest

from app.errors import InvariantViolationError, NotFoundError
from app.models import Relation
from app.services import changeset, element_edit, graph
from tests.support.element_edit_fixtures import _graph, _relation


def test_update_relation_fields(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    rel = _relation(session, owner)

    element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[
            element_edit.ElementUpdate(
                "relation",
                rel.id,
                {"cardinality": "one_to_many", "forward_label": "订单", "reverse_label": "客户"},
            )
        ],
        owner_id=owner,
    )

    assert rel.cardinality == "one_to_many"
    assert rel.forward_label == "订单"
    assert rel.reverse_label == "客户"


def test_update_relation_rejects_unknown_cardinality(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    rel = _relation(session, owner)

    with pytest.raises(InvariantViolationError):
        element_edit.apply_edits(
            session,
            graph_id=g.id,
            ops=[element_edit.ElementUpdate("relation", rel.id, {"cardinality": "lots"})],
            owner_id=owner,
        )


def test_delete_relation_confirms_away(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    rel = _relation(session, owner)

    cs = element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[element_edit.ElementDelete("relation", rel.id)],
        owner_id=owner,
    )

    # 草稿阶段只是标记，确认后才真删。
    assert session.get(Relation, rel.id) is not None
    changeset.confirm_changeset(session, cs.id, owner_id=owner)
    assert session.get(Relation, rel.id) is None


def test_create_relation_joins_both_ends_and_records_op(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    cs = changeset.create_changeset(session, graph_id=g.id, owner_id=owner)
    customer = element_edit.create_object_type(
        session, changeset_id=cs.id, owner_id=owner, name="客户"
    )
    order = element_edit.create_object_type(
        session, changeset_id=cs.id, owner_id=owner, name="订单"
    )

    rel = element_edit.create_relation(
        session,
        changeset_id=cs.id,
        owner_id=owner,
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
        cardinality="one_to_many",
        forward_label="客户",
        reverse_label="订单",
    )

    objects = graph.list_graph_objects(session, g.id)
    assert {customer.id, order.id} <= set(objects)
    assert rel.id in graph.list_graph_relations(session, g.id)
    assert [(op.op, op.payload["kind"], op.target_id) for op in changeset.list_ops(session, cs.id)][
        -1
    ] == ("create", "relation", rel.id)


def test_create_relation_rejects_an_endpoint_outside_the_pool(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    cs = changeset.create_changeset(session, graph_id=g.id, owner_id=owner)
    source = element_edit.create_object_type(
        session, changeset_id=cs.id, owner_id=owner, name="客户"
    )

    with pytest.raises(NotFoundError):
        element_edit.create_relation(
            session,
            changeset_id=cs.id,
            owner_id=owner,
            source_type_id=source.id,
            target_type_id=uuid.uuid4(),
            name="下订单",
        )
