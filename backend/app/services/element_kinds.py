"""本体元素的种类注册表。

可进改动集的元素种类集中登记在这里：种类名、模型、可还原字段、归属解析、确认时的
状态流转、删除顺序、更新校验。changeset 与 element_edit 都从这里取，新增一种元素时
只加一行登记，不再各自维护一套按 kind 的分支。

种类名也只在这里写一次：改动集 op payload 里的 kind、`element_edit.ops` 里的常量、
删除保护的分派，都引这一份。
"""

from __future__ import annotations

import uuid
from collections.abc import Callable, Collection, Sequence
from dataclasses import dataclass
from typing import Any

from sqlalchemy.orm import Session

from app.errors import NotFoundError
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
from app.services import action, action_rules, criteria, object_type, property, relation, status

# 元素种类名。改动集 op 的 payload、编辑操作的常量、删除保护的分派都用这些值。
OBJECT_TYPE = "object_type"
PROPERTY = "property"
RELATION = "relation"
ACTION_TYPE = "action_type"
ACTION_PARAMETER = "action_parameter"
ACTION_RULE = "action_rule"
ACTION_RULE_ASSIGNMENT = "action_rule_assignment"
ACTION_CRITERIA_NODE = "action_criteria_node"
ACTION_CRITERIA_OPERAND = "action_criteria_operand"

# 元素种类给人看的叫法。审批、回执等用户可见文案统一从这里取，避免各处各写一套。
ELEMENT_KIND_LABELS: dict[str, str] = {
    OBJECT_TYPE: "对象类型",
    PROPERTY: "属性",
    RELATION: "关系",
    ACTION_TYPE: "动作",
    ACTION_PARAMETER: "动作参数",
    ACTION_RULE: "动作规则",
    ACTION_RULE_ASSIGNMENT: "字段赋值",
    ACTION_CRITERIA_NODE: "提交校验条件",
    ACTION_CRITERIA_OPERAND: "条件操作数",
}

# 撤销时的字段白名单用 frozenset；归属解析返回元素所属用户；promote 在确认时
# 把草稿翻成已确认；delete_phase 决定删除顺序（小的先删，先子后父）。
#
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


def _owner_id_attribute(session: Session, element: Any) -> uuid.UUID | None:
    """元素自带 owner_id 时直接取（对象类型、关系、动作类型都是共享池元素）。"""
    return getattr(element, "owner_id", None)


def _property_owner_id(session: Session, element: Any) -> uuid.UUID | None:
    """属性的归属看它所属的对象类型。"""
    owner_type = session.get(ObjectType, element.owner_type_id)
    return owner_type.owner_id if owner_type is not None else None


def _action_type_owner_id(session: Session, element: Any) -> uuid.UUID | None:
    """动作子元素的归属看它所属的动作类型。"""
    action_type = session.get(ActionType, element.action_type_id)
    return action_type.owner_id if action_type is not None else None


def _assignment_owner_id(session: Session, element: Any) -> uuid.UUID | None:
    """赋值挂在规则上，归属要顺着规则往上找。"""
    rule = session.get(ActionRule, element.rule_id)
    return _action_type_owner_id(session, rule) if rule is not None else None


def _operand_owner_id(session: Session, element: Any) -> uuid.UUID | None:
    """条件操作数挂在条件节点上，归属再往上找一层。"""
    node = session.get(ActionCriteriaNode, element.node_id)
    return _action_type_owner_id(session, node) if node is not None else None


def _promote_object_type(session: Session, element_id: uuid.UUID, *, owner_id=None) -> None:
    object_type.transition_object_type(session, element_id, status.CONFIRMED, owner_id=owner_id)


def _promote_property(session: Session, element_id: uuid.UUID, *, owner_id=None) -> None:
    property.transition_property(session, element_id, status.CONFIRMED, owner_id=owner_id)


def _promote_relation(session: Session, element_id: uuid.UUID, *, owner_id=None) -> None:
    relation.transition_relation(session, element_id, status.CONFIRMED, owner_id=owner_id)


def _promote_action_type(session: Session, element_id: uuid.UUID, *, owner_id=None) -> None:
    action.transition_action_type(session, element_id, status.CONFIRMED, owner_id=owner_id)


def _promote_plain(
    kind: str, model: type, load_owner_id: Callable[[Session, Any], uuid.UUID | None]
) -> Callable[..., None]:
    """动作层子元素的确认：只翻状态，没有别的副作用。"""

    def promote(session: Session, element_id: uuid.UUID, *, owner_id=None) -> None:
        element = session.get(model, element_id)
        if element is None:
            raise NotFoundError(f"{kind} {element_id} not found")
        if owner_id is not None and load_owner_id(session, element) != owner_id:
            raise NotFoundError(f"{kind} {element_id} not found")
        status.ensure_transition(element.status, status.CONFIRMED, f"{kind} {element_id}")
        element.status = status.CONFIRMED
        session.flush()

    return promote


def _action_type_dependents(
    session: Session, action_type_id: uuid.UUID
) -> Sequence[tuple[str, uuid.UUID]]:
    """删动作类型时要一并删掉、又没法靠数据库级联一次带走的子元素。

    子元素对父元素是 ON DELETE CASCADE，但赋值 / 操作数对参数是 RESTRICT：一条
    DELETE 直接删动作类型会在删参数那一步撞外键。所以子元素逐个列出来，各记一条
    删除 op，由 delete_phase 排出「赋值 → 规则 → 参数 → 动作类型」的顺序。
    """
    dependents: list[tuple[str, uuid.UUID]] = []
    for rule in action_rules.list_rules(session, action_type_id):
        dependents.append((ACTION_RULE, rule.id))
        dependents.extend(
            (ACTION_RULE_ASSIGNMENT, assignment.id)
            for assignment in action_rules.list_assignments(session, rule.id)
        )
    nodes = criteria.list_nodes(session, action_type_id)
    dependents.extend((ACTION_CRITERIA_NODE, node.id) for node in nodes)
    dependents.extend(
        (ACTION_CRITERIA_OPERAND, operand.id)
        for operand in criteria.list_operands(session, [node.id for node in nodes])
    )
    dependents.extend(
        (ACTION_PARAMETER, parameter.id)
        for parameter in action.list_parameters(session, action_type_id)
    )
    return dependents


def _order_criteria_nodes_by_depth(session: Session, elements: Sequence[Any]) -> Sequence[Any]:
    """条件节点是自引用邻接表：同一档位里必须子先父后。"""
    return criteria.nodes_by_depth_desc(elements)


def _validate_property_patch(
    session: Session, element_id: uuid.UUID, patch: dict, exclude_ids: Collection[uuid.UUID]
) -> None:
    property.validate_property_patch(session, property_id=element_id, patch=patch)


def _validate_relation_patch(
    session: Session, element_id: uuid.UUID, patch: dict, exclude_ids: Collection[uuid.UUID]
) -> None:
    relation.validate_relation_patch(session, relation_id=element_id, patch=patch)


def _validate_action_parameter_patch(
    session: Session, element_id: uuid.UUID, patch: dict, exclude_ids: Collection[uuid.UUID]
) -> None:
    action.validate_parameter_patch(
        session, parameter_id=element_id, patch=patch, exclude_ids=exclude_ids
    )


def _validate_action_rule_patch(
    session: Session, element_id: uuid.UUID, patch: dict, exclude_ids: Collection[uuid.UUID]
) -> None:
    action_rules.validate_rule_patch(
        session, rule_id=element_id, patch=patch, exclude_ids=exclude_ids
    )


def _validate_assignment_patch(
    session: Session, element_id: uuid.UUID, patch: dict, exclude_ids: Collection[uuid.UUID]
) -> None:
    action_rules.validate_assignment_patch(
        session, assignment_id=element_id, patch=patch, exclude_ids=exclude_ids
    )


def _validate_criteria_node_patch(
    session: Session, element_id: uuid.UUID, patch: dict, exclude_ids: Collection[uuid.UUID]
) -> None:
    criteria.validate_node_patch(session, node_id=element_id, patch=patch)


def _validate_criteria_operand_patch(
    session: Session, element_id: uuid.UUID, patch: dict, exclude_ids: Collection[uuid.UUID]
) -> None:
    criteria.validate_operand_patch(session, operand_id=element_id, patch=patch)


@dataclass(frozen=True)
class ElementKind:
    model: type
    restorable_fields: frozenset[str]
    delete_phase: int
    load_owner_id: Callable[[Session, Any], uuid.UUID | None]
    promote: Callable[..., None]
    #: 更新校验：第四项是本改动集里已标删、还没落地的同种元素——草稿阶段的删除是延后的，
    #: 它们不该挡住新值（比如删掉「备注」再建一个同名参数）。不需要的种类直接忽略。
    validate_patch: Callable[[Session, uuid.UUID, dict, Collection[uuid.UUID]], None] | None = None
    #: 同一档位内的额外排序（自引用树要子先父后）；不给就按入参顺序。
    order_for_delete: Callable[[Session, Sequence[Any]], Sequence[Any]] | None = None
    #: 删这个元素时还要连带删掉的子元素（数据库级联带不起来的那部分）。
    dependents: Callable[[Session, uuid.UUID], Sequence[tuple[str, uuid.UUID]]] | None = None


REGISTRY: dict[str, ElementKind] = {
    OBJECT_TYPE: ElementKind(
        model=ObjectType,
        restorable_fields=frozenset({"name", "name_en", "description"}),
        delete_phase=4,
        load_owner_id=_owner_id_attribute,
        promote=_promote_object_type,
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
        load_owner_id=_property_owner_id,
        promote=_promote_property,
        validate_patch=_validate_property_patch,
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
        load_owner_id=_owner_id_attribute,
        promote=_promote_relation,
        validate_patch=_validate_relation_patch,
    ),
    ACTION_TYPE: ElementKind(
        model=ActionType,
        restorable_fields=frozenset({"name", "description"}),
        delete_phase=4,
        load_owner_id=_owner_id_attribute,
        promote=_promote_action_type,
        dependents=_action_type_dependents,
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
        load_owner_id=_action_type_owner_id,
        promote=_promote_plain(ACTION_PARAMETER, ActionParameter, _action_type_owner_id),
        validate_patch=_validate_action_parameter_patch,
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
        load_owner_id=_action_type_owner_id,
        promote=_promote_plain(ACTION_RULE, ActionRule, _action_type_owner_id),
        validate_patch=_validate_action_rule_patch,
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
        load_owner_id=_assignment_owner_id,
        promote=_promote_plain(ACTION_RULE_ASSIGNMENT, ActionRuleAssignment, _assignment_owner_id),
        validate_patch=_validate_assignment_patch,
    ),
    ACTION_CRITERIA_NODE: ElementKind(
        model=ActionCriteriaNode,
        restorable_fields=frozenset(
            {"parent_id", "order_index", "node_kind", "logic", "operator", "failure_message"}
        ),
        delete_phase=1,
        load_owner_id=_action_type_owner_id,
        promote=_promote_plain(ACTION_CRITERIA_NODE, ActionCriteriaNode, _action_type_owner_id),
        validate_patch=_validate_criteria_node_patch,
        order_for_delete=_order_criteria_nodes_by_depth,
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
        load_owner_id=_operand_owner_id,
        promote=_promote_plain(ACTION_CRITERIA_OPERAND, ActionCriteriaOperand, _operand_owner_id),
        validate_patch=_validate_criteria_operand_patch,
    ),
}


def get(kind: str) -> ElementKind:
    """按种类取登记项；未登记就抛，报错统一在调用点转成领域错误。"""
    entry = REGISTRY.get(kind)
    if entry is None:
        raise KeyError(kind)
    return entry
