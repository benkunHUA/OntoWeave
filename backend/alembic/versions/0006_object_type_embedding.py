"""object type embedding

Revision ID: 0006
Revises: 0005
Create Date: 2026-10-07 14:40:00.000000

"""

from collections.abc import Sequence

import sqlalchemy as sa
from pgvector.sqlalchemy import Vector

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "0006"
down_revision: str | None = "0005"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

EMBEDDING_DIM = 1536


def upgrade() -> None:
    op.add_column(
        "object_type",
        sa.Column("embedding", Vector(EMBEDDING_DIM), nullable=True),
    )
    # HNSW 不需要预先训练，适合边写边查。
    op.create_index(
        "ix_object_type_embedding_hnsw",
        "object_type",
        ["embedding"],
        postgresql_using="hnsw",
        postgresql_ops={"embedding": "vector_cosine_ops"},
    )


def downgrade() -> None:
    op.drop_index("ix_object_type_embedding_hnsw", table_name="object_type")
    op.drop_column("object_type", "embedding")
