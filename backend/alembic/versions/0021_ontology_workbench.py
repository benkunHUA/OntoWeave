"""ontology workbench conversation kind

Revision ID: 0021
Revises: 0020

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "0021"
down_revision: str | None = "0020"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.drop_constraint("ck_conversation_kind", "conversation", type_="check")
    op.create_check_constraint(
        "ck_conversation_kind",
        "conversation",
        "kind IN ('modeling', 'data_connection', 'data_pipeline', 'ontology_workbench')",
    )


def downgrade() -> None:
    connection = op.get_bind()
    workbench_conversations = connection.execute(
        sa.text("SELECT count(*) FROM conversation WHERE kind = 'ontology_workbench'")
    ).scalar_one()
    if workbench_conversations:
        raise RuntimeError(
            "cannot downgrade: "
            f"{workbench_conversations} ontology_workbench conversations still exist; "
            "delete them before downgrading"
        )

    op.drop_constraint("ck_conversation_kind", "conversation", type_="check")
    op.create_check_constraint(
        "ck_conversation_kind",
        "conversation",
        "kind IN ('modeling', 'data_connection', 'data_pipeline')",
    )
