import json
import uuid

import httpx
import pytest

from app.config import Settings
from app.errors import UnprocessableEntityError
from app.services.data_service.client import RevisionConflict
from app.services.data_service.ontology_client import OntologyDataServiceClient


def _client(handler) -> OntologyDataServiceClient:
    return OntologyDataServiceClient(
        Settings(data_service_url="http://java:18001"),
        transport=httpx.MockTransport(handler),
    )


def test_client_translates_binding_body_and_response():
    captured = {}
    attribute_id = uuid.uuid4()
    object_type_id = uuid.uuid4()
    dataset_id = uuid.uuid4()

    def handler(request: httpx.Request) -> httpx.Response:
        captured["request"] = request
        return httpx.Response(
            200,
            json={
                "id": str(uuid.uuid4()),
                "objectTypeId": str(object_type_id),
                "revision": 2,
                "draftDatasetKind": "SYNC",
                "draftDatasetId": str(dataset_id),
                "draftMapping": {
                    "primaryKeyAttributeId": str(attribute_id),
                    "attributes": [
                        {
                            "attributeId": str(attribute_id),
                            "column": "id",
                            "datatype": "text",
                            "required": True,
                            "isArray": False,
                            "enumValues": None,
                        }
                    ],
                },
            },
        )

    client = _client(handler)
    owner = uuid.uuid4()
    operation_id = uuid.uuid4()
    result = client.put_object_binding(
        object_type_id,
        owner_id=owner,
        payload={
            "base_revision": 1,
            "dataset_kind": "SYNC",
            "dataset_id": dataset_id,
            "mapping": {
                "primary_key_attribute_id": attribute_id,
                "title_key_attribute_id": None,
                "attributes": [
                    {
                        "attribute_id": attribute_id,
                        "column": "id",
                        "datatype": "text",
                        "required": True,
                        "is_array": False,
                        "enum_values": None,
                    }
                ],
            },
        },
        operation_id=operation_id,
    )

    request = captured["request"]
    body = request.read().decode()
    assert request.method == "PUT"
    assert request.url.path == f"/internal/v1/ontology/object-types/{object_type_id}/binding"
    assert request.url.params["ownerId"] == str(owner)
    assert request.headers["X-Internal-Token"] == "dev-internal-token"
    assert request.headers["X-Operation-Id"] == str(operation_id)
    payload = json.loads(body)
    assert payload["baseRevision"] == 1
    assert payload["datasetKind"] == "SYNC"
    assert payload["mapping"]["primaryKeyAttributeId"] == str(attribute_id)
    assert payload["mapping"]["attributes"][0]["isArray"] is False
    assert result["object_type_id"] == str(object_type_id)
    assert result["draft_mapping"]["attributes"][0]["attribute_id"] == str(attribute_id)


def test_client_reads_columns_and_sends_json_filters():
    captured = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        if request.url.path.endswith("/columns"):
            return httpx.Response(
                200,
                json=[{"name": "id", "type": "long", "optional": False, "example": 1}],
            )
        return httpx.Response(
            200,
            json={"items": [], "total": 0, "page": 1, "pageSize": 20, "indexVersionId": None},
        )

    client = _client(handler)
    owner = uuid.uuid4()
    dataset_id = uuid.uuid4()
    object_type_id = uuid.uuid4()
    attribute_id = uuid.uuid4()

    columns = client.list_dataset_columns(
        owner_id=owner, dataset_kind="SYNC", dataset_id=dataset_id
    )
    page = client.list_objects(
        object_type_id,
        owner_id=owner,
        page=2,
        page_size=50,
        q="ada",
        filters=[{"attribute_id": attribute_id, "op": "eq", "value": "A"}],
    )

    assert columns == [{"name": "id", "type": "long", "optional": False, "example": 1}]
    assert captured[0].url.path.endswith(f"/bindable-datasets/SYNC/{dataset_id}/columns")
    assert captured[1].url.params["pageSize"] == "50"
    assert str(attribute_id) in captured[1].url.params["filters"]
    assert page["page_size"] == 20


def test_client_reads_bindable_dataset_detail_for_indexing():
    captured = {}
    dataset_id = uuid.uuid4()
    version_id = uuid.uuid4()

    def handler(request: httpx.Request) -> httpx.Response:
        captured["request"] = request
        return httpx.Response(
            200,
            json={
                "kind": "SYNC",
                "id": str(dataset_id),
                "name": "retail.orders",
                "versionId": str(version_id),
                "versionNumber": 3,
                "rowCount": 42,
                "ownerId": str(uuid.uuid4()),
                "publishedAt": "2026-10-24T10:00:00Z",
                "columns": [
                    {"name": "order_id", "type": "string", "optional": False, "example": "o-1"}
                ],
            },
        )

    result = _client(handler).get_bindable_dataset(
        dataset_kind="SYNC", dataset_id=dataset_id, version_id=version_id
    )

    request = captured["request"]
    assert request.url.path.endswith(f"/bindable-datasets/SYNC/{dataset_id}")
    assert request.url.params["versionId"] == str(version_id)
    assert "ownerId" not in request.url.params
    assert result["name"] == "retail.orders"
    assert result["columns"][0]["name"] == "order_id"


def test_any_409_is_a_revision_conflict_with_details():
    def handler(request: httpx.Request) -> httpx.Response:  # noqa: ARG001
        return httpx.Response(
            409,
            json={
                "code": "MAPPING_REVISION_CONFLICT",
                "message": "草稿已变化",
                "currentRevision": 7,
                "currentMapping": {"primaryKeyAttributeId": str(uuid.uuid4())},
            },
        )

    with pytest.raises(RevisionConflict) as caught:
        _client(handler).put_relation_binding(
            uuid.uuid4(), owner_id=uuid.uuid4(), payload={"base_revision": 3}
        )

    assert caught.value.code == "MAPPING_REVISION_CONFLICT"
    assert caught.value.details["currentRevision"] == 7
    assert "currentMapping" in caught.value.details


def test_422_keeps_business_code_and_details():
    def handler(request: httpx.Request) -> httpx.Response:  # noqa: ARG001
        return httpx.Response(
            422,
            json={
                "code": "MAPPING_INVALID",
                "message": "主键未映射",
                "details": {"blocking": [{"code": "PRIMARY_KEY_MISSING"}]},
            },
        )

    with pytest.raises(UnprocessableEntityError) as caught:
        _client(handler).generate_object_binding(
            uuid.uuid4(), owner_id=uuid.uuid4(), payload={"mapping_revision": 1}
        )

    assert caught.value.code == "MAPPING_INVALID"
    assert caught.value.details["blocking"][0]["code"] == "PRIMARY_KEY_MISSING"


def test_relation_definition_uses_documented_path_and_operation_header():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["request"] = request
        return httpx.Response(200, json={"ok": True})

    relation_id = uuid.uuid4()
    operation_id = uuid.uuid4()
    _client(handler).put_relation_definition(
        relation_id,
        owner_id=uuid.uuid4(),
        payload={"source_type_id": uuid.uuid4(), "definition_revision": 3},
        operation_id=operation_id,
    )

    request = captured["request"]
    assert request.url.path == f"/internal/v1/ontology/relation-definitions/{relation_id}"
    assert request.headers["X-Operation-Id"] == str(operation_id)
    assert b'"sourceTypeId"' in request.read()


def test_relation_runs_use_binding_runs_path_and_accept_list_payload():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["request"] = request
        return httpx.Response(200, json=[{"id": str(uuid.uuid4()), "status": "SUCCEEDED"}])

    relation_id = uuid.uuid4()
    runs = _client(handler).list_relation_runs(relation_id, owner_id=uuid.uuid4(), limit=5)

    assert captured["request"].url.path == (
        f"/internal/v1/ontology/relation-types/{relation_id}/binding/runs"
    )
    assert captured["request"].url.params["limit"] == "5"
    assert runs[0]["status"] == "SUCCEEDED"
