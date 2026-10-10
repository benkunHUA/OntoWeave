"""sync view state names with graph names

Revision ID: 0009
Revises: 0008

"""

from collections.abc import Sequence

from alembic import op

revision: str = "0009"
down_revision: str | None = "0008"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute(
        """
        UPDATE view_state
        SET name = graph.name
        FROM graph
        WHERE view_state.graph_id = graph.id
          AND graph.name IS NOT NULL
          AND view_state.name IS DISTINCT FROM graph.name
        """
    )


def downgrade() -> None:
    # 名称已合并为图名称，旧视图名称无法可靠恢复。
    pass
