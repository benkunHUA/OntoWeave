"""对象与关系的映射绑定读写。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import (
    DataServiceUnavailableError,
    InvalidRequestError,
    NotFoundError,
)
from app.models import Property
from app.services.ontology.common import OBJECT_TYPE, require_subject
from app.services.ontology.relation_sync import (
    sync_subject_relations,
    sync_subject_relations_best_effort,
)


def get_binding(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
) -> dict[str, Any]:
    kind = require_subject(session, owner_id, subject_kind, subject_id)
    sync_subject_relations_best_effort(
        session, client, owner_id=owner_id, subject_kind=kind, subject_id=subject_id
    )
    if kind == OBJECT_TYPE:
        raw = client.get_object_binding(subject_id, owner_id=owner_id)
    else:
        raw = client.get_relation_binding(subject_id, owner_id=owner_id)
    return normalize_binding(raw, subject_kind=kind, subject_id=subject_id)


def save_binding(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    payload: dict[str, Any],
    operation_id: uuid.UUID | None = None,
) -> dict[str, Any]:
    kind = require_subject(session, owner_id, subject_kind, subject_id)
    prepared = prepare_object_payload(
        session, subject_kind=kind, subject_id=subject_id, payload=payload
    )
    assert prepared is not None
    payload = prepared
    sync_subject_relations_best_effort(
        session, client, owner_id=owner_id, subject_kind=kind, subject_id=subject_id
    )
    if kind == OBJECT_TYPE:
        raw = client.put_object_binding(
            subject_id, owner_id=owner_id, payload=payload, operation_id=operation_id
        )
    else:
        raw = client.put_relation_binding(
            subject_id, owner_id=owner_id, payload=payload, operation_id=operation_id
        )
    return normalize_binding(raw, subject_kind=kind, subject_id=subject_id)


def delete_binding(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    operation_id: uuid.UUID | None = None,
) -> None:
    kind = require_subject(session, owner_id, subject_kind, subject_id)
    if kind == OBJECT_TYPE:
        client.delete_object_binding(subject_id, owner_id=owner_id, operation_id=operation_id)
    else:
        client.delete_relation_binding(subject_id, owner_id=owner_id, operation_id=operation_id)


def validate_binding(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    payload: dict[str, Any] | None = None,
) -> dict[str, Any]:
    kind = require_subject(session, owner_id, subject_kind, subject_id)
    if kind != OBJECT_TYPE:
        raise InvalidRequestError("relation bindings do not support validate")
    payload = prepare_object_payload(
        session, subject_kind=kind, subject_id=subject_id, payload=payload
    )
    return client.validate_object_binding(subject_id, owner_id=owner_id, payload=payload)


def preview_binding(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    payload: dict[str, Any] | None = None,
) -> dict[str, Any]:
    kind = require_subject(session, owner_id, subject_kind, subject_id)
    payload = prepare_object_payload(
        session, subject_kind=kind, subject_id=subject_id, payload=payload
    )
    sync_subject_relations_best_effort(
        session, client, owner_id=owner_id, subject_kind=kind, subject_id=subject_id
    )
    if kind == OBJECT_TYPE:
        return client.preview_object_binding(subject_id, owner_id=owner_id, payload=payload)
    return client.preview_relation_binding(subject_id, owner_id=owner_id, payload=payload)


def generate_binding(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    payload: dict[str, Any],
    operation_id: uuid.UUID | None = None,
) -> dict[str, Any]:
    kind = require_subject(session, owner_id, subject_kind, subject_id)
    prepared = prepare_object_payload(
        session, subject_kind=kind, subject_id=subject_id, payload=payload
    )
    assert prepared is not None
    payload = prepared
    try:
        sync_subject_relations(
            session, client, owner_id=owner_id, subject_kind=kind, subject_id=subject_id
        )
    except Exception as exc:
        raise DataServiceUnavailableError(f"关系定义同步失败，生成已取消：{exc}") from exc
    if kind == OBJECT_TYPE:
        return client.generate_object_binding(
            subject_id, owner_id=owner_id, payload=payload, operation_id=operation_id
        )
    return client.generate_relation_binding(
        subject_id, owner_id=owner_id, payload=payload, operation_id=operation_id
    )


def list_runs(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    limit: int = 20,
) -> list[dict[str, Any]]:
    kind = require_subject(session, owner_id, subject_kind, subject_id)
    if kind == OBJECT_TYPE:
        return client.list_object_runs(subject_id, owner_id=owner_id, limit=limit)
    return client.list_relation_runs(subject_id, owner_id=owner_id, limit=limit)


def safe_binding(
    client: Any, *, owner_id: uuid.UUID, subject_kind: str, subject_id: uuid.UUID
) -> dict[str, Any] | None:
    try:
        if subject_kind == OBJECT_TYPE:
            raw = client.get_object_binding(subject_id, owner_id=owner_id)
        else:
            raw = client.get_relation_binding(subject_id, owner_id=owner_id)
    except (NotFoundError, DataServiceUnavailableError):
        return None
    return normalize_binding(raw, subject_kind=subject_kind, subject_id=subject_id)


def normalize_binding(
    raw: dict[str, Any], *, subject_kind: str, subject_id: uuid.UUID
) -> dict[str, Any]:
    """把 GET Object/RelationBindingResponse 与 PUT MappingSaveResponse 统一成浏览器形状。"""
    data = dict(raw)
    binding_id = data.get("binding_id") or data.get("bindingId") or data.get("id")
    if binding_id is not None:
        data.setdefault("binding_id", binding_id)
        data.setdefault("id", binding_id)
    if subject_kind == OBJECT_TYPE:
        data.setdefault("object_type_id", str(subject_id))
    else:
        data.setdefault("relation_id", str(subject_id))
    if "draft_mapping" not in data and isinstance(data.get("mapping"), dict):
        data["draft_mapping"] = data["mapping"]
    recent_run = data.get("recent_run") or data.get("recentRun")
    if recent_run is not None:
        data.setdefault("recent_run", recent_run)
        data.setdefault("latest_run", recent_run)
    return data


def prepare_object_payload(
    session: Session,
    *,
    subject_kind: str,
    subject_id: uuid.UUID,
    payload: dict[str, Any] | None,
) -> dict[str, Any] | None:
    """对象映射写入 Java 前补齐当前本体定义中的全部必填属性。"""
    if subject_kind != OBJECT_TYPE or not isinstance(payload, dict):
        return payload
    mapping = payload.get("mapping")
    if not isinstance(mapping, dict):
        return payload
    return {
        **payload,
        "mapping": with_required_attributes(session, subject_id, mapping),
    }


def with_required_attributes(
    session: Session, object_type_id: uuid.UUID, mapping: dict[str, Any]
) -> dict[str, Any]:
    required_ids = session.scalars(
        select(Property.id)
        .where(
            Property.owner_type_id == object_type_id,
            Property.required.is_(True),
        )
        .order_by(Property.name, Property.id)
    )
    return {**mapping, "required_attribute_ids": [str(value) for value in required_ids]}


__all__ = [
    "delete_binding",
    "generate_binding",
    "get_binding",
    "list_runs",
    "normalize_binding",
    "prepare_object_payload",
    "preview_binding",
    "safe_binding",
    "save_binding",
    "validate_binding",
    "with_required_attributes",
]
