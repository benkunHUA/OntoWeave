"""conversation resource identity

Revision ID: 0018
Revises: 0017

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "0018"
down_revision: str | None = "0017"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "conversation",
        sa.Column("kind", sa.Text(), nullable=False, server_default="modeling"),
    )
    op.create_check_constraint(
        "ck_conversation_kind",
        "conversation",
        "kind IN ('modeling', 'data_connection')",
    )
    op.add_column("conversation", sa.Column("resource_id", sa.UUID(), nullable=True))
    op.execute("UPDATE conversation SET resource_id = graph_id")
    op.alter_column(
        "conversation",
        "resource_id",
        existing_type=sa.UUID(),
        nullable=False,
    )
    op.create_unique_constraint(
        "uq_conversation_kind_resource",
        "conversation",
        ["kind", "resource_id"],
    )
    op.drop_constraint("conversation_graph_id_key", "conversation", type_="unique")
    op.drop_constraint("conversation_graph_id_fkey", "conversation", type_="foreignkey")
    op.drop_column("conversation", "graph_id")


def downgrade() -> None:
    non_modeling = (
        op.get_bind()
        .execute(sa.text("SELECT count(*) FROM conversation WHERE kind <> 'modeling'"))
        .scalar_one()
    )
    if non_modeling:
        raise RuntimeError("cannot downgrade while non-modeling conversations exist")

    orphaned_modeling = (
        op.get_bind()
        .execute(
            sa.text(
                """
                SELECT count(*)
                FROM conversation
                LEFT JOIN graph ON graph.id = conversation.resource_id
                WHERE conversation.kind = 'modeling' AND graph.id IS NULL
                """
            )
        )
        .scalar_one()
    )
    if orphaned_modeling:
        raise RuntimeError(
            f"cannot downgrade: {orphaned_modeling} modeling conversation(s) reference "
            "missing graph rows; repair or delete them before downgrading"
        )

    op.add_column("conversation", sa.Column("graph_id", sa.UUID(), nullable=True))
    op.execute("UPDATE conversation SET graph_id = resource_id")
    op.alter_column(
        "conversation",
        "graph_id",
        existing_type=sa.UUID(),
        nullable=False,
    )
    op.create_unique_constraint("conversation_graph_id_key", "conversation", ["graph_id"])
    op.create_foreign_key(
        "conversation_graph_id_fkey",
        "conversation",
        "graph",
        ["graph_id"],
        ["id"],
        ondelete="CASCADE",
    )
    op.drop_constraint("uq_conversation_kind_resource", "conversation", type_="unique")
    op.drop_column("conversation", "resource_id")
    op.drop_constraint("ck_conversation_kind", "conversation", type_="check")
    op.drop_column("conversation", "kind")
