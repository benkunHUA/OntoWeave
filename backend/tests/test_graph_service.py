import uuid

import pytest

from app.errors import InvariantViolationError, NotFoundError
from app.services import graph, ontology


def test_create_graph(session):
    g = graph.create_graph(session, owner_id=uuid.uuid4(), name="销售域建模")
    assert g.name == "销售域建模"
    assert g.id is not None


def test_add_object_to_graph_is_idempotent(session):
    g = graph.create_graph(session, owner_id=uuid.uuid4(), name="g")
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")

    graph.add_object_to_graph(session, g.id, obj.id)
    graph.add_object_to_graph(session, g.id, obj.id)

    assert graph.list_graph_objects(session, g.id) == [obj.id]


def test_add_object_to_graph_rejects_unknown_object(session):
    g = graph.create_graph(session, owner_id=uuid.uuid4(), name="g")
    with pytest.raises(NotFoundError):
        graph.add_object_to_graph(session, g.id, uuid.uuid4())


def test_add_relation_requires_both_endpoints_in_graph(session):
    g = graph.create_graph(session, owner_id=uuid.uuid4(), name="g")
    customer = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    order = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    rel = ontology.create_relation(
        session,
        owner_id=uuid.uuid4(),
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )

    graph.add_object_to_graph(session, g.id, customer.id)
    with pytest.raises(InvariantViolationError):
        graph.add_relation_to_graph(session, g.id, rel.id)

    graph.add_object_to_graph(session, g.id, order.id)
    graph.add_relation_to_graph(session, g.id, rel.id)
    assert graph.list_graph_relations(session, g.id) == [rel.id]


def test_relation_can_be_used_by_two_graphs_differently(session):
    g1 = graph.create_graph(session, owner_id=uuid.uuid4(), name="g1")
    g2 = graph.create_graph(session, owner_id=uuid.uuid4(), name="g2")
    a = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="供货方")
    b = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="采购方")
    supplies = ontology.create_relation(
        session, owner_id=uuid.uuid4(), source_type_id=a.id, target_type_id=b.id, name="供应"
    )
    orders = ontology.create_relation(
        session, owner_id=uuid.uuid4(), source_type_id=a.id, target_type_id=b.id, name="下订单"
    )
    for g in (g1, g2):
        graph.add_object_to_graph(session, g.id, a.id)
        graph.add_object_to_graph(session, g.id, b.id)
    graph.add_relation_to_graph(session, g1.id, supplies.id)
    graph.add_relation_to_graph(session, g2.id, orders.id)

    assert graph.list_graph_relations(session, g1.id) == [supplies.id]
    assert graph.list_graph_relations(session, g2.id) == [orders.id]


def test_removing_object_clears_its_graph_relations(session):
    g = graph.create_graph(session, owner_id=uuid.uuid4(), name="g")
    a = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    b = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    rel = ontology.create_relation(
        session, owner_id=uuid.uuid4(), source_type_id=a.id, target_type_id=b.id, name="下订单"
    )
    for obj in (a, b):
        graph.add_object_to_graph(session, g.id, obj.id)
    graph.add_relation_to_graph(session, g.id, rel.id)

    graph.remove_object_from_graph(session, g.id, a.id)

    assert a.id not in graph.list_graph_objects(session, g.id)
    assert graph.list_graph_relations(session, g.id) == []


def test_remove_object_from_graph_rejects_unknown_graph(session):
    with pytest.raises(NotFoundError):
        graph.remove_object_from_graph(session, uuid.uuid4(), uuid.uuid4())
