from __future__ import annotations

import hashlib
import math
import uuid
from collections.abc import Sequence
from typing import Protocol

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import EMBEDDING_DIM, ObjectType


class Embedder(Protocol):
    """嵌入模型的可替换接口。真实实现走外部 API，测试与开发用本地实现。"""

    dim: int

    def embed(self, texts: Sequence[str]) -> list[list[float]]: ...


def _tokenize(text: str) -> list[str]:
    """英文按词切、中文按字切。确定性、无依赖，够做词面相似度。"""
    tokens: list[str] = []
    buffer: list[str] = []
    for char in text.lower():
        if char.isascii() and char.isalnum():
            buffer.append(char)
            continue
        if buffer:
            tokens.append("".join(buffer))
            buffer = []
        if not char.isspace():
            tokens.append(char)
    if buffer:
        tokens.append("".join(buffer))
    return tokens


class HashingEmbedder:
    """把词哈希进固定维度再归一化。不依赖网络，用于开发和测试。"""

    def __init__(self, dim: int = EMBEDDING_DIM) -> None:
        self.dim = dim

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        return [self._embed_one(text) for text in texts]

    def _embed_one(self, text: str) -> list[float]:
        vector = [0.0] * self.dim
        for token in _tokenize(text):
            digest = hashlib.blake2b(token.encode("utf-8"), digest_size=8).digest()
            vector[int.from_bytes(digest, "big") % self.dim] += 1.0
        norm = math.sqrt(sum(value * value for value in vector))
        if norm == 0.0:
            return vector
        return [value / norm for value in vector]


def cosine(left: Sequence[float], right: Sequence[float]) -> float:
    dot = sum(a * b for a, b in zip(left, right, strict=True))
    left_norm = math.sqrt(sum(a * a for a in left))
    right_norm = math.sqrt(sum(b * b for b in right))
    if left_norm == 0.0 or right_norm == 0.0:
        return 0.0
    return dot / (left_norm * right_norm)


def _check_dim(embedder: Embedder) -> None:
    """列维度在 DDL 里写死了，嵌入器维度对不上就早点报清楚，别等数据库抛。"""
    if embedder.dim != EMBEDDING_DIM:
        raise InvariantViolationError(
            f"embedder dimension {embedder.dim} does not match the column dimension {EMBEDDING_DIM}"
        )


def save_embedding(
    session: Session, *, object_type_id: uuid.UUID, embedder: Embedder
) -> ObjectType:
    _check_dim(embedder)
    obj = session.get(ObjectType, object_type_id)
    if obj is None:
        raise NotFoundError(f"object type {object_type_id} not found")
    text = obj.name if not obj.description else f"{obj.name} {obj.description}"
    obj.embedding = embedder.embed([text])[0]
    session.flush()
    return obj


def search_object_types(
    session: Session,
    *,
    query: str,
    embedder: Embedder,
    owner_id: uuid.UUID | None = None,
    limit: int = 10,
    exclude_ids: Sequence[uuid.UUID] = (),
) -> list[tuple[ObjectType, float]]:
    """按语义远近返回候选 (对象, 相似度)，相似度是 1 - 余弦距离。"""
    _check_dim(embedder)
    vector = embedder.embed([query])[0]
    distance = ObjectType.embedding.cosine_distance(vector)
    stmt = select(ObjectType, distance.label("distance")).where(ObjectType.embedding.isnot(None))
    if owner_id is not None:
        stmt = stmt.where(ObjectType.owner_id == owner_id)
    if exclude_ids:
        stmt = stmt.where(ObjectType.id.notin_(list(exclude_ids)))
    stmt = stmt.order_by(distance).limit(limit)
    return [(row[0], 1.0 - float(row[1])) for row in session.execute(stmt)]
