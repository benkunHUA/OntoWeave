"""defaults for datatype / required / direction

Revision ID: 0002
Revises: 0001
Create Date: 2026-10-07 12:05:00.000000

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "0002"
down_revision: str | None = "0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.alter_column(
        "property",
        "datatype",
        existing_type=sa.Text(),
        existing_nullable=False,
        server_default="text",
    )
    op.alter_column(
        "property",
        "required",
        existing_type=sa.Boolean(),
        existing_nullable=False,
        server_default=sa.text("false"),
    )
    op.alter_column(
        "relation",
        "direction",
        existing_type=sa.Text(),
        existing_nullable=False,
        server_default="forward",
    )


def downgrade() -> None:
    op.alter_column(
        "property",
        "datatype",
        existing_type=sa.Text(),
        existing_nullable=False,
        server_default=None,
    )
    op.alter_column(
        "property",
        "required",
        existing_type=sa.Boolean(),
        existing_nullable=False,
        server_default=None,
    )
    op.alter_column(
        "relation",
        "direction",
        existing_type=sa.Text(),
        existing_nullable=False,
        server_default=None,
    )
