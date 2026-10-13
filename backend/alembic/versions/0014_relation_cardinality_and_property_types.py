"""relation cardinality + two-sided labels, property datatypes / array / keys

Revision ID: 0014
Revises: 0013

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "0014"
down_revision: str | None = "0013"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

# 与 app.vocabulary 保持一致；迁移刻意写死字面量，不 import 应用代码（冻结历史）。
_DATATYPE_CHECK = "datatype IN ('text', 'integer', 'decimal', 'boolean', 'date', 'timestamp')"
_CARDINALITY_CHECK = "cardinality IN ('one_to_one', 'one_to_many', 'many_to_many')"


def upgrade() -> None:
    # 历史 datatype 是自由字符串，先归一到受控集合，再加 CHECK；
    # 认不出的值一律兜底成 text，避免迁移在脏数据上失败。
    op.execute(
        """
        UPDATE property
        SET datatype = CASE datatype
            WHEN 'string' THEN 'text'
            WHEN 'int' THEN 'integer'
            WHEN 'float' THEN 'decimal'
            WHEN 'double' THEN 'decimal'
            WHEN 'bool' THEN 'boolean'
            WHEN 'datetime' THEN 'timestamp'
            ELSE 'text'
        END
        WHERE datatype NOT IN ('text', 'integer', 'decimal', 'boolean', 'date', 'timestamp')
        """
    )

    op.add_column(
        "property",
        sa.Column("is_array", sa.Boolean(), nullable=False, server_default=sa.text("false")),
    )
    op.add_column(
        "property",
        sa.Column("is_primary_key", sa.Boolean(), nullable=False, server_default=sa.text("false")),
    )
    op.add_column(
        "property",
        sa.Column("is_title_key", sa.Boolean(), nullable=False, server_default=sa.text("false")),
    )
    op.create_check_constraint("ck_property_datatype", "property", _DATATYPE_CHECK)

    op.add_column(
        "relation",
        sa.Column(
            "cardinality",
            sa.Text(),
            nullable=False,
            server_default="many_to_many",
        ),
    )
    op.add_column("relation", sa.Column("forward_label", sa.Text(), nullable=True))
    op.add_column("relation", sa.Column("reverse_label", sa.Text(), nullable=True))
    op.create_check_constraint("ck_relation_cardinality", "relation", _CARDINALITY_CHECK)


def downgrade() -> None:
    op.drop_constraint("ck_relation_cardinality", "relation", type_="check")
    op.drop_column("relation", "reverse_label")
    op.drop_column("relation", "forward_label")
    op.drop_column("relation", "cardinality")

    op.drop_constraint("ck_property_datatype", "property", type_="check")
    op.drop_column("property", "is_title_key")
    op.drop_column("property", "is_primary_key")
    op.drop_column("property", "is_array")
