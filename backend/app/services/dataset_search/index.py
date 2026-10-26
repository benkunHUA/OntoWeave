"""把 Java 已发布数据集的当前版本写入 pgvector 搜索索引。"""

from __future__ import annotations

import logging
import uuid
from datetime import datetime
from typing import Any

from sqlalchemy import update
from sqlalchemy.orm import Session

from app.models import DatasetSearchIndex
from app.services.dataset_search.documents import (
    TEMPLATE_VERSION,
    column_types,
    document_hash,
    embedding_model_name,
    ensure_embedding_dim,
    normalize_columns,
    render_document,
)

logger = logging.getLogger(__name__)


def normalize_dataset_kind(value: str) -> str:
    kind = value.strip().upper()
    if kind not in {"SYNC", "PIPELINE"}:
        raise ValueError(f"unsupported dataset kind: {value}")
    return kind


def index_dataset_version(
    session: Session,
    *,
    client: Any,
    embedder: Any,
    dataset_kind: str,
    dataset_id: uuid.UUID,
    version_id: uuid.UUID,
) -> DatasetSearchIndex:
    """幂等写入一个版本；同一数据集只保留一行 current。"""
    kind = normalize_dataset_kind(dataset_kind)
    ensure_embedding_dim(embedder)
    detail = client.get_bindable_dataset(
        dataset_kind=kind,
        dataset_id=dataset_id,
        version_id=version_id,
    )
    _validate_detail(detail, kind=kind, dataset_id=dataset_id, version_id=version_id)

    columns = normalize_columns(detail.get("columns") or [])
    types = column_types(columns)
    text = render_document(
        name=str(detail["name"]), version_number=int(detail["version_number"]), columns=columns
    )
    digest = document_hash(text)
    model = embedding_model_name(embedder)
    owner_id = _uuid(detail.get("owner_id"))
    published_at = _datetime(detail.get("published_at"))

    _clear_other_current(session, kind=kind, dataset_id=dataset_id, version_id=version_id)
    row = session.get(
        DatasetSearchIndex,
        {"dataset_kind": kind, "version_id": version_id},
    )
    if row is None:
        row = DatasetSearchIndex(
            dataset_kind=kind,
            version_id=version_id,
            dataset_id=dataset_id,
            owner_id=owner_id,
            name=str(detail["name"]),
            version_number=int(detail["version_number"]),
            row_count=int(detail.get("row_count") or 0),
            columns=columns,
            column_types=types,
            document_text=text,
            document_hash=digest,
            embedding_model=model,
            template_version=TEMPLATE_VERSION,
            status="pending",
            is_current=True,
        )
        session.add(row)
    else:
        row.dataset_id = dataset_id
        row.owner_id = owner_id
        row.name = str(detail["name"])
        row.version_number = int(detail["version_number"])
        row.row_count = int(detail.get("row_count") or 0)
        row.columns = columns
        row.column_types = types
        row.document_text = text
        row.document_hash = digest
        row.embedding_model = model
        row.template_version = TEMPLATE_VERSION
        row.is_current = True
    row.published_at = published_at
    row.error_message = None
    if not _reusable(row, digest=digest, model=model):
        row.status = "pending"
        row.embedding = None
        row.embedded_at = None
    session.flush()

    if row.status == "ready":
        return row
    try:
        row.embedding = embedder.embed([text])[0]
    except Exception as exc:
        row.status = "failed"
        row.error_message = str(exc)[:2000]
        logger.warning("dataset search embedding failed for %s %s", kind, version_id, exc_info=True)
        session.flush()
        return row
    row.status = "ready"
    row.embedded_at = datetime.now().astimezone()
    session.flush()
    return row


def backfill_owner(
    session: Session,
    *,
    client: Any,
    embedder: Any,
    owner_id: uuid.UUID,
    page_size: int = 100,
) -> int:
    """历史数据回填：按 owner 分页列出可绑定数据集，再逐个写索引。"""
    page = 1
    count = 0
    while True:
        result = client.list_bindable_datasets(owner_id=owner_id, page=page, size=page_size)
        items = result.get("items") or []
        for item in items:
            index_dataset_version(
                session,
                client=client,
                embedder=embedder,
                dataset_kind=str(item["kind"]),
                dataset_id=_uuid(item["id"]),
                version_id=_uuid(item["version_id"]),
            )
            count += 1
        if len(items) < page_size:
            break
        page += 1
    return count


def _reusable(row: DatasetSearchIndex, *, digest: str, model: str) -> bool:
    return (
        row.status == "ready"
        and row.embedding is not None
        and row.document_hash == digest
        and row.embedding_model == model
        and row.template_version == TEMPLATE_VERSION
    )


def _clear_other_current(
    session: Session, *, kind: str, dataset_id: uuid.UUID, version_id: uuid.UUID
) -> None:
    session.execute(
        update(DatasetSearchIndex)
        .where(
            DatasetSearchIndex.dataset_kind == kind,
            DatasetSearchIndex.dataset_id == dataset_id,
            DatasetSearchIndex.version_id != version_id,
            DatasetSearchIndex.is_current.is_(True),
        )
        .values(is_current=False)
    )


def _validate_detail(
    detail: dict[str, Any],
    *,
    kind: str,
    dataset_id: uuid.UUID,
    version_id: uuid.UUID,
) -> None:
    if str(detail.get("kind") or "").upper() != kind:
        raise ValueError("dataset detail kind does not match the published event")
    if _uuid(detail.get("id")) != dataset_id:
        raise ValueError("dataset detail id does not match the published event")
    if _uuid(detail.get("version_id")) != version_id:
        raise ValueError("dataset detail version is no longer current")
    if not detail.get("name") or detail.get("version_number") is None:
        raise ValueError("dataset detail is missing name or version number")
    _uuid(detail.get("owner_id"))


def _uuid(value: Any) -> uuid.UUID:
    return value if isinstance(value, uuid.UUID) else uuid.UUID(str(value))


def _datetime(value: Any) -> datetime | None:
    if value in (None, ""):
        return None
    if isinstance(value, datetime):
        return value
    text = str(value).replace("Z", "+00:00")
    return datetime.fromisoformat(text)


__all__ = ["backfill_owner", "index_dataset_version", "normalize_dataset_kind"]
