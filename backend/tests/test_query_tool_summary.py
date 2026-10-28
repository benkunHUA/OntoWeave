import uuid

from app.agent.tools.query.summary import summarize_query_tool_call
from app.models import ObjectType, Relation


def _graph_types(session, owner_id: uuid.UUID) -> tuple[ObjectType, ObjectType, Relation]:
    customer = ObjectType(owner_id=owner_id, name="客户")
    order = ObjectType(owner_id=owner_id, name="订单")
    session.add_all([customer, order])
    session.flush()
    relation = Relation(
        owner_id=owner_id,
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
        forward_label="下订单",
        reverse_label="属于",
        cardinality="one_to_many",
    )
    session.add(relation)
    session.flush()
    return customer, order, relation


def test_query_objects_summary_uses_object_name_and_search_term(session):
    owner = uuid.uuid4()
    customer, _, _ = _graph_types(session, owner)

    summary = summarize_query_tool_call(
        session,
        owner_id=owner,
        name="query_objects",
        args={"object_type_id": str(customer.id), "q": "张三", "limit": 10},
    )

    assert summary == "客户 · 搜索「张三」，最多 10 条"


def test_related_objects_summary_uses_relation_and_endpoint_names(session):
    owner = uuid.uuid4()
    _, _, relation = _graph_types(session, owner)

    outgoing = summarize_query_tool_call(
        session,
        owner_id=owner,
        name="get_related_objects",
        args={
            "relation_id": str(relation.id),
            "direction": "outgoing",
            "inputs": [{"object_type_id": "t", "primary_key": "1"}],
            "limit": 5,
        },
    )
    incoming = summarize_query_tool_call(
        session,
        owner_id=owner,
        name="get_related_objects",
        args={
            "relation_id": str(relation.id),
            "direction": "incoming",
            "inputs": [{"object_type_id": "t", "primary_key": "1"}],
        },
    )

    assert outgoing == "从「客户」沿「下订单」查「订单」，1 个起点，最多 5 条"
    assert incoming == "从「订单」沿「属于」查「客户」，1 个起点，最多 50 条"


def test_related_objects_summary_falls_back_when_relation_label_repeats_object_name(session):
    owner = uuid.uuid4()
    customer = ObjectType(owner_id=owner, name="客户")
    order = ObjectType(owner_id=owner, name="订单")
    order_line = ObjectType(owner_id=owner, name="订单明细")
    product = ObjectType(owner_id=owner, name="商品")
    session.add_all([customer, order, order_line, product])
    session.flush()
    incoming_relation = Relation(
        owner_id=owner,
        source_type_id=order.id,
        target_type_id=customer.id,
        name="下单客户",
        forward_label="订单",
        reverse_label="客户",
    )
    outgoing_relation = Relation(
        owner_id=owner,
        source_type_id=order_line.id,
        target_type_id=product.id,
        name="所含商品",
        forward_label="订单明细",
        reverse_label="商品",
    )
    session.add_all([incoming_relation, outgoing_relation])
    session.flush()

    incoming = summarize_query_tool_call(
        session,
        owner_id=owner,
        name="get_related_objects",
        args={
            "relation_id": str(incoming_relation.id),
            "direction": "incoming",
            "inputs": [{"object_type_id": "t", "primary_key": "1"}],
        },
    )
    outgoing = summarize_query_tool_call(
        session,
        owner_id=owner,
        name="get_related_objects",
        args={
            "relation_id": str(outgoing_relation.id),
            "direction": "outgoing",
            "inputs": [{"object_type_id": "t", "primary_key": "1"}],
        },
    )

    assert incoming == "从「客户」沿「下单客户」查「订单」，1 个起点，最多 50 条"
    assert outgoing == "从「订单明细」沿「所含商品」查「商品」，1 个起点，最多 50 条"


def test_query_summary_ignores_other_tools_and_unknown_resources(session):
    owner = uuid.uuid4()

    assert (
        summarize_query_tool_call(
            session, owner_id=owner, name="rewrite_query", args={"query": "订单"}
        )
        is None
    )
    assert (
        summarize_query_tool_call(
            session,
            owner_id=owner,
            name="query_objects",
            args={"object_type_id": str(uuid.uuid4()), "primary_keys": ["C001"]},
        )
        == "对象 · 按主键「C001」"
    )
