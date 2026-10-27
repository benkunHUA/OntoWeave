"""`test_ontology_client.py` 中 transport 相关测试。"""

import uuid

import httpx
import pytest

from app.clients.data_service.client import RevisionConflict
from app.errors import UnprocessableEntityError
from tests.support.ontology_client_fixtures import _client


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
