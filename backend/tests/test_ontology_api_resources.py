"""本体 BFF 的资源、详情与查询参数测试。"""

from __future__ import annotations

import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.config import get_settings
from app.services import ontology
from tests.support.ontology_api_support import API, FakeOntologyApiClient, build_client


@pytest.fixture
def fake_client() -> FakeOntologyApiClient:
    return FakeOntologyApiClient()


@pytest.fixture
def client(session_factory: sessionmaker[Session], fake_client) -> TestClient:
    return build_client(session_factory, fake_client)


def test_resources_groups_relations_and_uses_login_owner(client, session_factory, fake_client):
    owner = uuid.UUID(get_settings().default_user_id)
    with session_factory() as session:
        customer = ontology.create_object_type(session, owner_id=owner, name="客户")
        order = ontology.create_object_type(session, owner_id=owner, name="订单")
        many = ontology.create_relation(
            session,
            owner_id=owner,
            source_type_id=customer.id,
            target_type_id=order.id,
            name="下订单",
            cardinality="many_to_many",
        )
        foreign_key = ontology.create_relation(
            session,
            owner_id=owner,
            source_type_id=order.id,
            target_type_id=customer.id,
            name="属于",
            cardinality="one_to_many",
        )
        session.commit()

    response = client.get(f"{API}/resources")

    assert response.status_code == 200
    body = response.json()
    object_ids = {item["id"] for item in body["object_types"]}
    many_item = next(item for item in body["many_to_many_relations"] if item["id"] == str(many.id))
    foreign_key_ids = {item["id"] for item in body["foreign_key_relations"]}
    assert {str(customer.id), str(order.id)} <= object_ids
    assert str(foreign_key.id) in foreign_key_ids
    assert many_item["direction_label"].startswith("客户")
    assert owner in fake_client.owner_ids


def test_conversations_are_deterministic_per_resource(client):
    created = client.post("/api/v1/object-types", json={"name": "客户"}).json()

    missing = client.get(
        f"{API}/conversations",
        params={"resource_kind": "object_type", "resource_id": created["id"]},
    )
    first = client.post(
        f"{API}/conversations",
        params={"resource_kind": "object_type", "resource_id": created["id"]},
    )
    second = client.post(
        f"{API}/conversations",
        params={"resource_kind": "object_type", "resource_id": created["id"]},
    )

    assert missing.status_code == 200
    assert missing.json() is None
    assert first.status_code == 201
    assert first.json()["id"] == second.json()["id"]
    assert first.json()["kind"] == "ontology_workbench"
    assert uuid.UUID(first.json()["resource_id"]) == uuid.UUID(created["id"])


def test_resource_definition_returns_full_object_properties(client, session_factory):
    object_type = client.post("/api/v1/object-types", json={"name": "客户"}).json()
    object_type_id = uuid.UUID(object_type["id"])
    with session_factory() as session:
        ontology.create_property(
            session,
            owner_type_id=object_type_id,
            name="id",
            datatype="text",
            is_primary_key=True,
        )
        ontology.create_property(
            session,
            owner_type_id=object_type_id,
            name="name",
            datatype="text",
            is_title_key=True,
        )
        session.commit()

    response = client.get(f"{API}/resources/object_type/{object_type_id}")

    assert response.status_code == 200
    body = response.json()
    assert body["kind"] == "object_type"
    assert body["id"] == str(object_type_id)
    assert {item["name"] for item in body["properties"]} == {"id", "name"}
    assert next(item for item in body["properties"] if item["name"] == "id")["is_primary_key"]


def test_resource_definition_returns_relation_endpoints(client, session_factory):
    owner = uuid.UUID(get_settings().default_user_id)
    with session_factory() as session:
        customer = ontology.create_object_type(session, owner_id=owner, name="客户")
        order = ontology.create_object_type(session, owner_id=owner, name="订单")
        customer_id = ontology.create_property(
            session,
            owner_type_id=customer.id,
            name="id",
            datatype="text",
            is_primary_key=True,
        )
        ontology.create_property(
            session,
            owner_type_id=order.id,
            name="id",
            datatype="text",
            is_primary_key=True,
        )
        foreign_key = ontology.create_property(
            session, owner_type_id=order.id, name="customer_id", datatype="text"
        )
        relation = ontology.create_relation(
            session,
            owner_id=owner,
            source_type_id=order.id,
            target_type_id=customer.id,
            name="属于",
            cardinality="one_to_many",
            foreign_key_property_id=foreign_key.id,
        )
        session.commit()
        relation_id = relation.id
        customer_id_value = customer_id.id
        order_id = order.id
        customer_type_id = customer.id

    response = client.get(f"{API}/resources/relation/{relation_id}")

    assert response.status_code == 200
    body = response.json()
    assert body["kind"] == "relation"
    assert body["source"]["id"] == str(order_id)
    assert body["target"]["id"] == str(customer_type_id)
    assert [item["name"] for item in body["target"]["properties"]] == ["id"]
    assert body["target"]["properties"][0]["id"] == str(customer_id_value)
    assert body["foreign_key_property"]["name"] == "customer_id"


def test_resource_definition_rejects_unknown_kind_and_missing_resource(client):
    response = client.get(f"{API}/resources/unknown/{uuid.uuid4()}")
    assert response.status_code == 400

    response = client.get(f"{API}/resources/object_type/{uuid.uuid4()}")
    assert response.status_code == 404


def test_object_detail_merges_all_defined_properties_in_order(client, session_factory, fake_client):
    object_type = client.post("/api/v1/object-types", json={"name": "客户"}).json()
    object_type_id = uuid.UUID(object_type["id"])
    with session_factory() as session:
        first = ontology.create_property(
            session,
            owner_type_id=object_type_id,
            name="id",
            datatype="text",
            is_primary_key=True,
        )
        second = ontology.create_property(
            session, owner_type_id=object_type_id, name="name", datatype="text"
        )
        session.commit()

    fake_client.object_detail = {
        "title": "Ada",
        "index_version_id": str(uuid.uuid4()),
        "properties": [
            {"attribute_id": str(second.id), "value": "Ada"},
            {"attribute_id": str(first.id), "value": "1"},
        ],
    }
    response = client.get(
        f"{API}/object-types/{object_type['id']}/object", params={"primary_key": "1"}
    )
    assert response.status_code == 200
    assert response.json()["properties"] == {str(first.id): "1", str(second.id): "Ada"}

    fake_client.object_detail = {
        "title": None,
        "index_version_id": str(uuid.uuid4()),
        "properties": {},
    }
    response = client.get(
        f"{API}/object-types/{object_type['id']}/object", params={"primary_key": "1"}
    )
    assert response.json()["properties"] == {str(first.id): None, str(second.id): None}


def test_object_suggest_returns_mapping_and_reasons(client, session_factory):
    object_type = client.post("/api/v1/object-types", json={"name": "客户"}).json()
    object_type_id = uuid.UUID(object_type["id"])
    with session_factory() as session:
        ontology.create_property(
            session,
            owner_type_id=object_type_id,
            name="id",
            datatype="text",
            is_primary_key=True,
        )
        ontology.create_property(
            session,
            owner_type_id=object_type_id,
            name="name",
            datatype="text",
            is_title_key=True,
        )
        session.commit()

    response = client.post(
        f"{API}/object-types/{object_type['id']}/binding/suggest",
        json={"dataset_kind": "SYNC", "dataset_id": str(uuid.uuid4())},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["dataset_kind"] == "SYNC"
    assert {item["column"] for item in body["mapping"]["attributes"]} == {
        "id",
        "customer_name",
    }
    assert body["reasons"]


def test_invalid_object_filters_return_structured_422(client):
    object_type = client.post("/api/v1/object-types", json={"name": "客户"}).json()

    response = client.get(
        f"{API}/object-types/{object_type['id']}/objects",
        params={"filters": "not-json"},
    )

    assert response.status_code == 422
    assert response.json()["error"] == "unprocessable_entity"
    assert response.json()["code"] == "FILTER_INVALID"
