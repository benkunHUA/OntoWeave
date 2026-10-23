"""对象数据、关系浏览与运行记录查询。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Property, Relation
from app.services.object_type import get_object_type
from app.services.ontology.common import (
    DIRECTION_LABELS,
    OBJECT_TYPE,
    RELATION,
    require_subject,
    resource_subject,
)
from app.services.ontology.relation_sync import sync_subject_relations_best_effort


def get_run(client: Any, *, owner_id: uuid.UUID, run_id: uuid.UUID) -> dict[str, Any]:
    return client.get_run(run_id, owner_id=owner_id)


def cancel_run(
    client: Any,
    *,
    owner_id: uuid.UUID,
    run_id: uuid.UUID,
    operation_id: uuid.UUID | None = None,
) -> dict[str, Any]:
    return client.cancel_run(run_id, owner_id=owner_id, operation_id=operation_id)


def list_objects(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    object_type_id: uuid.UUID,
    page: int,
    page_size: int,
    q: str | None,
    filters: list[dict[str, Any]] | None,
) -> dict[str, Any]:
    resource_subject(
        session, owner_id=owner_id, resource_kind=OBJECT_TYPE, resource_id=object_type_id
    )
    return client.list_objects(
        object_type_id,
        owner_id=owner_id,
        page=page,
        page_size=page_size,
        q=q,
        filters=filters,
    )


def get_object(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    object_type_id: uuid.UUID,
    primary_key: str,
) -> dict[str, Any]:
    resource_subject(
        session, owner_id=owner_id, resource_kind=OBJECT_TYPE, resource_id=object_type_id
    )
    detail = client.get_object(object_type_id, owner_id=owner_id, primary_key=primary_key)
    return merge_object_detail(session, object_type_id=object_type_id, detail=detail)


def merge_object_detail(
    session: Session, *, object_type_id: uuid.UUID, detail: dict[str, Any]
) -> dict[str, Any]:
    """按本体定义顺序输出全部属性；未映射或缺失值补 null。"""
    raw = detail.get("properties")
    values: dict[str, Any] = {}
    if isinstance(raw, dict):
        values = {str(key): value for key, value in raw.items()}
        values = {
            str(key): value.get("value") if isinstance(value, dict) and "value" in value else value
            for key, value in values.items()
        }
    elif isinstance(raw, list):
        for item in raw:
            if not isinstance(item, dict):
                continue
            attribute_id = item.get("attribute_id") or item.get("attributeId")
            if attribute_id is not None:
                values[str(attribute_id)] = item.get("value")
    merged: dict[str, Any] = {}
    rows = list(
        session.scalars(
            select(Property)
            .where(Property.owner_type_id == object_type_id)
            .order_by(Property.name, Property.id)
        )
    )
    for prop in rows:
        merged[str(prop.id)] = values.get(str(prop.id))
    return {**detail, "properties": merged}


def relation_groups(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    object_type_id: uuid.UUID,
    primary_key: str,
    direction: str = "both",
) -> list[dict[str, Any]]:
    resource_subject(
        session, owner_id=owner_id, resource_kind=OBJECT_TYPE, resource_id=object_type_id
    )
    groups = client.list_object_relation_groups(
        object_type_id, owner_id=owner_id, primary_key=primary_key, direction=direction
    )
    return [decorate_group(session, group) for group in groups]


def related_objects(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    object_type_id: uuid.UUID,
    relation_id: uuid.UUID,
    primary_key: str,
    direction: str,
    page: int,
    page_size: int,
) -> dict[str, Any]:
    resource_subject(
        session, owner_id=owner_id, resource_kind=OBJECT_TYPE, resource_id=object_type_id
    )
    return client.list_object_relations(
        object_type_id,
        relation_id,
        owner_id=owner_id,
        primary_key=primary_key,
        direction=direction,
        page=page,
        page_size=page_size,
    )


def list_relation_instances(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    relation_id: uuid.UUID,
    page: int,
    page_size: int,
    from_primary_key: str | None,
    to_primary_key: str | None,
) -> dict[str, Any]:
    resource_subject(session, owner_id=owner_id, resource_kind=RELATION, resource_id=relation_id)
    return client.list_relation_instances(
        relation_id,
        owner_id=owner_id,
        page=page,
        page_size=page_size,
        from_primary_key=from_primary_key,
        to_primary_key=to_primary_key,
    )


def get_relation_overview(
    session: Session, client: Any, *, owner_id: uuid.UUID, relation_id: uuid.UUID
) -> dict[str, Any]:
    require_subject(session, owner_id, RELATION, relation_id)
    sync_subject_relations_best_effort(
        session, client, owner_id=owner_id, subject_kind=RELATION, subject_id=relation_id
    )
    raw = client.get_relation_overview(relation_id, owner_id=owner_id)
    return normalize_relation_overview(raw, relation_id=relation_id)


def normalize_relation_overview(raw: dict[str, Any], *, relation_id: uuid.UUID) -> dict[str, Any]:
    """把 W1-01 嵌套 RelationOverviewResponse 压平为 BFF 稳定字段。"""
    relation_value = raw.get("relation")
    relation: dict[str, Any] = relation_value if isinstance(relation_value, dict) else {}
    source = raw.get("source") if isinstance(raw.get("source"), dict) else None
    target = raw.get("target") if isinstance(raw.get("target"), dict) else None
    warnings = raw.get("warnings")
    if warnings is None:
        warnings = relation.get("warnings") or []
    latest_version = raw.get("latest_version", raw.get("latestVersion"))
    latest_run = raw.get("latest_run", raw.get("latestRun"))
    cardinality = relation.get("cardinality", raw.get("cardinality"))
    return {
        **raw,
        "relation_id": relation.get("relation_id", raw.get("relation_id", relation_id)),
        "relation_name": relation.get("name", raw.get("relation_name")),
        "relation_kind": _relation_kind(cardinality, raw.get("relation_kind")),
        "source_type_id": relation.get("source_type_id", raw.get("source_type_id")),
        "target_type_id": relation.get("target_type_id", raw.get("target_type_id")),
        "foreign_key_property_id": relation.get(
            "foreign_key_property_id", raw.get("foreign_key_property_id")
        ),
        "warnings": warnings,
        "latest_version": latest_version,
        "latest_run": latest_run,
        "source": source,
        "target": target,
    }


def decorate_group(session: Session, group: dict[str, Any]) -> dict[str, Any]:
    relation_id = group.get("relation_id")
    relation = session.get(Relation, relation_id) if relation_id else None
    direction = _direction(group.get("direction"))
    source = get_object_type(session, relation.source_type_id) if relation else None
    target = get_object_type(session, relation.target_type_id) if relation else None
    label = group.get("label")
    if relation is not None:
        if direction == "incoming":
            label = relation.reverse_label or relation.name
        elif direction == "outgoing":
            label = relation.forward_label or relation.name
        else:
            label = relation.name
    items = (
        group.get("items")
        or group.get("objects")
        or group.get("counterparts")
        or group.get("related_objects")
        or []
    )
    if not label:
        label = group.get("name")
    return {
        **group,
        "relation_id": relation_id,
        "direction": direction,
        "direction_label": DIRECTION_LABELS[direction],
        "label": label,
        "source_type_id": relation.source_type_id if relation else group.get("source_type_id"),
        "source_type_name": source.name if source else group.get("source_type_name"),
        "target_type_id": relation.target_type_id if relation else group.get("target_type_id"),
        "target_type_name": target.name if target else group.get("target_type_name"),
        "total": group.get("total", len(items)),
        "items": items,
    }


def _direction(value: Any) -> str:
    normalized = str(value or "both").strip().lower()
    if normalized in {"outgoing", "out", "from", "forward"}:
        return "outgoing"
    if normalized in {"incoming", "in", "to", "reverse"}:
        return "incoming"
    return "both"


def _relation_kind(cardinality: Any, fallback: Any) -> Any:
    if fallback:
        return fallback
    normalized = str(cardinality or "").lower()
    if normalized in {"many_to_many", "many-to-many", "manytomany"}:
        return "MANY_TO_MANY"
    if normalized:
        return "FK"
    return None


__all__ = [
    "cancel_run",
    "decorate_group",
    "get_object",
    "get_relation_overview",
    "get_run",
    "list_objects",
    "list_relation_instances",
    "merge_object_detail",
    "normalize_relation_overview",
    "related_objects",
    "relation_groups",
]
