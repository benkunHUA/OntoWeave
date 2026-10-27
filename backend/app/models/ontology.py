from __future__ import annotations

import uuid
from datetime import datetime

from pgvector.sqlalchemy import Vector
from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Text,
    UniqueConstraint,
    func,
    text,
)
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.constants import EMBEDDING_DIM
from app.models.base import Base
from app.vocabulary import (
    DEFAULT_CARDINALITY,
    DEFAULT_DATATYPE,
    PROPERTY_DATATYPES,
    RELATION_CARDINALITIES,
    sql_in_list,
)


class ObjectType(Base):
    __tablename__ = "object_type"
    __table_args__ = (
        CheckConstraint(
            "status IN ('draft', 'confirmed', 'approved')",
            name="ck_object_type_status",
        ),
        Index(
            "ix_object_type_embedding_hnsw",
            "embedding",
            postgresql_using="hnsw",
            postgresql_ops={"embedding": "vector_cosine_ops"},
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    owner_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False, index=True)
    name: Mapped[str] = mapped_column(Text, nullable=False)
    # 只生成一次：创建时补齐，之后改中文名不回写。用户可手工修改。
    name_en: Mapped[str | None] = mapped_column(Text)
    description: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(
        Text, nullable=False, default="draft", server_default="draft"
    )
    home_graph_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("graph.id", ondelete="SET NULL")
    )
    embedding: Mapped[list[float] | None] = mapped_column(Vector(EMBEDDING_DIM), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )


class Property(Base):
    __tablename__ = "property"
    __table_args__ = (
        CheckConstraint(
            "status IN ('draft', 'confirmed', 'approved')",
            name="ck_property_status",
        ),
        # datatype 是受控枚举，非法值在库层就挡掉。
        CheckConstraint(
            sql_in_list("datatype", PROPERTY_DATATYPES),
            name="ck_property_datatype",
        ),
        # enum 类型必须带候选值；其余类型不允许留一份用不上的枚举。
        CheckConstraint(
            "datatype <> 'enum' OR enum_values IS NOT NULL",
            name="ck_property_enum_values",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    owner_type_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("object_type.id", ondelete="CASCADE"),
        nullable=False,
    )
    name: Mapped[str] = mapped_column(Text, nullable=False)
    # 与对象英文名同一规则：为空时才批量生成，已有值永不被自动覆盖。
    name_en: Mapped[str | None] = mapped_column(Text)
    datatype: Mapped[str] = mapped_column(
        Text, nullable=False, default=DEFAULT_DATATYPE, server_default=DEFAULT_DATATYPE
    )
    required: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default=text("false")
    )
    # 多值属性（数组）。
    is_array: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default=text("false")
    )
    # 每个对象类型至多一个主键 / 标题键，唯一性由服务层保证。
    is_primary_key: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default=text("false")
    )
    is_title_key: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default=text("false")
    )
    # datatype = enum 时的候选值（有序、不含重复）；其余类型为 None。
    enum_values: Mapped[list[str] | None] = mapped_column(JSONB)
    status: Mapped[str] = mapped_column(
        Text, nullable=False, default="draft", server_default="draft"
    )


class Relation(Base):
    __tablename__ = "relation"
    __table_args__ = (
        CheckConstraint(
            "status IN ('draft', 'confirmed', 'approved')",
            name="ck_relation_status",
        ),
        CheckConstraint(
            sql_in_list("cardinality", RELATION_CARDINALITIES),
            name="ck_relation_cardinality",
        ),
        # 同一对类型之间、同名同向的关系只保留一条。自反关系（公司—控股→公司）
        # 是合法的，所以这里不做 source != target 限制。
        UniqueConstraint(
            "source_type_id",
            "target_type_id",
            "name",
            "direction",
            name="uq_relation_shape",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    owner_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    source_type_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("object_type.id", ondelete="CASCADE"),
        nullable=False,
    )
    target_type_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("object_type.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    name: Mapped[str] = mapped_column(Text, nullable=False)
    direction: Mapped[str] = mapped_column(
        Text, nullable=False, default="forward", server_default="forward"
    )
    # 基数：源端对目标端是一对一 / 一对多 / 多对多。
    cardinality: Mapped[str] = mapped_column(
        Text, nullable=False, default=DEFAULT_CARDINALITY, server_default=DEFAULT_CARDINALITY
    )
    # 两端各自的可读名；为空时界面回落到 name。
    forward_label: Mapped[str | None] = mapped_column(Text)
    reverse_label: Mapped[str | None] = mapped_column(Text)
    # 一对一 / 一对多的链接靠外键属性承载：这条属性存的是对端主键的值。
    # 多对多的链接由独立链接表承载，这里留空。属性被删时置空（SET NULL）。
    foreign_key_property_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("property.id", ondelete="SET NULL")
    )
    status: Mapped[str] = mapped_column(
        Text, nullable=False, default="draft", server_default="draft"
    )
    home_graph_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("graph.id", ondelete="SET NULL")
    )
