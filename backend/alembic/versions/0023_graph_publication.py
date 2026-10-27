"""graph publication artifacts

Revision ID: 0023
Revises: 0022
"""

from collections.abc import Sequence

import sqlalchemy as sa
from pgvector.sqlalchemy import Vector

from alembic import op

revision: str = "0023"
down_revision: str | None = "0022"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

EMBEDDING_DIM = 1536


def upgrade() -> None:
    op.add_column("graph", sa.Column("graph_card", sa.Text(), nullable=True))
    op.add_column("graph", sa.Column("graph_embedding", Vector(EMBEDDING_DIM), nullable=True))
    op.add_column("graph", sa.Column("graph_embedding_model", sa.Text(), nullable=True))
    op.add_column("graph", sa.Column("published_at", sa.DateTime(timezone=True), nullable=True))
    op.create_index(
        "ix_graph_embedding_hnsw",
        "graph",
        ["graph_embedding"],
        postgresql_using="hnsw",
        postgresql_ops={"graph_embedding": "vector_cosine_ops"},
    )


def downgrade() -> None:
    op.drop_index("ix_graph_embedding_hnsw", table_name="graph")
    op.drop_column("graph", "published_at")
    op.drop_column("graph", "graph_embedding_model")
    op.drop_column("graph", "graph_embedding")
    op.drop_column("graph", "graph_card")
