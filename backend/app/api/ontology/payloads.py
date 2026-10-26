"""BFF 请求/响应的形状转换。"""

from __future__ import annotations

import json
from typing import Any

from app.api.ontology.deps import mapping_payload
from app.errors import UnprocessableEntityError
from app.schemas.ontology import (
    BindingDraftRequest,
    FilterCondition,
    IndexRun,
    ObjectPreview,
    PreviewBindingRequest,
    PreviewSkip,
    RelationPreview,
    ValidateBindingRequest,
)


def binding_payload(payload: BindingDraftRequest) -> dict:
    return {
        "base_revision": payload.base_revision,
        "dataset_kind": payload.dataset_kind.value,
        "dataset_id": str(payload.dataset_id),
        "mapping": mapping_payload(payload.mapping),
    }


def validation_payload(payload: ValidateBindingRequest | None) -> dict | None:
    if payload is None:
        return None
    result: dict = {}
    if payload.dataset_kind is not None:
        result["dataset_kind"] = payload.dataset_kind.value
    if payload.dataset_id is not None:
        result["dataset_id"] = str(payload.dataset_id)
    if payload.mapping is not None:
        result["mapping"] = mapping_payload(payload.mapping)
    return result


def preview_payload(payload: PreviewBindingRequest | None) -> dict | None:
    result = validation_payload(payload)
    if payload is not None:
        result = result or {}
        result["limit"] = payload.limit
    return result


def preview_model(result: dict, *, relation: bool = False) -> ObjectPreview | RelationPreview:
    skipped = [PreviewSkip.model_validate(item) for item in _skipped_rows(result.get("skipped"))]
    skipped_reasons: dict[str, int] = {}
    for item in skipped:
        skipped_reasons[item.code] = skipped_reasons.get(item.code, 0) + 1
    validation = result.get("validation")
    if not isinstance(validation, dict):
        validation = {
            "blocking": result.get("blocking") or [],
            "warnings": result.get("warnings") or [],
            "valid": result.get("valid", not result.get("blocking")),
        }
    body = {
        **result,
        "validation": validation,
        "skipped": skipped,
        "skipped_count": len(skipped),
        "skipped_reasons": skipped_reasons,
    }
    if relation:
        body["pairs"] = result.get("pairs") or result.get("sample") or []
    else:
        body["rows"] = result.get("rows") or result.get("sample") or []
    return RelationPreview.model_validate(body) if relation else ObjectPreview.model_validate(body)


def runs(rows: list[dict]) -> list[IndexRun]:
    # Java 在排队 / 运行阶段会把 stats 序列化为 null，浏览层按空对象展示。
    return [IndexRun.model_validate({**row, "stats": row.get("stats") or {}}) for row in rows]


def filter_payload(raw: str | None) -> list[dict] | None:
    if not raw:
        return None
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise UnprocessableEntityError(
            "filters must be a JSON array", code="FILTER_INVALID"
        ) from exc
    if not isinstance(value, list):
        raise UnprocessableEntityError("filters must be a JSON array", code="FILTER_INVALID")
    try:
        return [FilterCondition.model_validate(item).model_dump(mode="json") for item in value]
    except ValueError as exc:
        raise UnprocessableEntityError(
            "filters contain an invalid condition", code="FILTER_INVALID"
        ) from exc


def _skipped_rows(value: Any) -> list[dict[str, Any]]:
    if not isinstance(value, list):
        return []
    return [item for item in value if isinstance(item, dict)]


__all__ = [
    "binding_payload",
    "filter_payload",
    "preview_model",
    "preview_payload",
    "runs",
    "validation_payload",
]
