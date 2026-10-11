"""clear embeddings on draft object types

Revision ID: 0013
Revises: 0012

"""

from collections.abc import Sequence

from alembic import op

revision: str = "0013"
down_revision: str | None = "0012"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # 向量改到「确认」时才写：历史草稿上残留的向量清掉，
    # 否则草稿仍会被 search_ontology 检索到、被误复用。
    op.execute("UPDATE object_type SET embedding = NULL WHERE status = 'draft'")


def downgrade() -> None:
    # 清掉的草稿向量没有留存，无法恢复（重新确认时会按当前文本重算）。
    pass
