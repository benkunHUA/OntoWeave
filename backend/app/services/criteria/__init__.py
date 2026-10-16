"""提交校验的条件树（门面）。

一次只碰一棵树：建节点与操作数、整棵替换、整棵树的形状校验、以及自引用带来的删除
顺序。分段放在 `store`（读取与删除顺序）、`shape`（单条条件的形状）、`build`（建构）、
`tree`（整棵树的形状与补丁校验）里，调用方只认这里。
"""

from app.services.criteria.build import (
    CONDITION,
    DEFAULT_LOGIC,
    GROUP,
    create_condition,
    create_group,
    create_operand,
    replace_criteria,
)
from app.services.criteria.plan import (
    ConditionSpec,
    ReplacementPlan,
    parse_condition,
    replacement_plan,
)
from app.services.criteria.shape import (
    ensure_condition_shape,
    ensure_condition_sides,
    ensure_operand_shape,
)
from app.services.criteria.store import (
    LEFT,
    RIGHT,
    NodeRead,
    OperandSpec,
    find_root,
    get_node,
    get_operand,
    list_nodes,
    list_operands,
    load_tree,
    nodes_by_depth_desc,
    operand_spec,
)
from app.services.criteria.tree import (
    V1_SHAPE_NOTE,
    ensure_tree_shape,
    validate_node_patch,
    validate_operand_patch,
)

__all__ = [
    "CONDITION",
    "ConditionSpec",
    "ReplacementPlan",
    "DEFAULT_LOGIC",
    "GROUP",
    "LEFT",
    "RIGHT",
    "V1_SHAPE_NOTE",
    "NodeRead",
    "OperandSpec",
    "create_condition",
    "create_group",
    "create_operand",
    "ensure_condition_shape",
    "ensure_condition_sides",
    "ensure_operand_shape",
    "ensure_tree_shape",
    "find_root",
    "get_node",
    "get_operand",
    "list_nodes",
    "list_operands",
    "load_tree",
    "nodes_by_depth_desc",
    "operand_spec",
    "parse_condition",
    "replace_criteria",
    "replacement_plan",
    "validate_node_patch",
    "validate_operand_patch",
]
