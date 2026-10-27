"""query_objects：对象实例的锚点搜索、条件列表与按主键读取。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.schemas.query import QueryItem, QueryObjectsInput, QueryObjectsResult, QueryStatus
from app.services import ontology
from app.services.query.common import (
    ensure_projectable,
    items_from_result,
    project_properties,
)


def query_objects(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    payload: QueryObjectsInput,
    step_id: str,
) -> QueryObjectsResult:
    object_type_id = uuid.UUID(payload.object_type_id)
    project_ids = ensure_projectable(session, object_type_id, payload.project)
    if payload.primary_key is not None:
        detail = _detail(session, client, owner_id, object_type_id, payload.primary_key)
        if detail is None:
            return QueryObjectsResult(
                step_id=step_id,
                object_type_id=object_type_id,
                status=QueryStatus.EMPTY,
            )
        item = _item(object_type_id, detail, [str(value) for value in project_ids])
        return QueryObjectsResult(
            step_id=step_id,
            object_type_id=object_type_id,
            index_version_id=detail.get("index_version_id"),
            items=[item],
            total=1,
            status=QueryStatus.OK,
        )

    raw = client.query_objects(
        object_type_id,
        owner_id=owner_id,
        q=payload.q,
        filters=[_filter_payload(item) for item in payload.filters],
        sort=[_sort_payload(item) for item in payload.sort],
        limit=payload.limit,
        project=project_ids,
    )
    version = raw.get("index_version_id")
    if version is None:
        return QueryObjectsResult(
            step_id=step_id,
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
        step_id=step_id,
        object_type_id=object_type_id,
        index_version_id=version,
        items=items,
        total=total,
        status=status,
        truncated=truncated,
    )


def _detail(
    session: Session,
    client: Any,
    owner_id: uuid.UUID,
    object_type_id: uuid.UUID,
    primary_key: str,
) -> dict[str, Any] | None:
    try:
        return ontology.get_object(
            session,
            client,
            owner_id=owner_id,
            object_type_id=object_type_id,
            primary_key=primary_key,
        )
    except NotFoundError:
        return None


def _item(object_type_id: uuid.UUID, detail: dict[str, Any], project: list[str]) -> QueryItem:
    return QueryItem(
        object_type_id=object_type_id,
        primary_key=str(detail.get("primary_key") or ""),
        title=detail.get("title"),
        properties=project_properties(detail, project),
    )


def _filter_payload(item: Any) -> dict[str, Any]:
    return {"attribute_id": item.property_id, "op": item.op.value, "value": item.value}


def _sort_payload(item: Any) -> dict[str, Any]:
    return {"attribute_id": item.property_id, "direction": item.direction.value}
