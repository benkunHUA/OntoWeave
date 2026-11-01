"""business conversation kind

Revision ID: 0025
Revises: 0024
"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "0025"
down_revision: str | None = "0024"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.drop_constraint("ck_conversation_kind", "conversation", type_="check")
    op.execute("UPDATE conversation SET kind = 'business' WHERE kind = 'query'")
    op.create_check_constraint(
        "ck_conversation_kind",
        "conversation",
        "kind IN ('modeling', 'data_connection', 'data_pipeline', 'ontology_workbench', 'business')",
    )


def downgrade() -> None:
    connection = op.get_bind()
    business = connection.execute(
        sa.text("SELECT count(*) FROM conversation WHERE kind = 'business'")
    ).scalar_one()
    if business:
        raise RuntimeError(
            f"cannot downgrade: {business} business conversations still exist; "
            "delete them before downgrading"
        )
    op.drop_constraint("ck_conversation_kind", "conversation", type_="check")
    op.create_check_constraint(
        "ck_conversation_kind",
        "conversation",
        "kind IN ('modeling', 'data_connection', 'data_pipeline', 'ontology_workbench', 'query')",
    )
