"""`test_element_edit.py` 的共享 fixture、假客户端与构造 helper。"""

import uuid

from app.services import graph, ontology


def _graph(session, owner_id: uuid.UUID):
    return graph.create_graph(session, owner_id=owner_id, name="测试图")


def _relation(session, owner: uuid.UUID):
    customer = ontology.create_object_type(session, owner_id=owner, name="客户")
    order = ontology.create_object_type(session, owner_id=owner, name="订单")
    return ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )
