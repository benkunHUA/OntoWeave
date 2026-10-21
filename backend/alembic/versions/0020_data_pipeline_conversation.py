"""data pipeline conversation binding and turn ids

Revision ID: 0020
Revises: 0019

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "0020"
down_revision: str | None = "0019"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.drop_constraint("ck_conversation_kind", "conversation", type_="check")
    op.create_check_constraint(
        "ck_conversation_kind",
        "conversation",
        "kind IN ('modeling', 'data_connection', 'data_pipeline')",
    )
    op.add_column("conversation", sa.Column("archived_at", sa.DateTime(timezone=True)))
    op.add_column("conversation_event", sa.Column("turn_id", sa.UUID()))
    op.create_index("ix_conversation_event_turn", "conversation_event", ["turn_id"])


def downgrade() -> None:
    connection = op.get_bind()
    pipelines = connection.execute(
        sa.text("SELECT count(*) FROM conversation WHERE kind = 'data_pipeline'")
    ).scalar_one()
    if pipelines:
        raise RuntimeError(
            f"cannot downgrade: {pipelines} data_pipeline conversations still exist; "
            "delete them before downgrading"
        )

    turn_ids = connection.execute(
        sa.text("SELECT count(*) FROM conversation_event WHERE turn_id IS NOT NULL")
    ).scalar_one()
    if turn_ids:
        raise RuntimeError(
            f"cannot downgrade: {turn_ids} conversation events still carry turn ids; "
            "clear them before downgrading"
        )

    op.drop_index("ix_conversation_event_turn", table_name="conversation_event")
    op.drop_column("conversation_event", "turn_id")
    op.drop_column("conversation", "archived_at")
    op.drop_constraint("ck_conversation_kind", "conversation", type_="check")
    op.create_check_constraint(
        "ck_conversation_kind",
        "conversation",
        "kind IN ('modeling', 'data_connection')",
    )
