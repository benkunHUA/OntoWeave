"""changeset op clock-based created_at

Revision ID: 0012
Revises: 0011
Create Date: 2026-10-11

"""

from collections.abc import Sequence

from alembic import op

revision: str = "0012"
down_revision: str | None = "0011"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

# now() 是事务开始时间，同一个事务里插入的多条改动集操作会拿到同一个时间戳，
# list_ops 只能再按随机 uuid 排，顺序就不确定了。撤销要「倒着回放」每条操作，
# 顺序错了还原结果就错了。换成 clock_timestamp()，每条 insert 各有先后。
UPGRADE_SQL = """
ALTER TABLE changeset_op
    ALTER COLUMN created_at SET DEFAULT clock_timestamp()
"""

DOWNGRADE_SQL = """
ALTER TABLE changeset_op
    ALTER COLUMN created_at SET DEFAULT now()
"""


def upgrade() -> None:
    op.execute(UPGRADE_SQL)


def downgrade() -> None:
    op.execute(DOWNGRADE_SQL)
