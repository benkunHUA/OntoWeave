"""unique relation shape

Revision ID: 0003
Revises: 0002
Create Date: 2026-10-07 12:30:00.000000

"""
from typing import Sequence, Union

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "0003"
down_revision: Union[str, None] = "0002"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_unique_constraint(
        "uq_relation_shape",
        "relation",
        ["source_type_id", "target_type_id", "name", "direction"],
    )


def downgrade() -> None:
    op.drop_constraint("uq_relation_shape", "relation", type_="unique")
