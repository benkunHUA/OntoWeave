"""property enum datatype

Revision ID: 0016
Revises: 0015

"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str = "0016"
down_revision: str | None = "0015"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

# 与 app.vocabulary 保持一致；迁移刻意写死字面量，不 import 应用代码（冻结历史）。
_DATATYPE_CHECK = (
    "datatype IN ('text', 'integer', 'decimal', 'boolean', 'date', 'timestamp', 'enum')"
)
_ENUM_VALUES_CHECK = "datatype <> 'enum' OR enum_values IS NOT NULL"


def upgrade() -> None:
    op.add_column(
        "property",
        sa.Column("enum_values", postgresql.JSONB(astext_type=sa.Text()), nullable=True),
    )
    # datatype 的候选集合要接纳 enum，约束得整条重建。
    op.drop_constraint("ck_property_datatype", "property", type_="check")
    op.create_check_constraint("ck_property_datatype", "property", _DATATYPE_CHECK)
    op.create_check_constraint("ck_property_enum_values", "property", _ENUM_VALUES_CHECK)


def downgrade() -> None:
    op.drop_constraint("ck_property_enum_values", "property", type_="check")
    op.drop_constraint("ck_property_datatype", "property", type_="check")
    # 已经用了 enum 的属性没法退回旧集合，一并落回 text，否则约束加不回去。
    op.execute("UPDATE property SET datatype = 'text' WHERE datatype = 'enum'")
    op.create_check_constraint(
        "ck_property_datatype",
        "property",
        "datatype IN ('text', 'integer', 'decimal', 'boolean', 'date', 'timestamp')",
    )
    op.drop_column("property", "enum_values")
