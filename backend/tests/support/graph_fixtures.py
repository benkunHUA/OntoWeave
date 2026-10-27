"""已发布图的共享测试数据。"""

from __future__ import annotations

from app.services import graph, ontology, status


def _published_graph(session, owner):
    row = graph.create_graph(session, owner_id=owner, name="销售域")
    customer = ontology.create_object_type(session, owner_id=owner, name="客户")
    order = ontology.create_object_type(session, owner_id=owner, name="订单")
    customer_key = ontology.create_property(
        session,
        owner_type_id=customer.id,
        name="客户编号",
        datatype="text",
        is_primary_key=True,
    )
    order_amount = ontology.create_property(
        session,
        owner_type_id=order.id,
        name="金额",
        datatype="decimal",
    )
    relation = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
        forward_label="下单",
    )
    for obj in (customer, order):
        graph.add_object_to_graph(session, row.id, obj.id, owner_id=owner)
        ontology.transition_object_type(session, obj.id, status.CONFIRMED, owner_id=owner)
    graph.add_relation_to_graph(session, row.id, relation.id, owner_id=owner)
    ontology.transition_relation(session, relation.id, status.CONFIRMED, owner_id=owner)
    for prop in (customer_key, order_amount):
        ontology.transition_property(session, prop.id, status.CONFIRMED, owner_id=owner)
    return row, customer, order, relation
