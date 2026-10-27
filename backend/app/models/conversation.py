from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import (
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

from app.models.base import Base
from app.vocabulary import CONVERSATION_KINDS, ConversationKind, sql_in_list


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
