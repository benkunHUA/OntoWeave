import uuid

from sqlalchemy import text

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


def test_non_null_columns_have_db_level_defaults(session):
    """绕开 ORM 直接插入时，默认值应该由数据库补上，而不是只靠 Python 端 default。"""
    customer = ObjectType(owner_id=uuid.uuid4(), name="客户")
    order = ObjectType(owner_id=uuid.uuid4(), name="订单")
    session.add_all([customer, order])
    session.flush()

    session.execute(
        text(
            "insert into property (id, owner_type_id, name) "
            "values (gen_random_uuid(), :owner, '数量')"
        ),
        {"owner": customer.id},
    )
    session.execute(
        text(
            "insert into relation (id, owner_id, source_type_id, target_type_id, name) "
            "values (gen_random_uuid(), :owner, :src, :dst, '下订单')"
        ),
        {"owner": customer.id, "src": customer.id, "dst": order.id},
    )

    datatype, required = session.execute(
        text("select datatype, required from property")
    ).one()
    (direction,) = session.execute(text("select direction from relation")).one()

    assert datatype == "text"
    assert required is False
    assert direction == "forward"
