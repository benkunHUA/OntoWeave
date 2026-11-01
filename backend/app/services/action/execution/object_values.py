"""行动执行期间读取 effective 对象属性。"""

from __future__ import annotations

import uuid
from typing import Any

from app.errors import NotFoundError
from app.services.action.execution.errors import ActionExecutionError
from app.services.action.execution.parameters import ObjectReference


class EffectiveObjectReader:
    """同一执行内缓存对象；条件校验与赋值解析看到的是同一份 effective 值。"""

    def __init__(self, *, owner_id: uuid.UUID, client: Any) -> None:
        self._owner_id = owner_id
        self._client = client
        self._objects: dict[tuple[uuid.UUID, str], dict[str, Any]] = {}

    def get(self, reference: ObjectReference) -> dict[str, Any]:
        key = (reference.object_type_id, reference.primary_key)
        if key not in self._objects:
            try:
                raw = self._client.get_object(
                    reference.object_type_id,
                    owner_id=self._owner_id,
                    primary_key=reference.primary_key,
                )
            except NotFoundError as exc:
                raise ActionExecutionError(
                    "OBJECT_NOT_FOUND",
                    f"object {reference.primary_key!r} is not visible",
                    details={
                        "object_type_id": str(reference.object_type_id),
                        "primary_key": reference.primary_key,
                    },
                ) from exc
            self._objects[key] = raw if isinstance(raw, dict) else {}
        return self._objects[key]

    def property(self, reference: ObjectReference, property_id: uuid.UUID | None) -> Any:
        if property_id is None:
            raise ActionExecutionError(
                "INVALID_ACTION_DEFINITION", "object property operand is missing a property id"
            )
        return effective_properties(self.get(reference)).get(str(property_id))


def effective_properties(raw: dict[str, Any]) -> dict[str, Any]:
    """把 Java 对象详情里的属性（对象或数组）统一成 `属性 ID -> 值`。"""
    value = raw.get("properties")
    if isinstance(value, dict):
        return {
            str(key): item.get("value") if isinstance(item, dict) and "value" in item else item
            for key, item in value.items()
        }
    if not isinstance(value, list):
        return {}
    result: dict[str, Any] = {}
    for item in value:
        if not isinstance(item, dict):
            continue
        attribute_id = item.get("attribute_id") or item.get("attributeId")
        if attribute_id is not None:
            result[str(attribute_id)] = item.get("value")
    return result


__all__ = ["EffectiveObjectReader", "effective_properties"]
