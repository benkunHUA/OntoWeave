from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any

from pgvector.sqlalchemy import Vector
from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    Text,
    func,
    text,
)
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.constants import EMBEDDING_DIM
from app.models.base import Base
from app.vocabulary import (
    ACTION_PARAMETER_KINDS,
    ACTION_RULE_KINDS,
    ACTION_VALUE_SOURCES,
    PROPERTY_DATATYPES,
    sql_in_list,
)


class ActionType(Base):
    """动作类型：一次能做的一组改动的定义。共享池元素，与对象类型同构。"""

    __tablename__ = "action_type"
    __table_args__ = (
        CheckConstraint(
            "status IN ('draft', 'confirmed', 'approved')",
            name="ck_action_type_status",
        ),
        Index(
            "ix_action_type_embedding_hnsw",
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


class GraphAction(Base):
    __tablename__ = "graph_action"

    graph_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("graph.id", ondelete="CASCADE"), primary_key=True
    )
    action_type_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("action_type.id", ondelete="CASCADE"),
        primary_key=True,
        index=True,
    )
    added_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class ActionParameter(Base):
    """动作的输入。值参数复用属性数据类型，对象引用参数指向某个对象类型。"""

    __tablename__ = "action_parameter"
    __table_args__ = (
        CheckConstraint(
            "status IN ('draft', 'confirmed', 'approved')",
            name="ck_action_parameter_status",
        ),
        CheckConstraint(
            sql_in_list("kind", ACTION_PARAMETER_KINDS),
            name="ck_action_parameter_kind",
        ),
        # 值参数才带数据类型；对象引用参数指向一个对象类型，两组列互斥。
        CheckConstraint(
            "(kind = 'object_ref') OR (datatype IS NOT NULL)",
            name="ck_action_parameter_datatype",
        ),
        CheckConstraint(
            "datatype IS NULL OR " + sql_in_list("datatype", PROPERTY_DATATYPES),
            name="ck_action_parameter_datatype_value",
        ),
        CheckConstraint(
            "datatype <> 'enum' OR enum_values IS NOT NULL",
            name="ck_action_parameter_enum_values",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    action_type_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("action_type.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    name: Mapped[str] = mapped_column(Text, nullable=False)
    # 给智能体看的说明：参数名往往看不出该填什么。
    description: Mapped[str | None] = mapped_column(Text)
    kind: Mapped[str] = mapped_column(Text, nullable=False, default="value", server_default="value")
    datatype: Mapped[str | None] = mapped_column(Text)
    target_object_type_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("object_type.id", ondelete="RESTRICT")
    )
    is_array: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default=text("false")
    )
    is_required: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default=text("false")
    )
    default_value: Mapped[Any | None] = mapped_column(JSONB)
    enum_values: Mapped[list[str] | None] = mapped_column(JSONB)
    order_index: Mapped[int] = mapped_column(
        Integer, nullable=False, default=0, server_default=text("0")
    )
    status: Mapped[str] = mapped_column(
        Text, nullable=False, default="draft", server_default="draft"
    )


class ActionRule(Base):
    """规则：把参数变成对本体的一次改动。

    `source_parameter_id` 是规则的第一个对象参数——对象规则用它指明「改哪个对象」，
    链接规则用它指明源端；`target_parameter_id` 只有链接规则用，指目标端。
    """

    __tablename__ = "action_rule"
    __table_args__ = (
        CheckConstraint(
            "status IN ('draft', 'confirmed', 'approved')",
            name="ck_action_rule_status",
        ),
        CheckConstraint(
            sql_in_list("rule_kind", ACTION_RULE_KINDS),
            name="ck_action_rule_kind",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    action_type_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("action_type.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    rule_kind: Mapped[str] = mapped_column(Text, nullable=False)
    target_object_type_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("object_type.id", ondelete="RESTRICT")
    )
    target_relation_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("relation.id", ondelete="RESTRICT")
    )
    source_parameter_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("action_parameter.id", ondelete="RESTRICT")
    )
    target_parameter_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("action_parameter.id", ondelete="RESTRICT")
    )
    order_index: Mapped[int] = mapped_column(
        Integer, nullable=False, default=0, server_default=text("0")
    )
    status: Mapped[str] = mapped_column(
        Text, nullable=False, default="draft", server_default="draft"
    )


class ActionRuleAssignment(Base):
    """对象规则里的「哪个属性 ← 哪个值来源」。"""

    __tablename__ = "action_rule_assignment"
    __table_args__ = (
        CheckConstraint(
            "status IN ('draft', 'confirmed', 'approved')",
            name="ck_action_rule_assignment_status",
        ),
        CheckConstraint(
            sql_in_list("value_source", ACTION_VALUE_SOURCES),
            name="ck_action_rule_assignment_source",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    rule_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("action_rule.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    target_property_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("property.id", ondelete="RESTRICT"), nullable=False
    )
    value_source: Mapped[str] = mapped_column(Text, nullable=False)
    value_parameter_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("action_parameter.id", ondelete="RESTRICT")
    )
    object_parameter_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("action_parameter.id", ondelete="RESTRICT")
    )
    object_property_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("property.id", ondelete="RESTRICT")
    )
    static_value: Mapped[Any | None] = mapped_column(JSONB)
    status: Mapped[str] = mapped_column(
        Text, nullable=False, default="draft", server_default="draft"
    )
