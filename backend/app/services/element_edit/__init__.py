"""本体改动的服务层入口（门面）。

一次编辑 = 一个 op：改已有元素、建新元素、标记删除、整棵替换条件树。操作形状在
`ops`，登记流程在 `apply`，各元素的建法在 `creates`，条件树的替换在 `criteria`。
这里只做重新导出，让接口层 / 工具层 / 测试不必关心内部怎么分文件。
"""

from app.services.element_edit.apply import (
    apply_edits,
    record_update,
    remove_element,
    write_handlers,
)
from app.services.element_edit.creates import (
    create_action_parameter,
    create_action_rule,
    create_action_rule_assignment,
    create_action_type,
    create_object_type,
    create_property,
    create_relation,
    reuse_action_type,
    reuse_object_type,
)
from app.services.element_edit.criteria import replace_action_criteria
from app.services.element_edit.mappers import edits_from_payload
from app.services.element_edit.ops import (
    ACTION_CRITERIA_NODE,
    ACTION_CRITERIA_OPERAND,
    ACTION_PARAMETER,
    ACTION_RULE,
    ACTION_RULE_ASSIGNMENT,
    ACTION_TYPE,
    OBJECT_TYPE,
    PROPERTY,
    RELATION,
    ActionCriteriaReplace,
    ActionParameterCreate,
    ActionRuleAssignmentCreate,
    ActionRuleCreate,
    ActionTypeCreate,
    EditOp,
    ElementDelete,
    ElementUpdate,
    ObjectTypeCreate,
    ObjectTypeReuse,
    PropertyCreate,
    RelationCreate,
)

__all__ = [
    "ACTION_CRITERIA_NODE",
    "ACTION_CRITERIA_OPERAND",
    "ACTION_PARAMETER",
    "ACTION_RULE",
    "ACTION_RULE_ASSIGNMENT",
    "ACTION_TYPE",
    "OBJECT_TYPE",
    "PROPERTY",
    "RELATION",
    "ActionCriteriaReplace",
    "ActionParameterCreate",
    "ActionRuleAssignmentCreate",
    "ActionRuleCreate",
    "ActionTypeCreate",
    "EditOp",
    "ElementDelete",
    "ElementUpdate",
    "ObjectTypeCreate",
    "ObjectTypeReuse",
    "PropertyCreate",
    "RelationCreate",
    "apply_edits",
    "create_action_parameter",
    "create_action_rule",
    "create_action_rule_assignment",
    "create_action_type",
    "create_object_type",
    "create_property",
    "create_relation",
    "edits_from_payload",
    "record_update",
    "remove_element",
    "replace_action_criteria",
    "reuse_action_type",
    "reuse_object_type",
    "write_handlers",
]
