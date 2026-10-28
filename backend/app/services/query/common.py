"""查询结果的归属校验与结果归一。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvalidRequestError
from app.models import Property
from app.schemas.query import QueryItem


def ensure_projectable(
    session: Session, object_type_id: uuid.UUID, project: list[str]
) -> list[uuid.UUID]:
    """把 project 归一成属性 ID；兼容模型传属性名或英文名。"""
    if not project:
        return list(
            session.scalars(
                select(Property.id)
                .where(
                    Property.owner_type_id == object_type_id,
                    (Property.is_primary_key.is_(True)) | (Property.is_title_key.is_(True)),
                )
                .order_by(Property.name, Property.id)
            )
        )

    properties = list(
        session.scalars(select(Property).where(Property.owner_type_id == object_type_id))
    )
    by_id = {str(row.id): row.id for row in properties}
    by_name = {row.name: row.id for row in properties}
    by_name.update({row.name_en: row.id for row in properties if row.name_en})

    resolved: list[uuid.UUID] = []
    missing: list[str] = []
    for value in project:
        property_id = by_id.get(value) or by_name.get(value)
        if property_id is None:
            missing.append(value)
        else:
            resolved.append(property_id)
    if missing:
        raise InvalidRequestError("project contains properties outside the object type")
    return resolved


def items_from_result(raw: dict[str, Any], fallback_type_id: uuid.UUID) -> list[QueryItem]:
    rows = raw.get("items")
    if not isinstance(rows, list):
        return []
    items: list[QueryItem] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        primary_key = str(row.get("primary_key") or "")
        if not primary_key:
            continue
        try:
            object_type_id = uuid.UUID(str(row.get("object_type_id") or fallback_type_id))
        except ValueError:
            object_type_id = fallback_type_id
        items.append(
            QueryItem(
                object_type_id=object_type_id,
                primary_key=primary_key,
                title=row.get("title"),
                properties=_result_properties(row.get("properties")),
            )
        )
    return items


def _result_properties(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return {str(key): item for key, item in value.items()}
    if not isinstance(value, list):
        return {}
    properties: dict[str, Any] = {}
    for item in value:
        if not isinstance(item, dict):
            continue
        attribute_id = item.get("attribute_id") or item.get("attributeId")
        if attribute_id is not None:
            properties[str(attribute_id)] = item.get("value")
    return properties
