"""用 W1-03 Java DTO 的真实 JSON 形状锁定对象查询契约。"""

from __future__ import annotations

import httpx

from app.services import ontology
from tests.ontology_api_support import API
from tests.ontology_java_support import (
    api_client,
    create_object_type,
    create_relation_with_types,
    object_detail,
    relation_group,
)


def test_relation_group_parses_java_related_objects_as_items(session_factory):
    with session_factory() as db:
        source_id, _, relation_id = create_relation_with_types(db)

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path.endswith(f"/object-types/{source_id}/object-relations")
        return httpx.Response(200, json={"groups": [relation_group(relation_id)]})

    response = api_client(session_factory, handler).get(
        f"{API}/object-types/{source_id}/object-relations",
        params={"primary_key": "customer-1", "direction": "outgoing"},
    )

    assert response.status_code == 200
    group = response.json()[0]
    assert group["relation_id"] == str(relation_id)
    assert group["items"] == [{"primary_key": "order-1", "title": "订单 1"}]
    assert group["total"] == 1


def test_object_detail_parses_java_property_list(session_factory):
    with session_factory() as db:
        object_type_id = create_object_type(db)
        name_property = ontology.create_property(
            db, owner_type_id=object_type_id, name="name", datatype="text"
        )
        db.commit()
        name_property_id = name_property.id

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path.endswith(f"/object-types/{object_type_id}/object")
        return httpx.Response(
            200,
            json=object_detail(
                "customer-1",
                properties=[{"attributeId": str(name_property_id), "value": "Ada"}],
            ),
        )

    response = api_client(session_factory, handler).get(
        f"{API}/object-types/{object_type_id}/object",
        params={"primary_key": "customer-1"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["primary_key"] == "customer-1"
    assert body["properties"][str(name_property_id)] == "Ada"
