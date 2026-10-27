"""锁定 W1-01 MappingSnapshot.requiredAttributeIds 的对象映射契约。"""

from __future__ import annotations

import json
import uuid

import httpx

from app.services import ontology
from tests.support.ontology_api_support import API
from tests.support.ontology_java_support import api_client, create_object_type, mapping_save


def test_object_flows_fill_required_ids_and_keep_java_blocking(session_factory):
    binding_id = uuid.uuid4()
    dataset_id = uuid.uuid4()
    with session_factory() as db:
        object_type_id = create_object_type(db)
        primary_key = ontology.create_property(
            db,
            owner_type_id=object_type_id,
            name="id",
            datatype="text",
            required=True,
            is_primary_key=True,
        )
        required_name = ontology.create_property(
            db, owner_type_id=object_type_id, name="name", datatype="text", required=True
        )
        db.commit()
        primary_key_id = str(primary_key.id)
        required_name_id = str(required_name.id)
        required_ids = {primary_key_id, required_name_id}

    blocking = [
        {
            "code": "REQUIRED_ATTRIBUTE_MISSING",
            "message": "必填属性未映射",
            "attributeId": required_name_id,
            "column": None,
        }
    ]
    mapping = {"primary_key_attribute_id": primary_key_id, "attributes": []}
    requests: dict[str, dict] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        body = json.loads(request.content) if request.content else {}
        requests[f"{request.method} {path}"] = body
        if path.endswith("/binding") and request.method == "PUT":
            java_mapping = {**body["mapping"], "requiredAttributeIds": sorted(required_ids)}
            return httpx.Response(
                200,
                json=mapping_save(
                    binding_id=binding_id,
                    mapping=java_mapping,
                    validation_result={"blocking": blocking, "warnings": []},
                ),
            )
        if path.endswith("/binding/validate"):
            return httpx.Response(200, json={"blocking": blocking, "warnings": [], "valid": False})
        if path.endswith("/binding/preview"):
            return httpx.Response(
                200,
                json={"sample": [], "blocking": blocking, "warnings": [], "skipped": []},
            )
        if path.endswith("/columns"):
            return httpx.Response(
                200,
                json={
                    "columns": [
                        {"name": "id", "type": "string", "optional": False},
                        {"name": "name", "type": "string", "optional": False},
                    ]
                },
            )
        if path.endswith("/binding/generate"):
            return httpx.Response(200, json={"runId": str(uuid.uuid4())})
        raise AssertionError(f"unexpected request: {request.method} {path}")

    client = api_client(session_factory, handler)
    binding_url = f"{API}/object-types/{object_type_id}/binding"
    saved = client.put(
        binding_url,
        json={
            "base_revision": 4,
            "dataset_kind": "SYNC",
            "dataset_id": str(dataset_id),
            "mapping": mapping,
        },
    )
    validated = client.post(f"{binding_url}/validate", json={"mapping": mapping})
    previewed = client.post(f"{binding_url}/preview", json={"mapping": mapping, "limit": 2})
    suggested = client.post(
        f"{binding_url}/suggest",
        json={"dataset_kind": "SYNC", "dataset_id": str(dataset_id), "mapping": mapping},
    )
    generated = client.post(f"{binding_url}/generate", json={"mapping_revision": 5})

    put_body = requests[f"PUT /internal/v1/ontology/object-types/{object_type_id}/binding"]
    assert set(put_body["mapping"]["requiredAttributeIds"]) == required_ids
    validate_body = requests[
        f"POST /internal/v1/ontology/object-types/{object_type_id}/binding/validate"
    ]
    assert set(validate_body["mapping"]["requiredAttributeIds"]) == required_ids
    preview_body = requests[
        f"POST /internal/v1/ontology/object-types/{object_type_id}/binding/preview"
    ]
    assert set(preview_body["mapping"]["requiredAttributeIds"]) == required_ids
    generate_body = requests[
        f"POST /internal/v1/ontology/object-types/{object_type_id}/binding/generate"
    ]
    assert generate_body["mappingRevision"] == 5

    assert saved.status_code == 200
    assert set(saved.json()["draft_mapping"]["required_attribute_ids"]) == required_ids
    assert saved.json()["validation"]["blocking"][0]["code"] == "REQUIRED_ATTRIBUTE_MISSING"
    assert validated.status_code == 200
    assert validated.json()["blocking"][0]["code"] == "REQUIRED_ATTRIBUTE_MISSING"
    assert previewed.status_code == 200
    assert previewed.json()["validation"]["blocking"][0]["code"] == "REQUIRED_ATTRIBUTE_MISSING"
    assert suggested.status_code == 200
    assert set(suggested.json()["mapping"]["required_attribute_ids"]) == required_ids
    assert generated.status_code == 200
    assert uuid.UUID(generated.json()["run_id"])
