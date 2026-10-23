"""Java DTO 真实 JSON 形状与 MockTransport 装配。"""

from __future__ import annotations

import uuid

import httpx
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.api.ontology import get_ontology_client
from app.config import Settings, get_settings
from app.main import _create_app
from app.services import ontology
from app.services.data_service.ontology_client import OntologyDataServiceClient


def owner_id() -> uuid.UUID:
    return uuid.UUID(get_settings().default_user_id)


def api_client(session_factory: sessionmaker[Session], handler) -> TestClient:
    data_client = OntologyDataServiceClient(
        Settings(data_service_url="http://java:18001"),
        transport=httpx.MockTransport(handler),
    )
    app = _create_app(session_factory, enable_agent_checkpoints=False)
    app.dependency_overrides[get_ontology_client] = lambda: data_client
    return TestClient(app)


def create_object_type(session: Session) -> uuid.UUID:
    obj = ontology.create_object_type(session, owner_id=owner_id(), name="客户")
    session.commit()
    return obj.id


def create_relation_with_types(session: Session) -> tuple[uuid.UUID, uuid.UUID, uuid.UUID]:
    source = ontology.create_object_type(session, owner_id=owner_id(), name="客户")
    target = ontology.create_object_type(session, owner_id=owner_id(), name="订单")
    ontology.create_property(
        session, owner_type_id=source.id, name="id", datatype="text", is_primary_key=True
    )
    ontology.create_property(
        session, owner_type_id=target.id, name="id", datatype="text", is_primary_key=True
    )
    relation = ontology.create_relation(
        session,
        owner_id=owner_id(),
        source_type_id=source.id,
        target_type_id=target.id,
        name="下订单",
        cardinality="many_to_many",
    )
    session.commit()
    return source.id, target.id, relation.id


def recent_run(run_id: uuid.UUID | None = None) -> dict:
    return {
        "id": str(run_id or uuid.uuid4()),
        "ownerId": str(uuid.uuid4()),
        "subjectKind": "OBJECT_TYPE",
        "subjectId": str(uuid.uuid4()),
        "bindingId": str(uuid.uuid4()),
        "triggerType": "MANUAL",
        "status": "SUCCEEDED",
        "phase": None,
        "targetDatasetKind": "SYNC",
        "targetDatasetVersionId": str(uuid.uuid4()),
        "mappingRevision": 4,
        "requestedAt": "2026-10-23T10:00:00Z",
        "startedAt": "2026-10-23T10:00:01Z",
        "heartbeatAt": "2026-10-23T10:00:02Z",
        "finishedAt": "2026-10-23T10:00:03Z",
        "errorCode": None,
        "errorMessage": None,
        "stats": {"written": 10},
    }


def validation() -> dict:
    return {
        "blocking": [],
        "warnings": [
            {
                "code": "TITLE_FALLBACK",
                "message": "标题列缺失",
                "attributeId": str(uuid.uuid4()),
                "column": "name",
            }
        ],
    }


def object_binding(
    object_type_id: uuid.UUID,
    *,
    owner_id: uuid.UUID,
    binding_id: uuid.UUID | None = None,
) -> dict:
    return {
        "bindingId": str(binding_id or uuid.uuid4()),
        "ownerId": str(owner_id),
        "objectTypeId": str(object_type_id),
        "status": "ACTIVE",
        "revision": 4,
        "draftDatasetKind": "SYNC",
        "draftDatasetId": str(uuid.uuid4()),
        "draftMapping": {"attributes": [], "revision": 4},
        "activeDatasetKind": "SYNC",
        "activeDatasetId": str(uuid.uuid4()),
        "activeDatasetVersionId": str(uuid.uuid4()),
        "activeMappingRevision": 3,
        "currentIndexVersionId": str(uuid.uuid4()),
        "validationState": "OK",
        "validation": validation(),
        "recentRun": recent_run(),
    }


def relation_binding(
    relation_id: uuid.UUID,
    *,
    owner_id: uuid.UUID,
    binding_id: uuid.UUID | None = None,
) -> dict:
    data = object_binding(uuid.uuid4(), owner_id=owner_id, binding_id=binding_id)
    data.pop("objectTypeId")
    data["relationId"] = str(relation_id)
    data["recentRun"]["subjectKind"] = "RELATION"
    return data


def mapping_save(
    *,
    binding_id: uuid.UUID | None = None,
    mapping: dict | None = None,
    validation_result: dict | None = None,
) -> dict:
    return {
        "bindingId": str(binding_id or uuid.uuid4()),
        "revision": 5,
        "mapping": mapping or {"attributes": [], "revision": 5},
        "validation": validation_result or validation(),
    }


def object_preview() -> dict:
    return {
        "sample": [
            {
                "primaryKey": "customer-1",
                "title": "Ada",
                "properties": {"name": "Ada"},
            }
        ],
        "blocking": [],
        "warnings": validation()["warnings"],
        "skipped": [
            {
                "code": "MISSING_VALUE",
                "message": "必填值为空",
                "reference": "customer-2.name",
            },
            {
                "code": "MISSING_VALUE",
                "message": "必填值为空",
                "reference": "customer-3.name",
            },
            {
                "code": "TYPE_MISMATCH",
                "message": "无法转换类型",
                "reference": "customer-4.age",
            },
        ],
    }


def object_detail(primary_key: str, *, properties: list[dict]) -> dict:
    return {
        "primaryKey": primary_key,
        "title": "Ada",
        "indexVersionId": str(uuid.uuid4()),
        "properties": properties,
    }


def relation_group(
    relation_id: uuid.UUID,
    *,
    direction: str = "outgoing",
    name: str = "下订单",
    related: list[dict] | None = None,
) -> dict:
    objects = related or [{"primaryKey": "order-1", "title": "订单 1"}]
    return {
        "relationId": str(relation_id),
        "direction": direction,
        "name": name,
        "total": len(objects),
        "relatedObjects": objects,
    }


def relation_preview() -> dict:
    return {
        "sample": [{"fromPrimaryKey": "customer-1", "toPrimaryKey": "order-1"}],
        "blocking": [],
        "warnings": [],
        "skipped": [
            {
                "code": "TARGET_MISSING",
                "message": "目标对象不存在",
                "reference": "order-9",
            }
        ],
    }


def relation_definition(relation_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
    return {
        "relationId": str(relation_id),
        "ownerId": str(owner_id),
        "sourceTypeId": str(uuid.uuid4()),
        "targetTypeId": str(uuid.uuid4()),
        "name": "下订单",
        "cardinality": "many_to_many",
        "foreignKeyPropertyId": None,
        "foreignKeyDatatype": None,
        "sourcePrimaryKeyAttributeId": str(uuid.uuid4()),
        "sourcePrimaryKeyDatatype": "text",
        "targetPrimaryKeyAttributeId": str(uuid.uuid4()),
        "targetPrimaryKeyDatatype": "text",
        "definitionRevision": 1,
        "syncedAt": "2026-10-23T10:00:00Z",
        "warnings": [],
    }


def relation_overview(relation_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
    definition = relation_definition(relation_id, owner_id=owner_id)
    return {
        "relation": definition,
        "source": {
            "objectTypeId": definition["sourceTypeId"],
            "bound": True,
            "status": "ACTIVE",
            "currentIndexVersionId": str(uuid.uuid4()),
            "activeDatasetId": str(uuid.uuid4()),
        },
        "target": {
            "objectTypeId": definition["targetTypeId"],
            "bound": False,
            "status": None,
            "currentIndexVersionId": None,
            "activeDatasetId": None,
        },
        "warnings": validation()["warnings"],
        "latestVersion": {
            "id": str(uuid.uuid4()),
            "relationKind": "MANY_TO_MANY",
            "status": "ACTIVE",
            "rowCount": 12,
            "skippedCount": 2,
            "runId": str(uuid.uuid4()),
            "activatedAt": "2026-10-23T10:00:03Z",
        },
        "latestRun": recent_run(),
    }


__all__ = [
    "api_client",
    "create_object_type",
    "create_relation_with_types",
    "mapping_save",
    "object_binding",
    "object_detail",
    "object_preview",
    "owner_id",
    "relation_binding",
    "relation_definition",
    "relation_group",
    "relation_overview",
    "relation_preview",
]
