"""relation foreign key binding

Revision ID: 0015
Revises: 0014

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "0015"
down_revision: str | None = "0014"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "relation",
        sa.Column("foreign_key_property_id", sa.UUID(), nullable=True),
    )
    op.create_foreign_key(
        "fk_relation_foreign_key_property",
        "relation",
        "property",
        ["foreign_key_property_id"],
        ["id"],
        ondelete="SET NULL",
    )


def downgrade() -> None:
    op.drop_constraint("fk_relation_foreign_key_property", "relation", type_="foreignkey")
    op.drop_column("relation", "foreign_key_property_id")
