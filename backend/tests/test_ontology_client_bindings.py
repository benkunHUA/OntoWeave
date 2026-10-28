"""`test_ontology_client.py` 中 bindings 相关测试。"""

import json
import uuid

import httpx

from tests.support.ontology_client_fixtures import _client


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
    assert captured[1].url.path.endswith(f"/object-types/{object_type_id}/objects")
    assert captured[1].url.params["pageSize"] == "50"
    assert str(attribute_id) in captured[1].url.params["filters"]
    assert page["page_size"] == 20


def test_client_posts_controlled_query_contracts():
    captured = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(
            200,
            json={
                "items": [
                    {
                        "objectTypeId": str(uuid.uuid4()),
                        "primaryKey": "o-1",
                        "title": "订单 1",
                        "properties": [],
                    }
                ],
                "total": 1,
                "truncated": False,
                "indexVersionId": str(uuid.uuid4()),
            },
        )

    client = _client(handler)
    owner = uuid.uuid4()
    object_type_id = uuid.uuid4()
    relation_id = uuid.uuid4()
    attribute_id = uuid.uuid4()

    client.query_objects(
        object_type_id,
        owner_id=owner,
        primary_keys=["o-1", "o-2"],
        filters=[{"attribute_id": attribute_id, "op": "eq", "value": "open"}],
        sort=[{"attribute_id": attribute_id, "direction": "desc"}],
        limit=1,
        project=[attribute_id],
    )
    client.related_objects(
        relation_id,
        owner_id=owner,
        direction="outgoing",
        inputs=[{"object_type_id": str(object_type_id), "primary_key": "c-1"}],
        limit=1,
    )

    object_body = json.loads(captured[0].read())
    related_body = json.loads(captured[1].read())
    assert captured[0].url.path.endswith(f"/object-types/{object_type_id}/objects/query")
    assert object_body["primaryKeys"] == ["o-1", "o-2"]
    assert object_body["filters"][0]["attributeId"] == str(attribute_id)
    assert object_body["sort"][0]["direction"] == "desc"
    assert captured[1].url.path.endswith(f"/relation-types/{relation_id}/related-objects")
    assert related_body["inputs"][0]["primaryKey"] == "c-1"


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
