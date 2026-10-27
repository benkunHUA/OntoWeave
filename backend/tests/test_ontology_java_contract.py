"""用 W1-01 Java DTO 的真实 JSON 形状锁定 BFF 契约。"""

from __future__ import annotations

import uuid

import httpx

from tests.support.ontology_api_support import API
from tests.support.ontology_java_support import (
    api_client,
    create_object_type,
    create_relation_with_types,
    mapping_save,
    object_binding,
    object_preview,
    owner_id,
    relation_binding,
    relation_definition,
    relation_overview,
    relation_preview,
)


def test_dataset_page_parses_java_version_number(session_factory):
    dataset_id = uuid.uuid4()
    version_id = uuid.uuid4()

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/internal/v1/ontology/bindable-datasets"
        return httpx.Response(
            200,
            json={
                "items": [
                    {
                        "kind": "SYNC",
                        "id": str(dataset_id),
                        "name": "customers",
                        "versionId": str(version_id),
                        "versionNumber": 7,
                        "rowCount": 120,
                    }
                ],
                "total": 1,
                "page": 1,
                "size": 20,
            },
        )

    response = api_client(session_factory, handler).get(f"{API}/bindable-datasets")

    assert response.status_code == 200
    item = response.json()["items"][0]
    assert item["version_id"] == str(version_id)
    assert item["version_number"] == 7


def test_binding_get_and_put_use_java_response_fields(session_factory):
    binding_id = uuid.uuid4()
    with session_factory() as db:
        object_type_id = create_object_type(db)

    def handler(request: httpx.Request) -> httpx.Response:
        if request.method == "GET":
            return httpx.Response(200, json=object_binding(object_type_id, owner_id=owner_id()))
        return httpx.Response(200, json=mapping_save(binding_id=binding_id))

    client = api_client(session_factory, handler)
    fetched = client.get(f"{API}/object-types/{object_type_id}/binding")
    saved = client.put(
        f"{API}/object-types/{object_type_id}/binding",
        json={
            "base_revision": 4,
            "dataset_kind": "SYNC",
            "dataset_id": str(uuid.uuid4()),
            "mapping": {"primary_key_attribute_id": str(uuid.uuid4()), "attributes": []},
        },
    )

    assert fetched.status_code == 200
    body = fetched.json()
    assert body["binding_id"] == body["id"]
    assert body["object_type_id"] == str(object_type_id)
    assert body["recent_run"]["status"] == "SUCCEEDED"
    assert body["latest_run"]["id"] == body["recent_run"]["id"]
    assert body["validation"]["valid"] is True
    assert saved.status_code == 200
    assert saved.json()["binding_id"] == str(binding_id)
    assert saved.json()["id"] == str(binding_id)
    assert saved.json()["object_type_id"] == str(object_type_id)
    assert saved.json()["draft_mapping"]["revision"] == 5


def test_preview_parses_java_sample_and_skipped(session_factory):
    with session_factory() as db:
        object_type_id = create_object_type(db)

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path.endswith("/binding/preview")
        return httpx.Response(200, json=object_preview())

    response = api_client(session_factory, handler).post(
        f"{API}/object-types/{object_type_id}/binding/preview", json={"limit": 20}
    )

    assert response.status_code == 200
    body = response.json()
    assert body["rows"][0]["primary_key"] == "customer-1"
    assert body["skipped_count"] == 3
    assert body["skipped_reasons"] == {"MISSING_VALUE": 2, "TYPE_MISMATCH": 1}
    assert body["skipped"][0]["reference"] == "customer-2.name"
    assert body["validation"]["warnings"][0]["code"] == "TITLE_FALLBACK"


def test_relation_preview_parses_java_skipped_list(session_factory):
    with session_factory() as db:
        relation_id = create_relation_with_types(db)[2]

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/relation-definitions/" + str(relation_id)):
            return httpx.Response(200, json=relation_definition(relation_id, owner_id=owner_id()))
        return httpx.Response(200, json=relation_preview())

    response = api_client(session_factory, handler).post(
        f"{API}/relation-types/{relation_id}/binding/preview", json={"limit": 20}
    )

    assert response.status_code == 200
    body = response.json()
    assert body["pairs"][0]["from_primary_key"] == "customer-1"
    assert body["skipped_count"] == 1
    assert body["skipped_reasons"] == {"TARGET_MISSING": 1}


def test_relation_overview_flattens_java_nested_response(session_factory):
    with session_factory() as db:
        relation_id = create_relation_with_types(db)[2]

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/relation-definitions/" + str(relation_id)):
            return httpx.Response(200, json=relation_definition(relation_id, owner_id=owner_id()))
        return httpx.Response(200, json=relation_overview(relation_id, owner_id=owner_id()))

    response = api_client(session_factory, handler).get(
        f"{API}/relation-types/{relation_id}/overview"
    )

    assert response.status_code == 200
    body = response.json()
    assert body["relation_id"] == str(relation_id)
    assert body["relation_name"] == "下订单"
    assert body["relation_kind"] == "MANY_TO_MANY"
    assert body["warnings"][0]["code"] == "TITLE_FALLBACK"
    assert body["latest_version"]["row_count"] == 12
    assert body["latest_run"]["status"] == "SUCCEEDED"
    assert body["source"]["bound"] is True


def test_revision_conflict_response_keeps_code_and_java_details(session_factory):
    current_mapping = {"primaryKeyAttributeId": str(uuid.uuid4()), "attributes": []}
    with session_factory() as db:
        object_type_id = create_object_type(db)

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "PUT"
        return httpx.Response(
            409,
            json={
                "code": "MAPPING_REVISION_CONFLICT",
                "message": "映射版本已变化，请刷新后重试",
                "details": {"currentRevision": 7, "draft": current_mapping},
                "requestId": "req-1",
            },
        )

    client = api_client(session_factory, handler)
    response = client.put(
        f"{API}/object-types/{object_type_id}/binding",
        json={
            "base_revision": 2,
            "dataset_kind": "SYNC",
            "dataset_id": str(uuid.uuid4()),
            "mapping": {"primary_key_attribute_id": str(uuid.uuid4()), "attributes": []},
        },
    )

    assert response.status_code == 409
    body = response.json()
    assert body["error"] == "revision_conflict"
    assert body["code"] == "MAPPING_REVISION_CONFLICT"
    assert body["details"]["currentRevision"] == 7
    assert body["details"]["currentMapping"] == current_mapping


def test_relation_binding_response_normalizes_relation_id(session_factory):
    with session_factory() as db:
        relation_id = create_relation_with_types(db)[2]

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=relation_binding(relation_id, owner_id=owner_id()))

    response = api_client(session_factory, handler).get(
        f"{API}/relation-types/{relation_id}/binding"
    )
    assert response.status_code == 200
    assert response.json()["relation_id"] == str(relation_id)
