"""english names and dataset search index

Revision ID: 0022
Revises: 0021
"""

from collections.abc import Sequence

import sqlalchemy as sa
from pgvector.sqlalchemy import Vector
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str = "0022"
down_revision: str | None = "0021"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

EMBEDDING_DIM = 1536


def upgrade() -> None:
    op.add_column("object_type", sa.Column("name_en", sa.Text(), nullable=True))
    op.add_column("property", sa.Column("name_en", sa.Text(), nullable=True))

    op.create_table(
        "dataset_search_index",
        sa.Column("dataset_kind", sa.Text(), nullable=False),
        sa.Column("version_id", sa.UUID(), nullable=False),
        sa.Column("dataset_id", sa.UUID(), nullable=False),
        sa.Column("owner_id", sa.UUID(), nullable=False),
        sa.Column("name", sa.Text(), nullable=False),
        sa.Column("version_number", sa.Integer(), nullable=False),
        sa.Column("row_count", sa.BigInteger(), nullable=False),
        sa.Column(
            "columns",
            postgresql.JSONB(),
            nullable=False,
            server_default=sa.text("'[]'::jsonb"),
        ),
        sa.Column(
            "column_types",
            sa.ARRAY(sa.Text()),
            nullable=False,
            server_default=sa.text("'{}'::text[]"),
        ),
        sa.Column("document_text", sa.Text(), nullable=False),
        sa.Column("document_hash", sa.Text(), nullable=False),
        sa.Column("embedding", Vector(EMBEDDING_DIM), nullable=True),
        sa.Column("embedding_model", sa.Text(), nullable=False),
        sa.Column("template_version", sa.Text(), nullable=False),
        sa.Column("status", sa.Text(), nullable=False, server_default=sa.text("'pending'")),
        sa.Column("error_message", sa.Text(), nullable=True),
        sa.Column("is_current", sa.Boolean(), nullable=False, server_default=sa.text("true")),
        sa.Column("published_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("embedded_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.CheckConstraint(
            "dataset_kind IN ('SYNC', 'PIPELINE')",
            name="ck_dataset_search_index_kind",
        ),
        sa.CheckConstraint(
            "status IN ('pending', 'ready', 'failed')",
            name="ck_dataset_search_index_status",
        ),
        sa.PrimaryKeyConstraint("dataset_kind", "version_id"),
    )
    op.create_index(
        "uq_dataset_search_index_current",
        "dataset_search_index",
        ["dataset_kind", "dataset_id"],
        unique=True,
        postgresql_where=sa.text("is_current"),
    )
    op.create_index(
        "ix_dataset_search_index_owner_current",
        "dataset_search_index",
        ["owner_id", "is_current", "status"],
    )
    op.create_index(
        "ix_dataset_search_index_column_types",
        "dataset_search_index",
        ["column_types"],
        postgresql_using="gin",
    )
    op.create_index(
        "ix_dataset_search_index_embedding_hnsw",
        "dataset_search_index",
        ["embedding"],
        postgresql_using="hnsw",
        postgresql_ops={"embedding": "vector_cosine_ops"},
        postgresql_where=sa.text("is_current AND status = 'ready'"),
    )


def downgrade() -> None:
    op.drop_index("ix_dataset_search_index_embedding_hnsw", table_name="dataset_search_index")
    op.drop_index("ix_dataset_search_index_column_types", table_name="dataset_search_index")
    op.drop_index("ix_dataset_search_index_owner_current", table_name="dataset_search_index")
    op.drop_index("uq_dataset_search_index_current", table_name="dataset_search_index")
    op.drop_table("dataset_search_index")
    op.drop_column("property", "name_en")
    op.drop_column("object_type", "name_en")
