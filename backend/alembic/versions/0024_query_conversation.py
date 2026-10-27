"""query conversation kind

Revision ID: 0024
Revises: 0023
"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "0024"
down_revision: str | None = "0023"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.drop_constraint("ck_conversation_kind", "conversation", type_="check")
    op.create_check_constraint(
        "ck_conversation_kind",
        "conversation",
        "kind IN ('modeling', 'data_connection', 'data_pipeline', 'ontology_workbench', 'query')",
    )


def downgrade() -> None:
    connection = op.get_bind()
    queries = connection.execute(
        sa.text("SELECT count(*) FROM conversation WHERE kind = 'query'")
    ).scalar_one()
    if queries:
        raise RuntimeError(
            f"cannot downgrade: {queries} query conversations still exist; "
            "delete them before downgrading"
        )
    op.drop_constraint("ck_conversation_kind", "conversation", type_="check")
    op.create_check_constraint(
        "ck_conversation_kind",
        "conversation",
        "kind IN ('modeling', 'data_connection', 'data_pipeline', 'ontology_workbench')",
    )
