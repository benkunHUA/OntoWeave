from __future__ import annotations

import hashlib
import logging
import math
import uuid
from collections.abc import Sequence
from typing import Any, Protocol

import httpx
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import Settings
from app.errors import InvariantViolationError, NotFoundError
from app.models import EMBEDDING_DIM, ActionType, ObjectType
from app.services import status

logger = logging.getLogger(__name__)


class Embedder(Protocol):
    """嵌入模型的可替换接口。真实实现走外部 API，测试与开发用本地实现。"""

    dim: int

    def embed(self, texts: Sequence[str]) -> list[list[float]]: ...


def embedder_model_name(embedder: Embedder) -> str:
    model = getattr(embedder, "model", None)
    return str(model) if model else "local-hash"


def ensure_embedding_dim(embedder: Embedder) -> None:
    """列维度在 DDL 里写死了，嵌入器维度对不上就早点报清楚，别等数据库抛。"""
    if embedder.dim != EMBEDDING_DIM:
        raise InvariantViolationError(
            f"embedder dimension {embedder.dim} does not match the column dimension {EMBEDDING_DIM}"
        )


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


class ApiEmbedder:
    """OpenAI 兼容的 /embeddings 客户端。

    私有化环境把 base_url 指向自建网关即可，不需要改代码。
    """

    def __init__(
        self,
        *,
        base_url: str,
        api_key: str,
        model: str,
        dim: int = EMBEDDING_DIM,
        timeout: float = 30.0,
        send_dimensions: bool = True,
        client: httpx.Client | None = None,
    ) -> None:
        if not api_key:
            raise InvariantViolationError("embedding api key is not configured")
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.model = model
        self.dim = dim
        self.timeout = timeout
        self.send_dimensions = send_dimensions
        self._client = client

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        payload: dict = {"model": self.model, "input": list(texts)}
        if self.send_dimensions:
            payload["dimensions"] = self.dim
        client = self._client or httpx
        response = client.post(
            f"{self.base_url}/embeddings",
            headers={"Authorization": f"Bearer {self.api_key}"},
            json=payload,
            timeout=self.timeout,
        )
        response.raise_for_status()
        rows = sorted(response.json()["data"], key=lambda row: row["index"])
        vectors = [row["embedding"] for row in rows]
        for vector in vectors:
            if len(vector) != self.dim:
                raise InvariantViolationError(
                    f"embedding model returned {len(vector)} dimensions, expected "
                    f"{self.dim}; change EMBEDDING_MODEL or migrate the column"
                )
        return vectors


def build_embedder(settings: Settings) -> Embedder:
    """配了 key 就走真实模型，没配就回落到本地确定性实现，方便开发与测试。"""
    if settings.embedding_configured:
        return ApiEmbedder(
            base_url=settings.embedding_base_url,
            api_key=settings.embedding_api_key,
            model=settings.embedding_model,
            dim=settings.embedding_dim,
            timeout=settings.embedding_timeout_seconds,
            send_dimensions=settings.embedding_send_dimensions,
        )
    return HashingEmbedder(dim=settings.embedding_dim)


def cosine(left: Sequence[float], right: Sequence[float]) -> float:
    dot = sum(a * b for a, b in zip(left, right, strict=True))
    left_norm = math.sqrt(sum(a * a for a in left))
    right_norm = math.sqrt(sum(b * b for b in right))
    if left_norm == 0.0 or right_norm == 0.0:
        return 0.0
    return dot / (left_norm * right_norm)


def _check_dim(embedder: Embedder) -> None:
    ensure_embedding_dim(embedder)


def _embedding_text(element: Any) -> str:
    """检索文本只由名称 + 描述拼成，状态、归属、图都不参与。"""
    return element.name if not element.description else f"{element.name} {element.description}"


def _refresh_embedding(
    session: Session, *, model: Any, element_id: uuid.UUID, embedder: Embedder, kind: str
) -> Any:
    """让向量始终跟着确认状态走：确认/批准过的才有向量，草稿一律清空。

    这样检索命中的必然已确认，调用方不用再判状态；草稿也不会被检索到、被误复用。
    改动名称/描述后同样调它，把旧向量刷新掉。
    """
    _check_dim(embedder)
    element = session.get(model, element_id)
    if element is None:
        raise NotFoundError(f"{kind} {element_id} not found")
    if element.status == status.DRAFT:
        element.embedding = None
    else:
        element.embedding = embedder.embed([_embedding_text(element)])[0]
    session.flush()
    return element


def _try_refresh_embedding(
    session: Session, *, model: Any, element_id: uuid.UUID, embedder: Embedder, kind: str
) -> None:
    """尽力而为版：算向量失败不往上抛，只记日志。

    定稿（确认/撤销）和改名这类路径用。本体改动本身已经生效，不该因为嵌入服务
    抖动就整笔回滚；代价是这个元素暂时没向量、检索不到，等下次编辑或再次确认时补。
    """
    try:
        _refresh_embedding(
            session, model=model, element_id=element_id, embedder=embedder, kind=kind
        )
    except Exception:
        logger.warning(
            "%s %s embedding refresh failed; leaving it unembedded",
            kind,
            element_id,
            exc_info=True,
        )


def _search_embeddings(
    session: Session,
    *,
    model: Any,
    query: str,
    embedder: Embedder,
    owner_id: uuid.UUID | None = None,
    limit: int = 10,
    exclude_ids: Sequence[uuid.UUID] = (),
) -> list[tuple[Any, float]]:
    """按语义远近返回候选 (元素, 相似度)，相似度是 1 - 余弦距离。"""
    _check_dim(embedder)
    vector = embedder.embed([query])[0]
    distance = model.embedding.cosine_distance(vector)
    stmt = select(model, distance.label("distance")).where(model.embedding.isnot(None))
    if owner_id is not None:
        stmt = stmt.where(model.owner_id == owner_id)
    if exclude_ids:
        stmt = stmt.where(model.id.notin_(list(exclude_ids)))
    stmt = stmt.order_by(distance).limit(limit)
    return [(row[0], 1.0 - float(row[1])) for row in session.execute(stmt)]


def refresh_object_type_embedding(
    session: Session, *, object_type_id: uuid.UUID, embedder: Embedder
) -> ObjectType:
    return _refresh_embedding(
        session, model=ObjectType, element_id=object_type_id, embedder=embedder, kind="object type"
    )


def try_refresh_object_type_embedding(
    session: Session, *, object_type_id: uuid.UUID, embedder: Embedder
) -> None:
    _try_refresh_embedding(
        session, model=ObjectType, element_id=object_type_id, embedder=embedder, kind="object type"
    )


def search_object_types(
    session: Session,
    *,
    query: str,
    embedder: Embedder,
    owner_id: uuid.UUID | None = None,
    limit: int = 10,
    exclude_ids: Sequence[uuid.UUID] = (),
) -> list[tuple[ObjectType, float]]:
    return _search_embeddings(
        session,
        model=ObjectType,
        query=query,
        embedder=embedder,
        owner_id=owner_id,
        limit=limit,
        exclude_ids=exclude_ids,
    )


def refresh_action_type_embedding(
    session: Session, *, action_type_id: uuid.UUID, embedder: Embedder
) -> ActionType:
    """动作类型与对象类型同一条路径：确认过的才有向量，草稿一律清空。"""
    return _refresh_embedding(
        session, model=ActionType, element_id=action_type_id, embedder=embedder, kind="action type"
    )


def try_refresh_action_type_embedding(
    session: Session, *, action_type_id: uuid.UUID, embedder: Embedder
) -> None:
    _try_refresh_embedding(
        session, model=ActionType, element_id=action_type_id, embedder=embedder, kind="action type"
    )


def search_action_types(
    session: Session,
    *,
    query: str,
    embedder: Embedder,
    owner_id: uuid.UUID | None = None,
    limit: int = 10,
    exclude_ids: Sequence[uuid.UUID] = (),
) -> list[tuple[ActionType, float]]:
    return _search_embeddings(
        session,
        model=ActionType,
        query=query,
        embedder=embedder,
        owner_id=owner_id,
        limit=limit,
        exclude_ids=exclude_ids,
    )
