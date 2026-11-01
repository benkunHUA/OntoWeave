"""把规则赋值和对象参数解析成 planner 可直接使用的值。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.models import ActionParameter, ActionRule, ActionRuleAssignment, Property
from app.services import property as property_service
from app.services.action.detail import ActionDetail
from app.services.action.execution.errors import ActionExecutionError
from app.services.action.execution.object_values import EffectiveObjectReader
from app.services.action.execution.parameters import ObjectReference, ResolvedParameters
from app.vocabulary import ActionParameterKind, ActionValueSource


class RuleValueReader:
    def __init__(
        self,
        session: Session,
        detail: ActionDetail,
        parameters: ResolvedParameters,
        objects: EffectiveObjectReader,
    ) -> None:
        self.session = session
        self.parameters = parameters
        self.objects = objects
        self.parameters_by_id: dict[uuid.UUID, ActionParameter] = {
            item.id: item for item in detail.parameters
        }

    def assignable_property(
        self, assignment: ActionRuleAssignment, object_type_id: uuid.UUID
    ) -> Property:
        target = property_service.get_property(self.session, assignment.target_property_id)
        if target.owner_type_id != object_type_id:
            raise ActionExecutionError(
                "INVALID_ACTION_DEFINITION",
                "assignment target does not belong to the rule's object type",
                details={
                    "assignment_id": str(assignment.id),
                    "property_id": str(target.id),
                },
            )
        if target.is_array:
            raise ActionExecutionError(
                "UNSUPPORTED_ARRAY",
                f"property {target.name!r} is an array; arrays are not supported yet",
                details={
                    "property_id": str(target.id),
                    "property_name": target.name,
                    "assignment_id": str(assignment.id),
                },
            )
        return target

    def value(self, assignment: ActionRuleAssignment) -> Any:
        source = assignment.value_source
        if source == ActionValueSource.PARAMETER.value:
            parameter_id = assignment.value_parameter_id
            if parameter_id is None or parameter_id not in self.parameters.values:
                raise ActionExecutionError(
                    "INVALID_ACTION_DEFINITION",
                    "parameter assignment has no resolved value parameter",
                    details={"assignment_id": str(assignment.id)},
                )
            return self.parameters.values[parameter_id]
        if source == ActionValueSource.OBJECT_PROPERTY.value:
            reference = self._resolved_reference(assignment.object_parameter_id)
            return self.objects.property(reference, assignment.object_property_id)
        if source == ActionValueSource.STATIC.value:
            return assignment.static_value
        raise ActionExecutionError(
            "INVALID_ACTION_DEFINITION",
            f"unsupported assignment source {source!r}",
            details={"assignment_id": str(assignment.id)},
        )

    def object_reference(
        self,
        rule: ActionRule,
        parameter_id: uuid.UUID | None,
        *,
        expected_type_id: uuid.UUID,
        role: str,
    ) -> ObjectReference:
        if parameter_id is None:
            raise _invalid_rule(rule, f"rule is missing its {role} parameter")
        reference = self._resolved_reference(parameter_id)
        if reference.object_type_id != expected_type_id:
            raise ActionExecutionError(
                "OBJECT_TYPE_MISMATCH",
                f"{role} parameter points at another object type",
                details={
                    "parameter_id": str(parameter_id),
                    "object_type_id": str(reference.object_type_id),
                    "expected_object_type_id": str(expected_type_id),
                },
            )
        return reference

    def _resolved_reference(self, parameter_id: uuid.UUID | None) -> ObjectReference:
        parameter = self.parameters_by_id.get(parameter_id) if parameter_id is not None else None
        reference = self.parameters.objects.get(parameter_id) if parameter_id is not None else None
        if parameter is None or reference is None:
            raise ActionExecutionError(
                "MISSING_PARAMETER",
                "object parameter has no resolved object",
                details={
                    "parameter_id": str(parameter_id) if parameter_id is not None else None,
                    "parameter_name": parameter.name if parameter is not None else None,
                },
            )
        if parameter.kind != ActionParameterKind.OBJECT_REF.value:
            raise ActionExecutionError(
                "INVALID_ACTION_DEFINITION",
                "rule endpoint is not an object reference parameter",
                details={"parameter_id": str(parameter.id)},
            )
        return reference


def _invalid_rule(rule: ActionRule, message: str) -> ActionExecutionError:
    return ActionExecutionError(
        "INVALID_ACTION_DEFINITION",
        message,
        details={"rule_id": str(rule.id), "rule_kind": rule.rule_kind},
    )


__all__ = ["RuleValueReader"]
