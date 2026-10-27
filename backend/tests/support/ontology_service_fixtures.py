"""`test_ontology_service.py` 的共享 fixture、假客户端与构造 helper。"""

import uuid

from app.services import ontology


def _customer_order(session):
    customer = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    order = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    return customer, order
