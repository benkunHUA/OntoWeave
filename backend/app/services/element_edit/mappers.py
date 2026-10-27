"""HTTP 编辑请求到领域操作对象的显式映射。"""

from __future__ import annotations

from app.schemas.changeset import (
    AddActionParameterEdit,
    AddActionRuleAssignmentEdit,
    AddActionRuleEdit,
    CreateActionTypeEdit,
    CreateObjectTypeEdit,
    CreatePropertyEdit,
    CreateRelationEdit,
    DeleteEdit,
    GraphEditsCreate,
    ReuseObjectTypeEdit,
    SetActionCriteriaEdit,
    UpdateEdit,
)
from app.services.element_edit.ops import (
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


def edits_from_payload(payload: GraphEditsCreate) -> list[EditOp]:
    """把请求里的编辑转成服务层的操作对象。"""
    ops: list[EditOp] = []
    for edit in payload.edits:
        if isinstance(edit, UpdateEdit):
            ops.append(
                ElementUpdate(
                    element_kind=edit.element_kind,
                    element_id=edit.element_id,
                    patch=edit.patch,
                )
            )
        elif isinstance(edit, CreatePropertyEdit):
            ops.append(
                PropertyCreate(
                    owner_type_id=edit.owner_type_id,
                    name=edit.name,
                    name_en=edit.name_en,
                    datatype=edit.datatype,
                    required=edit.required,
                    is_array=edit.is_array,
                    is_primary_key=edit.is_primary_key,
                    is_title_key=edit.is_title_key,
                    enum_values=edit.enum_values,
                )
            )
        elif isinstance(edit, CreateObjectTypeEdit):
            ops.append(
                ObjectTypeCreate(
                    name=edit.name,
                    name_en=edit.name_en,
                    description=edit.description,
                )
            )
        elif isinstance(edit, ReuseObjectTypeEdit):
            ops.append(ObjectTypeReuse(object_type_id=edit.object_type_id))
        elif isinstance(edit, CreateRelationEdit):
            ops.append(
                RelationCreate(
                    source_type_id=edit.source_type_id,
                    target_type_id=edit.target_type_id,
                    name=edit.name,
                    direction=edit.direction,
                    cardinality=edit.cardinality,
                    forward_label=edit.forward_label,
                    reverse_label=edit.reverse_label,
                    foreign_key_property_id=edit.foreign_key_property_id,
                )
            )
        elif isinstance(edit, CreateActionTypeEdit):
            ops.append(ActionTypeCreate(name=edit.name, description=edit.description))
        elif isinstance(edit, AddActionParameterEdit):
            ops.append(
                ActionParameterCreate(
                    action_type_id=edit.action_type_id,
                    name=edit.name,
                    kind=edit.kind,
                    description=edit.description,
                    datatype=edit.datatype,
                    target_object_type_id=edit.target_object_type_id,
                    is_array=edit.is_array,
                    is_required=edit.is_required,
                    default_value=edit.default_value,
                    enum_values=edit.enum_values,
                )
            )
        elif isinstance(edit, AddActionRuleEdit):
            ops.append(
                ActionRuleCreate(
                    action_type_id=edit.action_type_id,
                    rule_kind=edit.rule_kind,
                    target_object_type_id=edit.target_object_type_id,
                    target_relation_id=edit.target_relation_id,
                    source_parameter_id=edit.source_parameter_id,
                    target_parameter_id=edit.target_parameter_id,
                    assignments=tuple(assignment.model_dump() for assignment in edit.assignments),
                )
            )
        elif isinstance(edit, AddActionRuleAssignmentEdit):
            ops.append(
                ActionRuleAssignmentCreate(
                    rule_id=edit.rule_id,
                    target_property_id=edit.target_property_id,
                    value_source=edit.value_source,
                    value_parameter_id=edit.value_parameter_id,
                    object_parameter_id=edit.object_parameter_id,
                    object_property_id=edit.object_property_id,
                    static_value=edit.static_value,
                )
            )
        elif isinstance(edit, SetActionCriteriaEdit):
            ops.append(
                ActionCriteriaReplace(
                    action_type_id=edit.action_type_id,
                    conditions=tuple(condition.model_dump() for condition in edit.conditions),
                )
            )
        elif isinstance(edit, DeleteEdit):
            ops.append(
                ElementDelete(
                    element_kind=edit.element_kind,
                    element_id=edit.element_id,
                )
            )
    return ops
