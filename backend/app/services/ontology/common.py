"""本体映射服务共享常量、资源定位与 DTO 摘要。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvalidRequestError, NotFoundError
from app.models import ObjectType, Property, Relation
from app.services.object_type import get_object_type
from app.services.relation import get_relation
from app.vocabulary import RelationCardinality

OBJECT_TYPE = "object_type"
RELATION = "relation"
DIRECTION_LABELS = {"outgoing": "出向", "incoming": "入向", "both": "双向"}


def normalize_resource_kind(value: str) -> str:
    normalized = value.strip().lower().replace("-", "_")
    if normalized in {OBJECT_TYPE, "objecttype", "object_types"}:
        return OBJECT_TYPE
    if normalized in {RELATION, "relation_type", "relation_types", "relations"}:
        return RELATION
    raise InvalidRequestError(f"unsupported resource kind: {value}")


def resource_subject(
    session: Session, *, owner_id: uuid.UUID, resource_kind: str, resource_id: uuid.UUID
) -> ObjectType | Relation:
    kind = normalize_resource_kind(resource_kind)
    if kind == OBJECT_TYPE:
        obj = get_object_type(session, resource_id)
        if obj.owner_id != owner_id:
            raise NotFoundError(f"object type {resource_id} not found")
        return obj
    rel = get_relation(session, resource_id)
    if rel.owner_id != owner_id:
        raise NotFoundError(f"relation {resource_id} not found")
    return rel


def require_subject(
    session: Session, owner_id: uuid.UUID, subject_kind: str, subject_id: uuid.UUID
) -> str:
    kind = normalize_resource_kind(subject_kind)
    resource_subject(session, owner_id=owner_id, resource_kind=kind, resource_id=subject_id)
    return kind


def object_brief(obj: ObjectType) -> dict[str, Any]:
    return {
        "id": obj.id,
        "name": obj.name,
        "description": obj.description,
        "status": obj.status,
    }


def properties(session: Session, object_type_id: uuid.UUID) -> list[dict[str, Any]]:
    rows = session.scalars(
        select(Property)
        .where(Property.owner_type_id == object_type_id)
        .order_by(Property.name, Property.id)
    )
    return [property_brief(prop) for prop in rows]


def property_brief(prop: Property) -> dict[str, Any]:
    return {
        "id": prop.id,
        "name": prop.name,
        "datatype": prop.datatype,
        "required": prop.required,
        "is_array": prop.is_array,
        "is_primary_key": prop.is_primary_key,
        "is_title_key": prop.is_title_key,
        "enum_values": prop.enum_values,
        "status": prop.status,
    }


def relation_brief(
    relation: Relation, *, source_name: str | None, target_name: str | None
) -> dict[str, Any]:
    return {
        "id": relation.id,
        "name": relation.name,
        "status": relation.status,
        "cardinality": relation.cardinality,
        "source_type_id": relation.source_type_id,
        "source_type_name": source_name,
        "target_type_id": relation.target_type_id,
        "target_type_name": target_name,
        "forward_label": relation.forward_label,
        "reverse_label": relation.reverse_label,
        "foreign_key_property_id": relation.foreign_key_property_id,
    }


def relation_resource(
    relation: Relation, *, source_name: str | None, target_name: str | None
) -> dict[str, Any]:
    forward = relation.forward_label or relation.name
    reverse = relation.reverse_label or relation.name
    source = source_name or str(relation.source_type_id)
    target = target_name or str(relation.target_type_id)
    return {
        "id": relation.id,
        "name": relation.name,
        "status": relation.status,
        "kind": RELATION,
        "cardinality": relation.cardinality,
        "relation_kind": (
            "MANY_TO_MANY" if relation.cardinality == RelationCardinality.MANY_TO_MANY else "FK"
        ),
        "source_type_id": relation.source_type_id,
        "source_type_name": source_name,
        "target_type_id": relation.target_type_id,
        "target_type_name": target_name,
        "forward_label": forward,
        "reverse_label": reverse,
        "direction_label": f"{source} --{forward}--> {target}",
        "foreign_key_property_id": relation.foreign_key_property_id,
        "binding": None,
    }


def primary_key_property(session: Session, object_type_id: uuid.UUID) -> Property | None:
    return session.scalar(
        select(Property).where(
            Property.owner_type_id == object_type_id, Property.is_primary_key.is_(True)
        )
    )


def mapping_value(row: dict[str, Any], snake: str, camel: str | None = None) -> Any:
    return row.get(snake, row.get(camel)) if camel else row.get(snake)


def camel_key(key: str) -> str:
    head, *tail = key.split("_")
    return head + "".join(part[:1].upper() + part[1:] for part in tail)


__all__ = [
    "DIRECTION_LABELS",
    "OBJECT_TYPE",
    "RELATION",
    "camel_key",
    "mapping_value",
    "normalize_resource_kind",
    "object_brief",
    "primary_key_property",
    "properties",
    "property_brief",
    "relation_brief",
    "relation_resource",
    "require_subject",
    "resource_subject",
]
