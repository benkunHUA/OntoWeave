"""query_objects：对象实例的搜索、条件列表与按主键批量读取。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.schemas.query import QueryObjectsInput, QueryObjectsResult, QueryStatus
from app.services.query.common import (
    ensure_projectable,
    items_from_result,
)


def query_objects(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    payload: QueryObjectsInput,
) -> QueryObjectsResult:
    object_type_id = uuid.UUID(payload.object_type_id)
    project_ids = ensure_projectable(session, object_type_id, payload.project)
    raw = client.query_objects(
        object_type_id,
        owner_id=owner_id,
        primary_keys=payload.primary_keys,
        q=payload.q,
        filters=[_filter_payload(item) for item in payload.filters],
        sort=[_sort_payload(item) for item in payload.sort],
        limit=payload.limit,
        project=project_ids,
    )
    version = raw.get("index_version_id")
    if version is None:
        return QueryObjectsResult(
            object_type_id=object_type_id,
            status=QueryStatus.NOT_INDEXED,
        )
    total = int(raw.get("total") or 0)
    items = items_from_result(raw, object_type_id)
    truncated = bool(raw.get("truncated")) or total > len(items)
    status = (
        QueryStatus.EMPTY if not items else QueryStatus.TRUNCATED if truncated else QueryStatus.OK
    )
    return QueryObjectsResult(
        object_type_id=object_type_id,
        index_version_id=version,
        items=items,
        total=total,
        status=status,
        truncated=truncated,
    )


def _filter_payload(item: Any) -> dict[str, Any]:
    return {"attribute_id": item.property_id, "op": item.op.value, "value": item.value}


def _sort_payload(item: Any) -> dict[str, Any]:
    return {"attribute_id": item.property_id, "direction": item.direction.value}
