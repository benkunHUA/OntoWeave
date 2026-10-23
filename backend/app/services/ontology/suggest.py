"""Python 侧映射建议入口。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.errors import InvalidRequestError
from app.services.ontology.bindings import safe_binding, with_required_attributes
from app.services.ontology.common import OBJECT_TYPE, require_subject
from app.services.ontology.matching import suggest_object_mapping, suggest_relation_mapping


def suggest_mapping(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    dataset_kind: str | None = None,
    dataset_id: uuid.UUID | None = None,
    current_mapping: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """按名称相似度与类型兼容度给出建议；只填充空位，不覆盖已有兼容映射。"""
    kind = require_subject(session, owner_id, subject_kind, subject_id)
    dataset_kind, dataset_id = _suggest_dataset(
        client,
        owner_id=owner_id,
        subject_kind=kind,
        subject_id=subject_id,
        dataset_kind=dataset_kind,
        dataset_id=dataset_id,
    )
    columns = client.list_dataset_columns(
        owner_id=owner_id, dataset_kind=dataset_kind, dataset_id=dataset_id
    )
    if kind == OBJECT_TYPE:
        mapping, reasons = suggest_object_mapping(
            session,
            object_type_id=subject_id,
            columns=columns,
            current=current_mapping,
        )
        mapping = with_required_attributes(session, subject_id, mapping)
    else:
        mapping, reasons = suggest_relation_mapping(
            session, relation_id=subject_id, columns=columns, current=current_mapping
        )
    return {
        "dataset_kind": dataset_kind,
        "dataset_id": dataset_id,
        "mapping": mapping,
        "reasons": reasons,
    }


def _suggest_dataset(
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    dataset_kind: str | None,
    dataset_id: uuid.UUID | None,
) -> tuple[str, uuid.UUID]:
    if dataset_id is None:
        if dataset_kind is not None:
            raise InvalidRequestError("dataset_kind requires dataset_id")
        binding = safe_binding(
            client, owner_id=owner_id, subject_kind=subject_kind, subject_id=subject_id
        )
        if binding is None:
            raise InvalidRequestError("请先选择数据集")
        kind = binding.get("draft_dataset_kind") or binding.get("active_dataset_kind")
        identity = binding.get("draft_dataset_id") or binding.get("active_dataset_id")
        if not kind or not identity:
            raise InvalidRequestError("当前绑定没有可用数据集")
        return str(kind).upper(), uuid.UUID(str(identity))
    if dataset_kind is not None:
        return dataset_kind.upper(), dataset_id
    binding = safe_binding(
        client, owner_id=owner_id, subject_kind=subject_kind, subject_id=subject_id
    )
    kind = binding.get("draft_dataset_kind") if binding else None
    if not kind:
        raise InvalidRequestError("dataset_kind is required for an unbound subject")
    return str(kind).upper(), dataset_id


__all__ = ["suggest_mapping"]
