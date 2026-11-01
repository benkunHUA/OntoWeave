import json
import uuid

import httpx
import pytest

from app.clients.data_service.ontology_client import EditConflict
from tests.support.ontology_client_fixtures import _client


def test_preview_edit_set_translates_request_and_response():
    captured = {}
    execution_id = uuid.uuid4()
    owner_id = uuid.uuid4()
    object_type_id = uuid.uuid4()
    property_id = uuid.uuid4()

    def handler(request: httpx.Request) -> httpx.Response:
        captured["request"] = request
        return httpx.Response(
            200,
            json={
                "executionId": str(execution_id),
                "objects": [
                    {
                        "kind": "OBJECT_CREATE",
                        "objectTypeId": str(object_type_id),
                        "primaryKey": "T-1",
                        "before": None,
                        "after": {"primaryKey": "T-1", "version": 0},
                    }
                ],
                "relations": [],
                "conflicts": [],
                "applicable": True,
            },
        )

    result = _client(handler).preview_edit_set(
        {
            "execution_id": execution_id,
            "owner_id": owner_id,
            "object_creates": [
                {
                    "object_type_id": object_type_id,
                    "primary_key": "T-1",
                    "values": {property_id: 12.5},
                }
            ],
        }
    )

    request = captured["request"]
    body = json.loads(request.read())
    assert request.method == "POST"
    assert request.url.path == "/internal/v1/ontology/edit-sets/preview"
    assert body["executionId"] == str(execution_id)
    assert body["ownerId"] == str(owner_id)
    assert body["objectCreates"][0]["objectTypeId"] == str(object_type_id)
    assert body["objectCreates"][0]["values"][str(property_id)] == 12.5
    assert result["execution_id"] == str(execution_id)
    assert result["objects"][0]["object_type_id"] == str(object_type_id)
    assert result["applicable"] is True


def test_apply_conflict_preserves_conflicts():
    conflicts = [
        {
            "code": "EDIT_CONFLICT",
            "message": "对象版本已变化",
            "details": {"primaryKey": "T-1"},
        }
    ]

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/internal/v1/ontology/edit-sets/apply"
        return httpx.Response(
            409,
            json={
                "code": "EDIT_CONFLICT",
                "message": "edit set 不可应用",
                "details": {"conflicts": conflicts},
            },
        )

    with pytest.raises(EditConflict) as raised:
        _client(handler).apply_edit_set({"execution_id": uuid.uuid4()})

    assert raised.value.code == "EDIT_CONFLICT"
    assert raised.value.conflicts == conflicts
    assert raised.value.details["conflicts"] == conflicts
