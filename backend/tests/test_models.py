import uuid

from app.models import Graph, ObjectType, Property, Relation


def test_object_type_defaults_to_draft(session):
    graph = Graph(owner_id=uuid.uuid4(), name="销售域建模")
    session.add(graph)
    session.flush()

    obj = ObjectType(owner_id=uuid.uuid4(), name="客户", home_graph_id=graph.id)
    session.add(obj)
    session.flush()
    session.refresh(obj)

    assert obj.status == "draft"
    assert obj.id is not None
    assert obj.created_at is not None


def test_property_belongs_to_object_type(session):
    obj = ObjectType(owner_id=uuid.uuid4(), name="订单")
    session.add(obj)
    session.flush()

    prop = Property(owner_type_id=obj.id, name="数量", datatype="int")
    session.add(prop)
    session.flush()

    assert prop.id is not None
    assert prop.required is False
    assert prop.status == "draft"


def test_relation_connects_two_object_types(session):
    customer = ObjectType(owner_id=uuid.uuid4(), name="客户")
    order = ObjectType(owner_id=uuid.uuid4(), name="订单")
    session.add_all([customer, order])
    session.flush()

    rel = Relation(
        owner_id=uuid.uuid4(),
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )
    session.add(rel)
    session.flush()

    assert rel.id is not None
    assert rel.status == "draft"
    assert rel.direction == "forward"
