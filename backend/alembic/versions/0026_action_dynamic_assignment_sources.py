"""action assignments: current_time and auto_generate sources

Revision ID: 0026
Revises: 0025

"""

from collections.abc import Sequence

from alembic import op

revision: str = "0026"
down_revision: str | None = "0025"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

_CONSTRAINT = "ck_action_rule_assignment_source"
_OLD_SOURCES = "value_source IN ('parameter', 'object_property', 'static')"
_NEW_SOURCES = (
    "value_source IN ('parameter', 'object_property', 'static', 'current_time', 'auto_generate')"
)


def upgrade() -> None:
    op.drop_constraint(_CONSTRAINT, "action_rule_assignment", type_="check")
    op.create_check_constraint(_CONSTRAINT, "action_rule_assignment", _NEW_SOURCES)


def downgrade() -> None:
    op.execute(
        "UPDATE action_rule_assignment SET value_source = 'static' "
        "WHERE value_source IN ('current_time', 'auto_generate')"
    )
    op.drop_constraint(_CONSTRAINT, "action_rule_assignment", type_="check")
    op.create_check_constraint(_CONSTRAINT, "action_rule_assignment", _OLD_SOURCES)
