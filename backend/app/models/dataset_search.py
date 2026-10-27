from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any

from pgvector.sqlalchemy import Vector
from sqlalchemy import (
    BigInteger,
    Boolean,
    CheckConstraint,
    DateTime,
    Index,
    Integer,
    Text,
    func,
    text,
)
from sqlalchemy.dialects.postgresql import ARRAY, JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.constants import EMBEDDING_DIM
from app.models.base import Base


class DatasetSearchIndex(Base):
    """可绑定数据集的当前版本检索快照。

    事实数据留在 Java 侧；这张表只保存检索所需的名称、字段和向量，可由发布事件或
    回填脚本重建。主键用 (dataset_kind, version_id)，每个数据集只允许一行 current。
    """

    __tablename__ = "dataset_search_index"
    __table_args__ = (
        CheckConstraint(
            "dataset_kind IN ('SYNC', 'PIPELINE')",
            name="ck_dataset_search_index_kind",
        ),
        CheckConstraint(
            "status IN ('pending', 'ready', 'failed')",
            name="ck_dataset_search_index_status",
        ),
        Index(
            "uq_dataset_search_index_current",
            "dataset_kind",
            "dataset_id",
            unique=True,
            postgresql_where=text("is_current"),
        ),
        Index(
            "ix_dataset_search_index_owner_current",
            "owner_id",
            "is_current",
            "status",
        ),
        Index(
            "ix_dataset_search_index_column_types",
            "column_types",
            postgresql_using="gin",
        ),
        Index(
            "ix_dataset_search_index_embedding_hnsw",
            "embedding",
            postgresql_using="hnsw",
            postgresql_ops={"embedding": "vector_cosine_ops"},
            postgresql_where=text("is_current AND status = 'ready'"),
        ),
    )

    dataset_kind: Mapped[str] = mapped_column(Text, primary_key=True)
    version_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    dataset_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    owner_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    name: Mapped[str] = mapped_column(Text, nullable=False)
    version_number: Mapped[int] = mapped_column(Integer, nullable=False)
    row_count: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    columns: Mapped[list[dict[str, Any]]] = mapped_column(
        JSONB, nullable=False, default=list, server_default=text("'[]'::jsonb")
    )
    column_types: Mapped[list[str]] = mapped_column(
        ARRAY(Text), nullable=False, default=list, server_default=text("'{}'::text[]")
    )
    document_text: Mapped[str] = mapped_column(Text, nullable=False, default="")
    document_hash: Mapped[str] = mapped_column(Text, nullable=False, default="")
    embedding: Mapped[list[float] | None] = mapped_column(Vector(EMBEDDING_DIM))
    embedding_model: Mapped[str] = mapped_column(Text, nullable=False)
    template_version: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[str] = mapped_column(
        Text, nullable=False, default="pending", server_default="pending"
    )
    error_message: Mapped[str | None] = mapped_column(Text)
    is_current: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=True, server_default=text("true")
    )
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    embedded_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )
