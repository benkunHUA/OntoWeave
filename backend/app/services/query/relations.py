"""get_related_objects：按关系批量取对端对象引用。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.errors import InvalidRequestError, NotFoundError
from app.models import GraphRelation, Relation
from app.schemas.query import (
    QueryDirection,
    QueryStatus,
    RelatedObjectItem,
    RelatedObjectsInput,
    RelatedObjectsResult,
)


def get_related_objects(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    graph_id: uuid.UUID,
    payload: RelatedObjectsInput,
) -> RelatedObjectsResult:
    relation_id = uuid.UUID(payload.relation_id)
    relation = session.get(Relation, relation_id)
    if relation is None:
        raise NotFoundError(f"relation {relation_id} not found")
    if session.get(GraphRelation, (graph_id, relation_id)) is None:
        raise InvalidRequestError("relation is not in the selected graph")

    target_type_id, input_type_id = _types(relation, payload.direction)
    _validate_inputs(payload, input_type_id)
    raw = client.related_objects(
        relation_id,
        owner_id=owner_id,
        direction=payload.direction.value,
        inputs=[
            {
                "object_type_id": item.object_type_id,
                "primary_key": item.primary_key,
            }
            for item in payload.inputs
        ],
        limit=payload.limit,
    )
    version_id = raw.get("index_version_id")
    if version_id is None:
        return RelatedObjectsResult(
            relation_id=relation_id,
            target_type_id=target_type_id,
            status=QueryStatus.NOT_INDEXED,
        )
    items = _items(raw, target_type_id)
    total = int(raw.get("total") or 0)
    truncated = bool(raw.get("truncated")) or total > len(items)
    status = (
        QueryStatus.EMPTY if not items else QueryStatus.TRUNCATED if truncated else QueryStatus.OK
    )
    return RelatedObjectsResult(
        relation_id=relation_id,
        target_type_id=target_type_id,
        index_version_id=version_id,
        items=items,
        total=total,
        status=status,
        truncated=truncated,
    )


def _types(relation: Relation, direction: QueryDirection) -> tuple[uuid.UUID, uuid.UUID]:
    if direction is QueryDirection.OUTGOING:
        return relation.target_type_id, relation.source_type_id
    return relation.source_type_id, relation.target_type_id


def _validate_inputs(payload: RelatedObjectsInput, expected: uuid.UUID) -> None:
    for item in payload.inputs:
        try:
            actual = uuid.UUID(item.object_type_id)
        except ValueError:
            raise InvalidRequestError("relation input contains an invalid object type") from None
        if actual != expected:
            raise InvalidRequestError("relation input object type does not match relation endpoint")


def _items(raw: dict[str, Any], fallback_type_id: uuid.UUID) -> list[RelatedObjectItem]:
    rows = raw.get("items")
    if not isinstance(rows, list):
        return []
    items: list[RelatedObjectItem] = []
    seen: set[tuple[str, str]] = set()
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
        key = (str(object_type_id), primary_key)
        if key in seen:
            continue
        seen.add(key)
        items.append(
            RelatedObjectItem(
                object_type_id=object_type_id,
                primary_key=primary_key,
                title=row.get("title"),
            )
        )
    return items
