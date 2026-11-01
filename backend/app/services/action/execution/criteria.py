"""运行时提交校验求值；条件树不交给 LLM。"""

from __future__ import annotations

import uuid
from typing import Any

from app.services.action.detail import ActionDetail
from app.services.action.execution.errors import ActionExecutionError
from app.services.action.execution.object_values import EffectiveObjectReader
from app.services.action.execution.parameters import ResolvedParameters
from app.services.criteria.store import NodeRead, OperandSpec
from app.vocabulary import ActionCriteriaNodeKind, ActionOperandKind

_UNARY = frozenset({"is null", "is not null"})


def evaluate_criteria(
    detail: ActionDetail,
    parameters: ResolvedParameters,
    *,
    owner_id: uuid.UUID,
    client: Any,
) -> None:
    if not detail.criteria:
        return
    evaluator = _Evaluator(parameters, owner_id=owner_id, client=client)
    _walk(detail.criteria, evaluator)


class _Evaluator:
    def __init__(
        self,
        parameters: ResolvedParameters,
        *,
        owner_id: uuid.UUID,
        client: Any,
    ) -> None:
        self.parameters = parameters
        self.owner_id = owner_id
        self.client = client
        self.objects = EffectiveObjectReader(owner_id=owner_id, client=client)

    def operand_value(self, operand: OperandSpec | None) -> Any:
        if operand is None:
            raise _invalid("condition is missing an operand")
        if operand.kind == ActionOperandKind.PARAMETER.value:
            parameter_id = operand.value_parameter_id
            if parameter_id is None or parameter_id not in self.parameters.values:
                raise _invalid("condition references an unresolved parameter")
            return self.parameters.values[parameter_id]
        if operand.kind == ActionOperandKind.OBJECT_PROPERTY.value:
            object_parameter_id = operand.object_parameter_id
            reference = (
                self.parameters.objects.get(object_parameter_id)
                if object_parameter_id is not None
                else None
            )
            if reference is None:
                raise ActionExecutionError(
                    "OBJECT_NOT_FOUND", "condition object parameter has no visible object"
                )
            if operand.object_property_id is None:
                raise _invalid("object property operand is missing a property id")
            return self.objects.property(reference, operand.object_property_id)
        if operand.kind == ActionOperandKind.STATIC.value:
            return operand.static_value
        raise _invalid(f"unsupported operand kind {operand.kind!r}")


def _walk(nodes: list[NodeRead], evaluator: _Evaluator) -> None:
    for node in nodes:
        if node.node_kind == ActionCriteriaNodeKind.GROUP.value:
            if node.logic not in (None, "and"):
                raise _invalid(f"unsupported criteria logic {node.logic!r}")
            _walk(list(node.children), evaluator)
        elif node.node_kind == ActionCriteriaNodeKind.CONDITION.value:
            _evaluate_condition(node, evaluator)
        else:
            raise _invalid(f"unsupported criteria node kind {node.node_kind!r}")


def _evaluate_condition(node: NodeRead, evaluator: _Evaluator) -> None:
    operator = node.operator
    if operator is None:
        raise _invalid("condition is missing an operator")
    actual = evaluator.operand_value(node.left)
    if operator in _UNARY:
        passed = actual is None if operator == "is null" else actual is not None
        expected = None
    else:
        expected = evaluator.operand_value(node.right)
        passed = _compare(actual, expected, operator)
    if not passed:
        raise ActionExecutionError(
            "CRITERIA_FAILED",
            node.failure_message or "submission criteria failed",
            details={
                "condition_id": str(node.id),
                "failure_message": node.failure_message,
                "actual": actual,
                "expected": expected,
            },
        )


def _compare(actual: Any, expected: Any, operator: str) -> bool:
    if operator == "is":
        return actual == expected
    if operator == "is not":
        return actual != expected
    if actual is None or expected is None:
        raise _invalid(f"operator {operator!r} cannot compare null")
    left, right = _comparable(actual, expected)
    if operator == "<":
        return left < right
    if operator == "<=":
        return left <= right
    if operator == ">":
        return left > right
    if operator == ">=":
        return left >= right
    raise _invalid(f"unsupported criteria operator {operator!r}")


def _comparable(actual: Any, expected: Any) -> tuple[Any, Any]:
    if isinstance(actual, bool) or isinstance(expected, bool):
        raise _invalid("boolean values only support equality")
    if isinstance(actual, (int, float)) and isinstance(expected, (int, float)):
        return actual, expected
    if isinstance(actual, str) and isinstance(expected, str):
        return actual, expected
    raise _invalid("condition compares incompatible value types")


def _invalid(message: str) -> ActionExecutionError:
    return ActionExecutionError("INVALID_CRITERIA", message)
