"""conversation message history

Revision ID: 0008
Revises: 0007

"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str = "0008"
down_revision: str | None = "0007"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "conversation_message",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("conversation_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("role", sa.Text(), nullable=False),
        sa.Column("content", sa.Text(), nullable=False),
        sa.Column("kind", sa.Text(), server_default="message", nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("clock_timestamp()"),
            nullable=False,
        ),
        sa.CheckConstraint(
            "kind IN ('message', 'error')",
            name="ck_conversation_message_kind",
        ),
        sa.CheckConstraint(
            "role IN ('user', 'assistant')",
            name="ck_conversation_message_role",
        ),
        sa.ForeignKeyConstraint(
            ["conversation_id"],
            ["conversation.id"],
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_conversation_message_conversation_id",
        "conversation_message",
        ["conversation_id"],
    )


def downgrade() -> None:
    op.drop_index(
        "ix_conversation_message_conversation_id",
        table_name="conversation_message",
    )
    op.drop_table("conversation_message")
