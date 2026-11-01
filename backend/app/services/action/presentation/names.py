"""一次投影内复用的名称解析缓存。"""

from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.models import ActionParameter, ObjectType, Property, Relation


class Names:
    def __init__(self, session: Session, parameters: list[ActionParameter]) -> None:
        self._session = session
        self._parameters: dict[uuid.UUID, str | None] = {item.id: item.name for item in parameters}
        self._objects: dict[uuid.UUID, str | None] = {}
        self._properties: dict[uuid.UUID, str | None] = {}
        self._relations: dict[uuid.UUID, str | None] = {}

    def object_type_name(self, object_type_id: uuid.UUID | None) -> str | None:
        if object_type_id is None:
            return None
        if object_type_id not in self._objects:
            item = self._session.get(ObjectType, object_type_id)
            self._objects[object_type_id] = item.name if item is not None else None
        return self._objects[object_type_id]

    def property_name(self, property_id: uuid.UUID | None) -> str | None:
        if property_id is None:
            return None
        if property_id not in self._properties:
            item = self._session.get(Property, property_id)
            self._properties[property_id] = item.name if item is not None else None
        return self._properties[property_id]

    def relation_name(self, relation_id: uuid.UUID | None) -> str | None:
        if relation_id is None:
            return None
        if relation_id not in self._relations:
            item = self._session.get(Relation, relation_id)
            self._relations[relation_id] = item.name if item is not None else None
        return self._relations[relation_id]

    def parameter_name(self, parameter_id: uuid.UUID | None) -> str | None:
        if parameter_id is None:
            return None
        if parameter_id in self._parameters:
            return self._parameters[parameter_id]
        item = self._session.get(ActionParameter, parameter_id)
        self._parameters[parameter_id] = item.name if item is not None else None
        return self._parameters[parameter_id]
