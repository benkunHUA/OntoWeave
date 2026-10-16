"""action layer: action types, parameters, rules, assignments, criteria

Revision ID: 0017
Revises: 0016

"""

from collections.abc import Sequence

import sqlalchemy as sa
from pgvector.sqlalchemy import Vector
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str = "0017"
down_revision: str | None = "0016"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

# 与 app.vocabulary 保持一致；迁移刻意写死字面量，不 import 应用代码（冻结历史）。
_STATUS = "status IN ('draft', 'confirmed', 'approved')"
_PARAMETER_KIND = "kind IN ('value', 'object_ref')"
_PARAMETER_DATATYPE = (
    "datatype IS NULL OR datatype IN "
    "('text', 'integer', 'decimal', 'boolean', 'date', 'timestamp', 'enum')"
)
_RULE_KIND = (
    "rule_kind IN ('create_object', 'modify_object', 'delete_object', 'create_link', 'delete_link')"
)
_VALUE_SOURCE = "value_source IN ('parameter', 'object_property', 'static')"
_NODE_KIND = "node_kind IN ('group', 'condition')"
_NODE_LOGIC = "logic IS NULL OR logic IN ('and')"
_NODE_OPERATOR = (
    "operator IS NULL OR operator IN "
    "('is', 'is not', 'is null', 'is not null', '<', '<=', '>', '>=')"
)
_OPERAND_SIDE = "side IN ('left', 'right')"
_OPERAND_KIND = "kind IN ('parameter', 'object_property', 'static', 'null')"


def upgrade() -> None:
    op.create_table(
        "action_type",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("owner_id", sa.UUID(), nullable=False),
        sa.Column("name", sa.Text(), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("status", sa.Text(), nullable=False, server_default="draft"),
        sa.Column("home_graph_id", sa.UUID(), nullable=True),
        sa.Column("embedding", Vector(dim=1536), nullable=True),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.ForeignKeyConstraint(["home_graph_id"], ["graph.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("id"),
        sa.CheckConstraint(_STATUS, name="ck_action_type_status"),
    )
    op.create_index("ix_action_type_owner_id", "action_type", ["owner_id"])
    op.create_index(
        "ix_action_type_embedding_hnsw",
        "action_type",
        ["embedding"],
        postgresql_using="hnsw",
        postgresql_ops={"embedding": "vector_cosine_ops"},
    )

    op.create_table(
        "graph_action",
        sa.Column("graph_id", sa.UUID(), nullable=False),
        sa.Column("action_type_id", sa.UUID(), nullable=False),
        sa.Column(
            "added_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.ForeignKeyConstraint(["graph_id"], ["graph.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["action_type_id"], ["action_type.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("graph_id", "action_type_id"),
    )
    op.create_index("ix_graph_action_action_type_id", "graph_action", ["action_type_id"])

    op.create_table(
        "action_parameter",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("action_type_id", sa.UUID(), nullable=False),
        sa.Column("name", sa.Text(), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("kind", sa.Text(), nullable=False, server_default="value"),
        sa.Column("datatype", sa.Text(), nullable=True),
        sa.Column("target_object_type_id", sa.UUID(), nullable=True),
        sa.Column("is_array", sa.Boolean(), nullable=False, server_default=sa.text("false")),
        sa.Column("is_required", sa.Boolean(), nullable=False, server_default=sa.text("false")),
        sa.Column("default_value", postgresql.JSONB(astext_type=sa.Text()), nullable=True),
        sa.Column("enum_values", postgresql.JSONB(astext_type=sa.Text()), nullable=True),
        sa.Column("order_index", sa.Integer(), nullable=False, server_default=sa.text("0")),
        sa.Column("status", sa.Text(), nullable=False, server_default="draft"),
        sa.ForeignKeyConstraint(["action_type_id"], ["action_type.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["target_object_type_id"], ["object_type.id"], ondelete="RESTRICT"),
        sa.PrimaryKeyConstraint("id"),
        sa.CheckConstraint(_STATUS, name="ck_action_parameter_status"),
        sa.CheckConstraint(_PARAMETER_KIND, name="ck_action_parameter_kind"),
        # 值参数必须有数据类型；对象引用参数指向对象类型，两组列互斥。
        sa.CheckConstraint(
            "(kind = 'object_ref') OR (datatype IS NOT NULL)",
            name="ck_action_parameter_datatype",
        ),
        sa.CheckConstraint(_PARAMETER_DATATYPE, name="ck_action_parameter_datatype_value"),
        sa.CheckConstraint(
            "datatype <> 'enum' OR enum_values IS NOT NULL",
            name="ck_action_parameter_enum_values",
        ),
    )
    op.create_index("ix_action_parameter_action_type_id", "action_parameter", ["action_type_id"])

    op.create_table(
        "action_rule",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("action_type_id", sa.UUID(), nullable=False),
        sa.Column("rule_kind", sa.Text(), nullable=False),
        sa.Column("target_object_type_id", sa.UUID(), nullable=True),
        sa.Column("target_relation_id", sa.UUID(), nullable=True),
        sa.Column("source_parameter_id", sa.UUID(), nullable=True),
        sa.Column("target_parameter_id", sa.UUID(), nullable=True),
        sa.Column("order_index", sa.Integer(), nullable=False, server_default=sa.text("0")),
        sa.Column("status", sa.Text(), nullable=False, server_default="draft"),
        sa.ForeignKeyConstraint(["action_type_id"], ["action_type.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["target_object_type_id"], ["object_type.id"], ondelete="RESTRICT"),
        sa.ForeignKeyConstraint(["target_relation_id"], ["relation.id"], ondelete="RESTRICT"),
        sa.ForeignKeyConstraint(
            ["source_parameter_id"], ["action_parameter.id"], ondelete="RESTRICT"
        ),
        sa.ForeignKeyConstraint(
            ["target_parameter_id"], ["action_parameter.id"], ondelete="RESTRICT"
        ),
        sa.PrimaryKeyConstraint("id"),
        sa.CheckConstraint(_STATUS, name="ck_action_rule_status"),
        sa.CheckConstraint(_RULE_KIND, name="ck_action_rule_kind"),
    )
    op.create_index("ix_action_rule_action_type_id", "action_rule", ["action_type_id"])

    op.create_table(
        "action_rule_assignment",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("rule_id", sa.UUID(), nullable=False),
        sa.Column("target_property_id", sa.UUID(), nullable=False),
        sa.Column("value_source", sa.Text(), nullable=False),
        sa.Column("value_parameter_id", sa.UUID(), nullable=True),
        sa.Column("object_parameter_id", sa.UUID(), nullable=True),
        sa.Column("object_property_id", sa.UUID(), nullable=True),
        sa.Column("static_value", postgresql.JSONB(astext_type=sa.Text()), nullable=True),
        sa.Column("status", sa.Text(), nullable=False, server_default="draft"),
        sa.ForeignKeyConstraint(["rule_id"], ["action_rule.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["target_property_id"], ["property.id"], ondelete="RESTRICT"),
        sa.ForeignKeyConstraint(
            ["value_parameter_id"], ["action_parameter.id"], ondelete="RESTRICT"
        ),
        sa.ForeignKeyConstraint(
            ["object_parameter_id"], ["action_parameter.id"], ondelete="RESTRICT"
        ),
        sa.ForeignKeyConstraint(["object_property_id"], ["property.id"], ondelete="RESTRICT"),
        sa.PrimaryKeyConstraint("id"),
        sa.CheckConstraint(_STATUS, name="ck_action_rule_assignment_status"),
        sa.CheckConstraint(_VALUE_SOURCE, name="ck_action_rule_assignment_source"),
    )
    op.create_index("ix_action_rule_assignment_rule_id", "action_rule_assignment", ["rule_id"])

    op.create_table(
        "action_criteria_node",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("action_type_id", sa.UUID(), nullable=False),
        sa.Column("parent_id", sa.UUID(), nullable=True),
        sa.Column("order_index", sa.Integer(), nullable=False, server_default=sa.text("0")),
        sa.Column("node_kind", sa.Text(), nullable=False),
        sa.Column("logic", sa.Text(), nullable=True),
        sa.Column("operator", sa.Text(), nullable=True),
        sa.Column("failure_message", sa.Text(), nullable=True),
        sa.Column("status", sa.Text(), nullable=False, server_default="draft"),
        sa.ForeignKeyConstraint(["action_type_id"], ["action_type.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["parent_id"], ["action_criteria_node.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.CheckConstraint(_STATUS, name="ck_action_criteria_node_status"),
        sa.CheckConstraint(_NODE_KIND, name="ck_action_criteria_node_kind"),
        sa.CheckConstraint(_NODE_LOGIC, name="ck_action_criteria_node_logic"),
        sa.CheckConstraint(_NODE_OPERATOR, name="ck_action_criteria_node_operator"),
    )
    op.create_index(
        "ix_action_criteria_node_action_type_id", "action_criteria_node", ["action_type_id"]
    )
    op.create_index("ix_action_criteria_node_parent_id", "action_criteria_node", ["parent_id"])

    op.create_table(
        "action_criteria_operand",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("node_id", sa.UUID(), nullable=False),
        sa.Column("side", sa.Text(), nullable=False),
        sa.Column("kind", sa.Text(), nullable=False),
        sa.Column("value_parameter_id", sa.UUID(), nullable=True),
        sa.Column("object_parameter_id", sa.UUID(), nullable=True),
        sa.Column("object_property_id", sa.UUID(), nullable=True),
        sa.Column("static_value", postgresql.JSONB(astext_type=sa.Text()), nullable=True),
        sa.Column("status", sa.Text(), nullable=False, server_default="draft"),
        sa.ForeignKeyConstraint(["node_id"], ["action_criteria_node.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(
            ["value_parameter_id"], ["action_parameter.id"], ondelete="RESTRICT"
        ),
        sa.ForeignKeyConstraint(
            ["object_parameter_id"], ["action_parameter.id"], ondelete="RESTRICT"
        ),
        sa.ForeignKeyConstraint(["object_property_id"], ["property.id"], ondelete="RESTRICT"),
        sa.PrimaryKeyConstraint("id"),
        sa.CheckConstraint(_STATUS, name="ck_action_criteria_operand_status"),
        sa.CheckConstraint(_OPERAND_SIDE, name="ck_action_criteria_operand_side"),
        sa.CheckConstraint(_OPERAND_KIND, name="ck_action_criteria_operand_kind"),
    )
    op.create_index("ix_action_criteria_operand_node_id", "action_criteria_operand", ["node_id"])


def downgrade() -> None:
    op.drop_index("ix_action_criteria_operand_node_id", table_name="action_criteria_operand")
    op.drop_table("action_criteria_operand")
    op.drop_index("ix_action_criteria_node_parent_id", table_name="action_criteria_node")
    op.drop_index("ix_action_criteria_node_action_type_id", table_name="action_criteria_node")
    op.drop_table("action_criteria_node")
    op.drop_index("ix_action_rule_assignment_rule_id", table_name="action_rule_assignment")
    op.drop_table("action_rule_assignment")
    op.drop_index("ix_action_rule_action_type_id", table_name="action_rule")
    op.drop_table("action_rule")
    op.drop_index("ix_action_parameter_action_type_id", table_name="action_parameter")
    op.drop_table("action_parameter")
    op.drop_index("ix_graph_action_action_type_id", table_name="graph_action")
    op.drop_table("graph_action")
    op.drop_index("ix_action_type_embedding_hnsw", table_name="action_type")
    op.drop_index("ix_action_type_owner_id", table_name="action_type")
    op.drop_table("action_type")
