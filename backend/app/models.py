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
    ForeignKey,
    Index,
    Integer,
    Text,
    UniqueConstraint,
    func,
    text,
)
from sqlalchemy.dialects.postgresql import ARRAY, JSONB, UUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

from app.vocabulary import (
    ACTION_CRITERIA_LOGICS,
    ACTION_CRITERIA_NODE_KINDS,
    ACTION_CRITERIA_OPERATORS,
    ACTION_CRITERIA_SIDES,
    ACTION_OPERAND_KINDS,
    ACTION_PARAMETER_KINDS,
    ACTION_RULE_KINDS,
    ACTION_VALUE_SOURCES,
    CONVERSATION_KINDS,
    DEFAULT_CARDINALITY,
    DEFAULT_DATATYPE,
    PROPERTY_DATATYPES,
    RELATION_CARDINALITIES,
    ConversationKind,
    sql_in_list,
)

# 嵌入维度写死在 DDL 里，换嵌入模型需要一次迁移。
EMBEDDING_DIM = 1536


class Base(DeclarativeBase):
    pass


class Graph(Base):
    __tablename__ = "graph"
    __table_args__ = (
        Index(
            "ix_graph_embedding_hnsw",
            "graph_embedding",
            postgresql_using="hnsw",
            postgresql_ops={"graph_embedding": "vector_cosine_ops"},
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    owner_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False, index=True)
    name: Mapped[str | None] = mapped_column(Text)
    graph_card: Mapped[str | None] = mapped_column(Text)
    graph_embedding: Mapped[list[float] | None] = mapped_column(Vector(EMBEDDING_DIM))
    graph_embedding_model: Mapped[str | None] = mapped_column(Text)
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
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
    __table_args__ = (
        CheckConstraint(sql_in_list("kind", CONVERSATION_KINDS), name="ck_conversation_kind"),
        UniqueConstraint("kind", "resource_id", name="uq_conversation_kind_resource"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    owner_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False, index=True)
    kind: Mapped[str] = mapped_column(
        Text,
        nullable=False,
        default=ConversationKind.MODELING.value,
        server_default=ConversationKind.MODELING.value,
    )
    resource_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    title: Mapped[str | None] = mapped_column(Text)
    archived_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
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


class ConversationEvent(Base):
    """数据连接 / 同步产生的结构化事件卡片。

    事件由 Java Outbox 经 RabbitMQ 投递，Python 按 `event_id` 幂等落库；它不是
    LangGraph 的消息，不进入检查点。`created_at` 用 clock_timestamp()，保证同一毫秒
    内的连续事件也有稳定顺序，`(created_at, id)` 就是增量游标。
    """

    __tablename__ = "conversation_event"
    __table_args__ = (
        UniqueConstraint("event_id", name="uq_conversation_event_event_id"),
        Index(
            "ix_conversation_event_conversation_created",
            "conversation_id",
            "created_at",
            "id",
        ),
        Index("ix_conversation_event_turn", "turn_id"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    conversation_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("conversation.id", ondelete="CASCADE"),
        nullable=False,
    )
    event_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    operation_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    run_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    turn_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    event_type: Mapped[str] = mapped_column(Text, nullable=False)
    summary: Mapped[str] = mapped_column(Text, nullable=False)
    payload: Mapped[dict] = mapped_column(
        JSONB, nullable=False, default=dict, server_default=text("'{}'::jsonb")
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.clock_timestamp(), nullable=False
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


class ActionCriteriaNode(Base):
    """提交校验的条件树节点（邻接表）：分组或叶子条件。"""

    __tablename__ = "action_criteria_node"
    __table_args__ = (
        CheckConstraint(
            "status IN ('draft', 'confirmed', 'approved')",
            name="ck_action_criteria_node_status",
        ),
        CheckConstraint(
            sql_in_list("node_kind", ACTION_CRITERIA_NODE_KINDS),
            name="ck_action_criteria_node_kind",
        ),
        CheckConstraint(
            "logic IS NULL OR " + sql_in_list("logic", ACTION_CRITERIA_LOGICS),
            name="ck_action_criteria_node_logic",
        ),
        CheckConstraint(
            "operator IS NULL OR " + sql_in_list("operator", ACTION_CRITERIA_OPERATORS),
            name="ck_action_criteria_node_operator",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    action_type_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("action_type.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    parent_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("action_criteria_node.id", ondelete="CASCADE"), index=True
    )
    order_index: Mapped[int] = mapped_column(
        Integer, nullable=False, default=0, server_default=text("0")
    )
    node_kind: Mapped[str] = mapped_column(Text, nullable=False)
    logic: Mapped[str | None] = mapped_column(Text)
    operator: Mapped[str | None] = mapped_column(Text)
    failure_message: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(
        Text, nullable=False, default="draft", server_default="draft"
    )


class ActionCriteriaOperand(Base):
    """条件的一侧操作数。左右共用一张表，用 side 区分。"""

    __tablename__ = "action_criteria_operand"
    __table_args__ = (
        CheckConstraint(
            "status IN ('draft', 'confirmed', 'approved')",
            name="ck_action_criteria_operand_status",
        ),
        CheckConstraint(
            sql_in_list("side", ACTION_CRITERIA_SIDES),
            name="ck_action_criteria_operand_side",
        ),
        CheckConstraint(
            sql_in_list("kind", ACTION_OPERAND_KINDS),
            name="ck_action_criteria_operand_kind",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    node_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("action_criteria_node.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    side: Mapped[str] = mapped_column(Text, nullable=False)
    kind: Mapped[str] = mapped_column(Text, nullable=False)
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
