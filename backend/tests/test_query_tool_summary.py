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


def test_query_step_summary_uses_relation_and_endpoint_names(session):
    owner = uuid.uuid4()
    _, _, relation = _graph_types(session, owner)

    outgoing = summarize_query_tool_call(
        session,
        owner_id=owner,
        name="execute_query_step",
        args={"relation_id": str(relation.id), "direction": "outgoing", "limit": 5},
    )
    incoming = summarize_query_tool_call(
        session,
        owner_id=owner,
        name="execute_query_step",
        args={"relation_id": str(relation.id), "direction": "incoming"},
    )

    assert outgoing == "从「客户」沿「下订单」查「订单」，最多 5 条"
    assert incoming == "从「订单」沿「属于」查「客户」，最多 20 条"


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
            args={"object_type_id": str(uuid.uuid4()), "primary_key": "C001"},
        )
        == "对象 · 按主键「C001」"
    )
