"""close tool blocks left running by an answered interrupt

Revision ID: 0011
Revises: 0010

"""

from collections.abc import Sequence

from alembic import op

revision: str = "0011"
down_revision: str | None = "0010"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

# ask_user 和工具审批都是在工具内部挂起的：起块的那一轮没有结果，ToolMessage 要等
# 用户回答后的下一轮才出现，跨轮导致状态漏回填。这里只收拾确定已经答完的历史：
# 同一条消息里既有中断块、后面又确实又来了新消息的，那必然是答过了。
# 运行到一半崩掉的块（没有中断块）不在这次修复范围内，不猜它的终态。
REPAIR_SQL = """
UPDATE conversation_message AS cm
SET blocks = (
    SELECT jsonb_agg(
        CASE
            WHEN block->>'kind' = 'tool' AND block->>'status' = 'running'
                THEN jsonb_set(block, '{status}', '"ok"'::jsonb)
            ELSE block
        END
        ORDER BY position
    )
    FROM jsonb_array_elements(cm.blocks) WITH ORDINALITY AS item(block, position)
)
WHERE jsonb_typeof(cm.blocks) = 'array'
  AND cm.blocks @> '[{"kind": "tool", "status": "running"}]'::jsonb
  AND cm.blocks @> '[{"kind": "interrupt"}]'::jsonb
  AND EXISTS (
      SELECT 1
      FROM conversation_message AS later
      WHERE later.conversation_id = cm.conversation_id
        AND (later.created_at, later.id) > (cm.created_at, cm.id)
  )
"""


def upgrade() -> None:
    op.execute(REPAIR_SQL)


def downgrade() -> None:
    # 修好的块无法分辨是原本就 ok 还是这次补的，不做回滚。
    pass
