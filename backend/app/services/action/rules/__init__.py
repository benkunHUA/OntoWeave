"""动作规则与字段赋值。"""

from app.services.action.rules.assignments import (
    assignment_target_type,
    create_assignment,
    ensure_assignment_shape,
    get_assignment,
    list_assignments,
    validate_assignment_patch,
)
from app.services.action.rules.common import get_rule, relation_endpoints
from app.services.action.rules.rules import (
    create_rule,
    ensure_rule_shape,
    list_rules,
    next_order_index,
    validate_rule_patch,
)

__all__ = [
    "assignment_target_type",
    "create_assignment",
    "create_rule",
    "ensure_assignment_shape",
    "ensure_rule_shape",
    "get_assignment",
    "get_rule",
    "list_assignments",
    "list_rules",
    "next_order_index",
    "relation_endpoints",
    "validate_assignment_patch",
    "validate_rule_patch",
]
