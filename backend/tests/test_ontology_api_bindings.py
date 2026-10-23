"""本体 BFF 绑定写入与错误映射测试。"""

from __future__ import annotations

import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.config import get_settings
from app.errors import UnprocessableEntityError
from app.services.data_service.client import RevisionConflict
from tests.ontology_api_support import API, FakeOntologyApiClient, build_client


@pytest.fixture
def fake_client() -> FakeOntologyApiClient:
    return FakeOntologyApiClient()


@pytest.fixture
def client(session_factory: sessionmaker[Session], fake_client) -> TestClient:
    return build_client(session_factory, fake_client)


def test_put_binding_rejects_client_supplied_owner(client):
    object_type = client.post("/api/v1/object-types", json={"name": "客户"}).json()

    response = client.put(
        f"{API}/object-types/{object_type['id']}/binding",
        json={
            "owner_id": str(uuid.uuid4()),
            "base_revision": 0,
            "dataset_kind": "SYNC",
            "dataset_id": str(uuid.uuid4()),
            "mapping": {"primary_key_attribute_id": str(uuid.uuid4()), "attributes": []},
        },
    )

    assert response.status_code == 422


def test_put_binding_uses_login_owner_and_operation_header(client, fake_client):
    object_type = client.post("/api/v1/object-types", json={"name": "客户"}).json()
    operation = uuid.uuid4()
    response = client.put(
        f"{API}/object-types/{object_type['id']}/binding",
        headers={"X-Operation-Id": str(operation)},
        json={
            "base_revision": 2,
            "dataset_kind": "SYNC",
            "dataset_id": str(uuid.uuid4()),
            "mapping": {"primary_key_attribute_id": str(uuid.uuid4()), "attributes": []},
        },
    )

    assert response.status_code == 200
    assert response.json()["revision"] == 3
    assert response.json()["binding_id"] == response.json()["id"]
    _, owner, payload, used_operation = fake_client.put_calls[0]
    assert owner == uuid.UUID(get_settings().default_user_id)
    assert payload["base_revision"] == 2
    assert used_operation == operation


def test_put_binding_maps_java_409_and_422(client, fake_client):
    object_type = client.post("/api/v1/object-types", json={"name": "客户"}).json()
    body = {
        "base_revision": 2,
        "dataset_kind": "SYNC",
        "dataset_id": str(uuid.uuid4()),
        "mapping": {"primary_key_attribute_id": str(uuid.uuid4()), "attributes": []},
    }
    url = f"{API}/object-types/{object_type['id']}/binding"

    fake_client.put_error = RevisionConflict(
        "草稿已变化", code="MAPPING_REVISION_CONFLICT", details={"currentRevision": 8}
    )
    conflict = client.put(url, json=body)
    assert conflict.status_code == 409
    assert conflict.json()["error"] == "revision_conflict"
    assert conflict.json()["code"] == "MAPPING_REVISION_CONFLICT"
    assert conflict.json()["details"]["currentRevision"] == 8

    fake_client.put_error = UnprocessableEntityError(
        "主键未映射",
        code="MAPPING_INVALID",
        details={"blocking": [{"code": "PRIMARY_KEY_MISSING"}]},
    )
    invalid = client.put(url, json=body)
    assert invalid.status_code == 422
    assert invalid.json()["code"] == "MAPPING_INVALID"
    assert invalid.json()["details"]["blocking"][0]["code"] == "PRIMARY_KEY_MISSING"
