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
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

from app.vocabulary import (
    DEFAULT_CARDINALITY,
    DEFAULT_DATATYPE,
    PROPERTY_DATATYPES,
    RELATION_CARDINALITIES,
    sql_in_list,
)

# 嵌入维度写死在 DDL 里，换嵌入模型需要一次迁移。
EMBEDDING_DIM = 1536


class Base(DeclarativeBase):
    pass


class Graph(Base):
    __tablename__ = "graph"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    owner_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False, index=True)
    name: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )


class Conversation(Base):
    __tablename__ = "conversation"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    owner_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False, index=True)
    graph_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("graph.id", ondelete="CASCADE"),
        nullable=False,
        unique=True,
    )
    title: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )


class ConversationMessage(Base):
    __tablename__ = "conversation_message"
    __table_args__ = (
        CheckConstraint(
            "role IN ('user', 'assistant')",
            name="ck_conversation_message_role",
        ),
        CheckConstraint(
            "kind IN ('message', 'error')",
            name="ck_conversation_message_kind",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    conversation_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("conversation.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    role: Mapped[str] = mapped_column(Text, nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    kind: Mapped[str] = mapped_column(
        Text, nullable=False, default="message", server_default="message"
    )
    # 助手回合的有序内容块（思考 / 正文 / 工具调用），刷新后照样能还原。
    # content 仍保留纯文本，供检索和旧逻辑使用。
    blocks: Mapped[list | None] = mapped_column(JSONB, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        # clock_timestamp() 比 now() 更适合消息排序：同一事务里的连续写入也有顺序。
        DateTime(timezone=True),
        server_default=func.clock_timestamp(),
        nullable=False,
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
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    owner_type_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("object_type.id", ondelete="CASCADE"),
        nullable=False,
    )
    name: Mapped[str] = mapped_column(Text, nullable=False)
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
    status: Mapped[str] = mapped_column(
        Text, nullable=False, default="draft", server_default="draft"
    )
    home_graph_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("graph.id", ondelete="SET NULL")
    )


class GraphObject(Base):
    __tablename__ = "graph_object"

    graph_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("graph.id", ondelete="CASCADE"), primary_key=True
    )
    object_type_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("object_type.id", ondelete="CASCADE"),
        primary_key=True,
        index=True,
    )
    added_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class GraphRelation(Base):
    __tablename__ = "graph_relation"

    graph_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("graph.id", ondelete="CASCADE"), primary_key=True
    )
    relation_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("relation.id", ondelete="CASCADE"),
        primary_key=True,
        index=True,
    )
    added_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class ViewState(Base):
    __tablename__ = "view_state"

    graph_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("graph.id", ondelete="CASCADE"), primary_key=True
    )
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    name: Mapped[str | None] = mapped_column(Text)
    spec: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )


class Changeset(Base):
    __tablename__ = "changeset"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    graph_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("graph.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    status: Mapped[str] = mapped_column(Text, nullable=False, default="open", server_default="open")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class ChangesetOp(Base):
    __tablename__ = "changeset_op"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    changeset_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("changeset.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    op: Mapped[str] = mapped_column(Text, nullable=False)
    target_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    payload: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    created_at: Mapped[datetime] = mapped_column(
        # clock_timestamp() 而不是 now()：撤销要按记录顺序倒着回放改动集操作，
        # 同一个事务里 now() 恒定，只能再按随机 uuid 排，顺序就散了。
        DateTime(timezone=True),
        server_default=func.clock_timestamp(),
        nullable=False,
    )
