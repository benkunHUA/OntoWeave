"""按当前本体定义召回可绑定数据集，并用确定性规则复核主键/必填/类型。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import DatasetSearchIndex
from app.services.dataset_search.documents import TEMPLATE_VERSION, embedding_model_name
from app.services.dataset_search.models import NEAR_MISS_LIMIT, RETRIEVAL_LIMIT
from app.services.dataset_search.scoring import blocked_reason, evaluate
from app.services.dataset_search.subject import load_subject_query
from app.services.ontology.matching import compatible_column_types


def find_bindable_datasets(
    session: Session,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    embedder: Any,
    limit: int = 10,
) -> dict[str, Any]:
    subject = load_subject_query(
        session,
        owner_id=owner_id,
        subject_kind=subject_kind,
        subject_id=subject_id,
    )
    vector = embedder.embed([subject.query_text])[0]
    safe_limit = max(1, min(limit, 20))
    allowed_types = sorted(
        {
            column_type
            for item in subject.requirements
            for column_type in compatible_column_types(item.datatype)
        }
    )
    rows = _vector_rows(
        session,
        owner_id=owner_id,
        embedder=embedder,
        vector=vector,
        allowed_types=allowed_types,
        limit=RETRIEVAL_LIMIT,
    )
    candidates = [
        evaluate(row, vector_similarity=similarity, subject=subject) for row, similarity in rows
    ]
    items = sorted(
        [item for item in candidates if item["status"] != "BLOCKED"],
        key=lambda item: (-item["score"], item["name"]),
    )[:safe_limit]

    near_misses: list[dict[str, Any]] = []
    if not items:
        seen = {(item["dataset_kind"], item["version_id"]) for item in candidates}
        near_misses.extend(
            {**item, "reason": blocked_reason(item)}
            for item in candidates
            if item["status"] == "BLOCKED"
        )
        for row, similarity in _vector_rows(
            session,
            owner_id=owner_id,
            embedder=embedder,
            vector=vector,
            allowed_types=[],
            limit=NEAR_MISS_LIMIT * 2,
        ):
            key = (row.dataset_kind, row.version_id)
            if key in seen or len(near_misses) >= NEAR_MISS_LIMIT:
                continue
            item = evaluate(row, vector_similarity=similarity, subject=subject)
            if item["status"] == "BLOCKED":
                near_misses.append({**item, "reason": blocked_reason(item)})
    result: dict[str, Any] = {
        "subject": {
            "kind": subject.subject_kind,
            "id": str(subject.subject_id),
            "name": subject.name,
            "name_en": subject.name_en,
        },
        "items": items,
        "near_misses": near_misses[:NEAR_MISS_LIMIT],
    }
    if not rows:
        result["message"] = "当前没有已建立向量索引的数据集；请等待发布事件完成或执行回填。"
    return result


def _vector_rows(
    session: Session,
    *,
    owner_id: uuid.UUID,
    embedder: Any,
    vector: list[float],
    allowed_types: list[str],
    limit: int,
) -> list[tuple[DatasetSearchIndex, float]]:
    distance = DatasetSearchIndex.embedding.cosine_distance(vector)
    stmt = select(DatasetSearchIndex, distance.label("distance")).where(
        DatasetSearchIndex.owner_id == owner_id,
        DatasetSearchIndex.is_current.is_(True),
        DatasetSearchIndex.status == "ready",
        DatasetSearchIndex.embedding.isnot(None),
        DatasetSearchIndex.embedding_model == embedding_model_name(embedder),
        DatasetSearchIndex.template_version == TEMPLATE_VERSION,
    )
    if allowed_types:
        stmt = stmt.where(DatasetSearchIndex.column_types.overlap(allowed_types))
    stmt = stmt.order_by(distance).limit(limit)
    return [(row[0], max(0.0, min(1.0, 1.0 - float(row[1])))) for row in session.execute(stmt)]


__all__ = ["find_bindable_datasets"]
