"""动作提交校验的条件树模型。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy import CheckConstraint, ForeignKey, Integer, Text, text
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base
from app.vocabulary import (
    ACTION_CRITERIA_LOGICS,
    ACTION_CRITERIA_NODE_KINDS,
    ACTION_CRITERIA_OPERATORS,
    ACTION_CRITERIA_SIDES,
    ACTION_OPERAND_KINDS,
    sql_in_list,
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
