"""execute_query_step：沿一条关系执行一步实例遍历。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.errors import InvalidRequestError, NotFoundError
from app.models import GraphRelation, Relation
from app.schemas.query import QueryDirection, QueryStatus, QueryStepInput, QueryStepResult
from app.services.query.common import ensure_projectable, items_from_result


def execute_query_step(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    graph_id: uuid.UUID,
    payload: QueryStepInput,
    input_items: list[dict[str, Any]],
    step_id: str,
) -> QueryStepResult:
    relation_id = uuid.UUID(payload.relation_id)
    relation = session.get(Relation, relation_id)
    if relation is None:
        raise NotFoundError(f"relation {relation_id} not found")
    if session.get(GraphRelation, (graph_id, relation_id)) is None:
        raise InvalidRequestError("relation is not in the selected graph")

    target_type_id, input_type_id = _types(relation, payload.direction)
    if not input_items:
        return QueryStepResult(
            step_id=step_id,
            target_type_id=target_type_id,
            status=QueryStatus.EMPTY,
        )
    _validate_input_type(input_items, input_type_id)
    project_ids = ensure_projectable(session, target_type_id, payload.project)
    raw = client.query_step(
        relation_id,
        owner_id=owner_id,
        direction=payload.direction.value,
        inputs=[
            {
                "object_type_id": str(item.get("object_type_id") or ""),
                "primary_key": str(item.get("primary_key") or ""),
            }
            for item in input_items
        ],
        filters=[_filter_payload(item) for item in payload.filters],
        sort=[_sort_payload(item) for item in payload.sort],
        limit=payload.limit,
        project=project_ids,
    )
    version_id = raw.get("index_version_id")
    if version_id is None:
        return QueryStepResult(
            step_id=step_id,
            target_type_id=target_type_id,
            status=QueryStatus.NOT_INDEXED,
        )
    items = items_from_result(raw, target_type_id)
    total = int(raw.get("total") or 0)
    truncated = bool(raw.get("truncated")) or total > len(items)
    if payload.expected_cardinality == "one" and total > 1:
        raise InvalidRequestError(
            f"relation step returned {total} objects but expected_cardinality is one"
        )
    status = (
        QueryStatus.EMPTY if not items else QueryStatus.TRUNCATED if truncated else QueryStatus.OK
    )
    return QueryStepResult(
        step_id=step_id,
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


def _validate_input_type(items: list[dict[str, Any]], expected: uuid.UUID) -> None:
    for item in items:
        raw = item.get("object_type_id")
        try:
            actual = uuid.UUID(str(raw))
        except (TypeError, ValueError):
            raise InvalidRequestError("input step contains an invalid object type") from None
        if actual != expected:
            raise InvalidRequestError("input step object type does not match relation endpoint")


def _filter_payload(item: Any) -> dict[str, Any]:
    return {"attribute_id": item.property_id, "op": item.op.value, "value": item.value}


def _sort_payload(item: Any) -> dict[str, Any]:
    return {"attribute_id": item.property_id, "direction": item.direction.value}
