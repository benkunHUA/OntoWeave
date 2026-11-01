"""行动参数归一、默认值、类型与对象引用校验。"""

from __future__ import annotations

import uuid
from collections.abc import Mapping
from dataclasses import dataclass
from typing import Any

from app.errors import InvariantViolationError, NotFoundError
from app.services import value_types
from app.services.action.detail import ActionDetail
from app.services.action.execution.errors import ActionExecutionError
from app.vocabulary import ActionParameterKind


@dataclass(frozen=True)
class ObjectReference:
    object_type_id: uuid.UUID
    primary_key: str


@dataclass(frozen=True)
class ResolvedParameters:
    values: dict[uuid.UUID, Any]
    objects: dict[uuid.UUID, ObjectReference | None]
    normalized: dict[str, dict[str, Any]]


def resolve_parameters(
    detail: ActionDetail,
    raw: Mapping[str, Any] | None,
    *,
    owner_id: uuid.UUID,
    client: Any,
) -> ResolvedParameters:
    supplied = _normalize_input(raw)
    known = {item.id: item for item in detail.parameters}
    unknown = set(supplied) - set(known)
    if unknown:
        raise ActionExecutionError(
            "UNKNOWN_PARAMETER",
            "action received unknown parameters",
            details={"parameter_ids": [str(item) for item in sorted(unknown)]},
        )

    values: dict[uuid.UUID, Any] = {}
    objects: dict[uuid.UUID, ObjectReference | None] = {}
    normalized_values: dict[str, Any] = {}
    normalized_objects: dict[str, Any] = {}
    for parameter in detail.parameters:
        has_value = parameter.id in supplied
        value = supplied.get(parameter.id) if has_value else parameter.default_value
        if parameter.is_array and has_value:
            raise ActionExecutionError(
                "UNSUPPORTED_ARRAY",
                f"parameter {parameter.name!r} is an array; arrays are not supported yet",
                details={"parameter_id": str(parameter.id), "parameter_name": parameter.name},
            )
        if not has_value and parameter.default_value is None and parameter.is_required:
            raise ActionExecutionError(
                "MISSING_PARAMETER",
                f"missing required parameter {parameter.name!r}",
                details={
                    "parameter_id": str(parameter.id),
                    "parameter_name": parameter.name,
                },
            )
        if parameter.kind == ActionParameterKind.OBJECT_REF.value:
            resolved = _resolve_object(
                parameter,
                value,
                owner_id=owner_id,
                client=client,
                required=parameter.is_required,
                has_value=has_value,
            )
            objects[parameter.id] = resolved
            normalized_objects[str(parameter.id)] = (
                None
                if resolved is None
                else {
                    "object_type_id": str(resolved.object_type_id),
                    "primary_key": resolved.primary_key,
                }
            )
            continue

        _validate_value(parameter, value, has_value=has_value)
        values[parameter.id] = value
        normalized_values[str(parameter.id)] = value

    return ResolvedParameters(
        values=values,
        objects=objects,
        normalized={"values": normalized_values, "objects": normalized_objects},
    )


def _normalize_input(raw: Mapping[str, Any] | None) -> dict[uuid.UUID, Any]:
    if raw is None:
        return {}
    if not isinstance(raw, Mapping):
        raise ActionExecutionError("INVALID_PARAMETER", "parameters must be an object")
    result: dict[uuid.UUID, Any] = {}
    for key, value in raw.items():
        try:
            parameter_id = uuid.UUID(str(key))
        except ValueError as exc:
            raise ActionExecutionError(
                "UNKNOWN_PARAMETER", f"invalid parameter id {key!r}"
            ) from exc
        if parameter_id in result:
            raise ActionExecutionError(
                "UNKNOWN_PARAMETER", f"duplicate parameter id {parameter_id}"
            )
        result[parameter_id] = value
    return result


def _resolve_object(
    parameter: Any,
    value: Any,
    *,
    owner_id: uuid.UUID,
    client: Any,
    required: bool,
    has_value: bool,
) -> ObjectReference | None:
    if value is None:
        if required:
            raise ActionExecutionError(
                "INVALID_PARAMETER",
                f"required object parameter {parameter.name!r} cannot be null",
                details={"parameter_id": str(parameter.id), "parameter_name": parameter.name},
            )
        return None
    if not isinstance(value, Mapping):
        raise _invalid(parameter, "object reference must be an object")
    try:
        object_type_id = uuid.UUID(str(value.get("object_type_id")))
        primary_key = str(value.get("primary_key") or "").strip()
    except ValueError as exc:
        raise _invalid(parameter, "object reference contains an invalid id") from exc
    if not primary_key:
        raise _invalid(parameter, "object reference needs a primary key")
    if object_type_id != parameter.target_object_type_id:
        raise ActionExecutionError(
            "OBJECT_TYPE_MISMATCH",
            f"parameter {parameter.name!r} expects object type {parameter.target_object_type_id}",
            details={"parameter_id": str(parameter.id), "parameter_name": parameter.name},
        )
    try:
        found = client.get_object(
            object_type_id,
            owner_id=owner_id,
            primary_key=primary_key,
        )
    except NotFoundError as exc:
        raise ActionExecutionError(
            "OBJECT_NOT_FOUND",
            f"object {primary_key!r} is not visible",
            details={"parameter_id": str(parameter.id), "primary_key": primary_key},
        ) from exc
    if not found:
        raise ActionExecutionError(
            "OBJECT_NOT_FOUND",
            f"object {primary_key!r} is not visible",
            details={"parameter_id": str(parameter.id), "primary_key": primary_key},
        )
    return ObjectReference(object_type_id=object_type_id, primary_key=primary_key)


def _validate_value(parameter: Any, value: Any, *, has_value: bool) -> None:
    if value is None:
        if parameter.is_required and has_value:
            raise ActionExecutionError(
                "INVALID_PARAMETER",
                f"required parameter {parameter.name!r} cannot be null",
                details={"parameter_id": str(parameter.id), "parameter_name": parameter.name},
            )
        return
    if parameter.datatype is None:
        raise _invalid(parameter, "value parameter has no datatype")
    try:
        value_types.ensure_value_matches(
            parameter.datatype,
            value,
            where=f"parameter {parameter.name!r}",
            enum_values=parameter.enum_values,
        )
    except InvariantViolationError as exc:
        raise _invalid(parameter, str(exc)) from exc


def _invalid(parameter: Any, message: str) -> ActionExecutionError:
    return ActionExecutionError(
        "INVALID_PARAMETER",
        message,
        details={"parameter_id": str(parameter.id), "parameter_name": parameter.name},
    )
