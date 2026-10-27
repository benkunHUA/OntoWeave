"""本体 BFF 路由测试的共享假客户端与装配。"""

from __future__ import annotations

import uuid

from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.api.ontology import get_ontology_client
from app.main import _create_app

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
            "binding_id": str(uuid.uuid4()),
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
            "binding_id": str(uuid.uuid4()),
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
            "binding_id": str(uuid.uuid4()),
            "revision": payload["base_revision"] + 1,
            "mapping": payload["mapping"],
            "validation": {"blocking": [], "warnings": []},
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


def build_client(
    session_factory: sessionmaker[Session], fake_client: FakeOntologyApiClient
) -> TestClient:
    app = _create_app(session_factory, enable_agent_checkpoints=False)
    app.dependency_overrides[get_ontology_client] = lambda: fake_client
    return TestClient(app)


__all__ = ["API", "FakeOntologyApiClient", "build_client"]
