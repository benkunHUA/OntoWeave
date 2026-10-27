"""本体元素种类注册表。"""

from __future__ import annotations

import app.services.element_kinds.ownership as ownership
import app.services.element_kinds.validators as validators
from app.models import (
    ActionCriteriaNode,
    ActionCriteriaOperand,
    ActionParameter,
    ActionRule,
    ActionRuleAssignment,
    ActionType,
    ObjectType,
    Property,
    Relation,
)
from app.services.element_kinds.types import (
    ACTION_CRITERIA_NODE,
    ACTION_CRITERIA_OPERAND,
    ACTION_PARAMETER,
    ACTION_RULE,
    ACTION_RULE_ASSIGNMENT,
    ACTION_TYPE,
    OBJECT_TYPE,
    PROPERTY,
    RELATION,
    ElementKind,
)

# delete_phase 是一条全局阶梯，不是各元素自己挑一个数。当前占用的档位：
#
#   0  动作的赋值 / 条件操作数（挂在规则、条件节点下面，最先删）
#   1  动作的规则 / 条件节点
#   2  动作的参数
#   3  属性 / 关系（外键引用对方，删对方前先走它们）
#   4  对象类型 / 动作类型（被上面所有东西引用，最后删）
#
# 同一档位里若出现自引用（如条件节点的 parent_id），还要按树深度倒序删——子先于父，
# 否则外键会挡住父节点的删除。顺序由登记项的 `order_for_delete` 提供。
REGISTRY: dict[str, ElementKind] = {
    OBJECT_TYPE: ElementKind(
        model=ObjectType,
        restorable_fields=frozenset({"name", "name_en", "description"}),
        delete_phase=4,
        load_owner_id=ownership.owner_id_attribute,
        promote=ownership.promote_object_type,
    ),
    PROPERTY: ElementKind(
        model=Property,
        restorable_fields=frozenset(
            {
                "name",
                "name_en",
                "datatype",
                "required",
                "is_array",
                "is_primary_key",
                "is_title_key",
                "enum_values",
            }
        ),
        delete_phase=3,
        load_owner_id=ownership.property_owner_id,
        promote=ownership.promote_property,
        validate_patch=validators.validate_property_patch,
    ),
    RELATION: ElementKind(
        model=Relation,
        restorable_fields=frozenset(
            {
                "name",
                "direction",
                "cardinality",
                "forward_label",
                "reverse_label",
                "foreign_key_property_id",
            }
        ),
        delete_phase=3,
        load_owner_id=ownership.owner_id_attribute,
        promote=ownership.promote_relation,
        validate_patch=validators.validate_relation_patch,
    ),
    ACTION_TYPE: ElementKind(
        model=ActionType,
        restorable_fields=frozenset({"name", "description"}),
        delete_phase=4,
        load_owner_id=ownership.owner_id_attribute,
        promote=ownership.promote_action_type,
        dependents=ownership.action_type_dependents,
    ),
    ACTION_PARAMETER: ElementKind(
        model=ActionParameter,
        restorable_fields=frozenset(
            {
                "name",
                "description",
                "kind",
                "datatype",
                "target_object_type_id",
                "is_array",
                "is_required",
                "default_value",
                "enum_values",
                "order_index",
            }
        ),
        delete_phase=2,
        load_owner_id=ownership.action_type_owner_id,
        promote=ownership.promote_plain(
            ACTION_PARAMETER, ActionParameter, ownership.action_type_owner_id
        ),
        validate_patch=validators.validate_action_parameter_patch,
    ),
    ACTION_RULE: ElementKind(
        model=ActionRule,
        restorable_fields=frozenset(
            {
                "rule_kind",
                "target_object_type_id",
                "target_relation_id",
                "source_parameter_id",
                "target_parameter_id",
                "order_index",
            }
        ),
        delete_phase=1,
        load_owner_id=ownership.action_type_owner_id,
        promote=ownership.promote_plain(ACTION_RULE, ActionRule, ownership.action_type_owner_id),
        validate_patch=validators.validate_action_rule_patch,
    ),
    ACTION_RULE_ASSIGNMENT: ElementKind(
        model=ActionRuleAssignment,
        restorable_fields=frozenset(
            {
                "target_property_id",
                "value_source",
                "value_parameter_id",
                "object_parameter_id",
                "object_property_id",
                "static_value",
            }
        ),
        delete_phase=0,
        load_owner_id=ownership.assignment_owner_id,
        promote=ownership.promote_plain(
            ACTION_RULE_ASSIGNMENT, ActionRuleAssignment, ownership.assignment_owner_id
        ),
        validate_patch=validators.validate_assignment_patch,
    ),
    ACTION_CRITERIA_NODE: ElementKind(
        model=ActionCriteriaNode,
        restorable_fields=frozenset(
            {"parent_id", "order_index", "node_kind", "logic", "operator", "failure_message"}
        ),
        delete_phase=1,
        load_owner_id=ownership.action_type_owner_id,
        promote=ownership.promote_plain(
            ACTION_CRITERIA_NODE, ActionCriteriaNode, ownership.action_type_owner_id
        ),
        validate_patch=validators.validate_criteria_node_patch,
        order_for_delete=ownership.order_criteria_nodes_by_depth,
    ),
    ACTION_CRITERIA_OPERAND: ElementKind(
        model=ActionCriteriaOperand,
        restorable_fields=frozenset(
            {
                "side",
                "kind",
                "value_parameter_id",
                "object_parameter_id",
                "object_property_id",
                "static_value",
            }
        ),
        delete_phase=0,
        load_owner_id=ownership.operand_owner_id,
        promote=ownership.promote_plain(
            ACTION_CRITERIA_OPERAND, ActionCriteriaOperand, ownership.operand_owner_id
        ),
        validate_patch=validators.validate_criteria_operand_patch,
    ),
}


def get(kind: str) -> ElementKind:
    """按种类取登记项；未登记就抛，报错统一在调用点转成领域错误。"""
    entry = REGISTRY.get(kind)
    if entry is None:
        raise KeyError(kind)
    return entry
