"""数据集检索文档的规范化、拼接与指纹。"""

from __future__ import annotations

import hashlib
import json
from collections.abc import Mapping, Sequence
from typing import Any

from app.models import EMBEDDING_DIM

TEMPLATE_VERSION = "dataset-v1"


def normalize_columns(columns: Sequence[Mapping[str, Any]]) -> list[dict[str, Any]]:
    normalized: list[dict[str, Any]] = []
    for column in columns:
        name = str(column.get("name") or "").strip()
        if not name:
            continue
        normalized.append(
            {
                "name": name,
                "type": str(column.get("type") or "unknown").strip().lower(),
                "optional": bool(column.get("optional", False)),
            }
        )
    return normalized


def column_types(columns: Sequence[Mapping[str, Any]]) -> list[str]:
    return sorted({str(column["type"]).split("(", 1)[0].strip() for column in columns})


def render_document(*, name: str, version_number: int, columns: Sequence[Mapping[str, Any]]) -> str:
    lines = [f"数据集：{name}", f"版本：{version_number}", "字段："]
    for column in columns:
        requirement = "可空" if column.get("optional") else "必填"
        lines.append(f"- {column['name']} ({column['type']}, {requirement})")
    return "\n".join(lines)


def document_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def embedding_model_name(embedder: Any) -> str:
    model = getattr(embedder, "model", None)
    return str(model) if model else "hashing"


def ensure_embedding_dim(embedder: Any) -> None:
    if getattr(embedder, "dim", None) != EMBEDDING_DIM:
        raise ValueError(
            f"embedder dimension {getattr(embedder, 'dim', None)} does not match "
            f"dataset_search_index.embedding dimension {EMBEDDING_DIM}"
        )


def compact_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


__all__ = [
    "TEMPLATE_VERSION",
    "column_types",
    "compact_json",
    "document_hash",
    "embedding_model_name",
    "ensure_embedding_dim",
    "normalize_columns",
    "render_document",
]
