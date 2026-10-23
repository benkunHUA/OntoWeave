"""给 Agent 读取的对象与关系定义。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.models import Property
from app.services.object_type import get_object_type
from app.services.ontology.common import (
    OBJECT_TYPE,
    object_brief,
    properties,
    property_brief,
    relation_brief,
    require_subject,
)
from app.services.relation import get_relation


def get_subject_definition(
    session: Session, *, owner_id: uuid.UUID, subject_kind: str, subject_id: uuid.UUID
) -> dict[str, Any]:
    kind = require_subject(session, owner_id, subject_kind, subject_id)
    if kind == OBJECT_TYPE:
        obj = get_object_type(session, subject_id)
        return {**object_brief(obj), "properties": properties(session, obj.id)}
    relation = get_relation(session, subject_id)
    source = get_object_type(session, relation.source_type_id)
    target = get_object_type(session, relation.target_type_id)
    foreign_key = (
        session.get(Property, relation.foreign_key_property_id)
        if relation.foreign_key_property_id
        else None
    )
    return {
        **relation_brief(relation, source_name=source.name, target_name=target.name),
        "source": {**object_brief(source), "properties": properties(session, source.id)},
        "target": {**object_brief(target), "properties": properties(session, target.id)},
        "foreign_key_property": property_brief(foreign_key) if foreign_key else None,
    }


__all__ = ["get_subject_definition"]
