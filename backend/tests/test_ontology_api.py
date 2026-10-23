import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.api.ontology import get_ontology_client
from app.config import get_settings
from app.errors import UnprocessableEntityError
from app.main import _create_app
from app.services import ontology
from app.services.data_service.client import RevisionConflict

API = "/api/v1/ontology"


class FakeOntologyApiClient:
    def __init__(self) -> None:
        self.owner_ids = []
        self.put_calls = []
        self.object_detail = {}
        self.put_error = None

    def get_object_binding(self, object_type_id, *, owner_id):
        self.owner_ids.append(owner_id)
        return {
            "id": str(uuid.uuid4()),
            "object_type_id": str(object_type_id),
            "revision": 4,
            "draft_dataset_kind": "SYNC",
            "draft_dataset_id": str(uuid.uuid4()),
            "draft_mapping": {"attributes": []},
            "status": "ACTIVE",
        }

    def get_relation_binding(self, relation_id, *, owner_id):
        self.owner_ids.append(owner_id)
        return {
            "id": str(uuid.uuid4()),
            "relation_id": str(relation_id),
            "revision": 2,
            "draft_dataset_kind": "SYNC",
            "draft_dataset_id": str(uuid.uuid4()),
            "draft_mapping": {},
            "status": "ACTIVE",
        }

    def put_relation_definition(self, relation_id, *, owner_id, payload, operation_id=None):
        return {"relation_id": str(relation_id)}

    def put_object_binding(self, object_type_id, *, owner_id, payload, operation_id=None):
        if self.put_error is not None:
            raise self.put_error
        self.put_calls.append((object_type_id, owner_id, payload, operation_id))
        return {
            "id": str(uuid.uuid4()),
            "object_type_id": str(object_type_id),
            "revision": payload["base_revision"] + 1,
            **payload,
        }

    def list_dataset_columns(self, *, owner_id, dataset_kind, dataset_id):
        return [
            {"name": "id", "type": "string", "optional": False, "example": "1"},
            {"name": "customer_name", "type": "string", "optional": True, "example": "Ada"},
        ]

    def get_object(self, object_type_id, *, owner_id, primary_key):
        return {**self.object_detail, "primary_key": primary_key}

    def list_object_runs(self, object_type_id, *, owner_id, limit=20):
        return []


@pytest.fixture
def fake_client() -> FakeOntologyApiClient:
    return FakeOntologyApiClient()


@pytest.fixture
def client(session_factory: sessionmaker[Session], fake_client) -> TestClient:
    app = _create_app(session_factory, enable_agent_checkpoints=False)
    app.dependency_overrides[get_ontology_client] = lambda: fake_client
    return TestClient(app)


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
        fk = ontology.create_relation(
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
    fk_ids = {item["id"] for item in body["foreign_key_relations"]}
    assert {str(customer.id), str(order.id)} <= object_ids
    assert str(fk.id) in fk_ids
    assert many_item["direction_label"].startswith("客户")
    assert owner in fake_client.owner_ids


def test_conversations_are_deterministic_per_resource(client):
    created = client.post("/api/v1/object-types", json={"name": "客户"}).json()

    first = client.get(
        f"{API}/conversations",
        params={"resource_kind": "object_type", "resource_id": created["id"]},
    )
    second = client.get(
        f"{API}/conversations",
        params={"resource_kind": "object_type", "resource_id": created["id"]},
    )

    assert first.status_code == 200
    assert first.json()["id"] == second.json()["id"]
    assert first.json()["kind"] == "ontology_workbench"
    assert uuid.UUID(first.json()["resource_id"]) == uuid.UUID(created["id"])


def test_put_binding_rejects_client_supplied_owner(client):
    object_type = client.post("/api/v1/object-types", json={"name": "客户"}).json()
    response = client.put(
        f"{API}/object-types/{object_type['id']}/binding",
        json={
            "owner_id": str(uuid.uuid4()),
            "base_revision": 0,
            "dataset_kind": "SYNC",
            "dataset_id": str(uuid.uuid4()),
            "mapping": {
                "primary_key_attribute_id": str(uuid.uuid4()),
                "attributes": [],
            },
        },
    )

    assert response.status_code == 422


def test_put_binding_uses_login_owner_and_operation_header(client, fake_client):
    object_type = client.post("/api/v1/object-types", json={"name": "客户"}).json()
    operation_id = uuid.uuid4()
    response = client.put(
        f"{API}/object-types/{object_type['id']}/binding",
        headers={"X-Operation-Id": str(operation_id)},
        json={
            "base_revision": 2,
            "dataset_kind": "SYNC",
            "dataset_id": str(uuid.uuid4()),
            "mapping": {
                "primary_key_attribute_id": str(uuid.uuid4()),
                "attributes": [],
            },
        },
    )

    assert response.status_code == 200
    assert response.json()["revision"] == 3
    _, owner, payload, used_operation_id = fake_client.put_calls[0]
    assert owner == uuid.UUID(get_settings().default_user_id)
    assert payload["base_revision"] == 2
    assert used_operation_id == operation_id


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
            {"attribute_id": str(first.id), "value": "1"},
            {"attribute_id": str(second.id), "value": "Ada"},
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
        "properties": [],
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


def test_put_binding_maps_java_409_and_422(client, fake_client):
    object_type = client.post("/api/v1/object-types", json={"name": "客户"}).json()
    body = {
        "base_revision": 2,
        "dataset_kind": "SYNC",
        "dataset_id": str(uuid.uuid4()),
        "mapping": {
            "primary_key_attribute_id": str(uuid.uuid4()),
            "attributes": [],
        },
    }
    url = f"{API}/object-types/{object_type['id']}/binding"

    fake_client.put_error = RevisionConflict(
        "草稿已变化", code="MAPPING_REVISION_CONFLICT", details={"currentRevision": 8}
    )
    conflict = client.put(url, json=body)
    assert conflict.status_code == 409
    assert conflict.json()["error"] == "invariant_violation"

    fake_client.put_error = UnprocessableEntityError(
        "主键未映射",
        code="MAPPING_INVALID",
        details={"blocking": [{"code": "PRIMARY_KEY_MISSING"}]},
    )
    invalid = client.put(url, json=body)
    assert invalid.status_code == 422
    assert invalid.json()["code"] == "MAPPING_INVALID"
    assert invalid.json()["details"]["blocking"][0]["code"] == "PRIMARY_KEY_MISSING"


def test_invalid_object_filters_return_structured_422(client):
    object_type = client.post("/api/v1/object-types", json={"name": "客户"}).json()

    response = client.get(
        f"{API}/object-types/{object_type['id']}/objects",
        params={"filters": "not-json"},
    )

    assert response.status_code == 422
    assert response.json()["error"] == "unprocessable_entity"
    assert response.json()["code"] == "FILTER_INVALID"
