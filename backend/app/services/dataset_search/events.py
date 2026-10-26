"""从数据发布事件增量维护数据集搜索索引。"""

from __future__ import annotations

import logging
import uuid
from collections.abc import Callable
from typing import Any

from sqlalchemy.orm import Session

from app.services.dataset_search.index import index_dataset_version

logger = logging.getLogger(__name__)

SessionFactory = Callable[[], Session]
SYNC_EVENT = "dataset.version.published"
PIPELINE_EVENT = "pipeline.build.succeeded"


def consume_published_event(
    factory: SessionFactory,
    envelope: dict[str, Any],
    *,
    client: Any,
    embedder: Any,
) -> int:
    """只处理两个发布事件；单条索引失败只记日志，不影响事件消费与后续版本。"""
    event_type = envelope.get("eventType")
    payload = envelope.get("payload")
    if not isinstance(payload, dict):
        return 0
    refs = _references(event_type, payload)
    indexed = 0
    for kind, dataset_id, version_id in refs:
        try:
            with factory() as session:
                index_dataset_version(
                    session,
                    client=client,
                    embedder=embedder,
                    dataset_kind=kind,
                    dataset_id=dataset_id,
                    version_id=version_id,
                )
                session.commit()
            indexed += 1
        except Exception:
            logger.warning(
                "dataset search index failed for %s %s version %s",
                kind,
                dataset_id,
                version_id,
                exc_info=True,
            )
    return indexed


def _references(event_type: Any, payload: dict[str, Any]) -> list[tuple[str, uuid.UUID, uuid.UUID]]:
    if event_type == SYNC_EVENT:
        dataset_id = _uuid(payload.get("datasetId"))
        version_id = _uuid(payload.get("versionId"))
        if dataset_id is None or version_id is None:
            return []
        return [("SYNC", dataset_id, version_id)]
    if event_type == PIPELINE_EVENT:
        refs: list[tuple[str, uuid.UUID, uuid.UUID]] = []
        outputs = payload.get("outputs")
        if not isinstance(outputs, list):
            return refs
        for output in outputs:
            if not isinstance(output, dict) or output.get("datasetId") is None:
                continue
            dataset_id = _uuid(output.get("datasetId"))
            version_id = _uuid(output.get("datasetVersionId"))
            if dataset_id is not None and version_id is not None:
                refs.append(("PIPELINE", dataset_id, version_id))
        return refs
    return []


def _uuid(value: Any) -> uuid.UUID | None:
    if value in (None, ""):
        return None
    try:
        return uuid.UUID(str(value))
    except (TypeError, ValueError):
        return None


__all__ = ["PIPELINE_EVENT", "SYNC_EVENT", "consume_published_event"]
