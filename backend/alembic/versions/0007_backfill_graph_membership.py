"""backfill graph membership for agent-created ontology elements

Revision ID: 0007
Revises: 0006

"""

from collections.abc import Sequence

from alembic import op

revision: str = "0007"
down_revision: str | None = "0006"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    """修复智能体曾经只写共享池、没有写图成员的记录。"""
    op.execute(
        """
        INSERT INTO graph_object (graph_id, object_type_id, added_at)
        SELECT home_graph_id, id, now()
        FROM object_type
        WHERE home_graph_id IS NOT NULL
        ON CONFLICT DO NOTHING
        """
    )
    op.execute(
        """
        INSERT INTO graph_relation (graph_id, relation_id, added_at)
        SELECT relation.home_graph_id, relation.id, now()
        FROM relation
        WHERE relation.home_graph_id IS NOT NULL
          AND EXISTS (
              SELECT 1
              FROM graph_object
              WHERE graph_object.graph_id = relation.home_graph_id
                AND graph_object.object_type_id = relation.source_type_id
          )
          AND EXISTS (
              SELECT 1
              FROM graph_object
              WHERE graph_object.graph_id = relation.home_graph_id
                AND graph_object.object_type_id = relation.target_type_id
          )
        ON CONFLICT DO NOTHING
        """
    )


def downgrade() -> None:
    # 这是数据修复迁移，不能可靠区分修复写回的行和原本存在的图成员。
    pass
