"""把对象类型 / 关系转成检索用的主体查询。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.models import ObjectType, Property, Relation
from app.services.dataset_search.models import Requirement, SubjectQuery
from app.services.ontology.common import primary_key_property, properties, require_subject


def load_subject_query(
    session: Session,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
) -> SubjectQuery:
    kind = require_subject(session, owner_id, subject_kind, subject_id)
    if kind == "object_type":
        obj = session.get(ObjectType, subject_id)
        if obj is None:
            raise ValueError(f"object type {subject_id} not found")
        requirements = tuple(_property_requirements(properties(session, obj.id)))
        return SubjectQuery(
            subject_kind=kind,
            subject_id=obj.id,
            name=obj.name,
            name_en=obj.name_en,
            query_text=_object_text(obj, requirements),
            requirements=requirements,
        )
    relation = session.get(Relation, subject_id)
    if relation is None:
        raise ValueError(f"relation {subject_id} not found")
    source = session.get(ObjectType, relation.source_type_id)
    target = session.get(ObjectType, relation.target_type_id)
    if source is None or target is None:
        raise ValueError("relation endpoints are missing")
    source_key = primary_key_property(session, source.id)
    target_key = primary_key_property(session, target.id)
    requirements = tuple(
        item
        for item in (
            _relation_key_requirement("源主键", source, source_key),
            _relation_key_requirement("目标主键", target, target_key),
        )
        if item is not None
    )
    return SubjectQuery(
        subject_kind=kind,
        subject_id=relation.id,
        name=relation.name,
        name_en=None,
        query_text=_relation_text(relation, source, target, requirements),
        requirements=requirements,
    )


def _property_requirements(rows: list[dict[str, Any]]) -> list[Requirement]:
    return [
        Requirement(
            label=str(item["name"]),
            name=str(item["name"]),
            name_en=item.get("name_en"),
            datatype=str(item["datatype"]),
            required=bool(item["required"]),
            is_array=bool(item["is_array"]),
            is_primary_key=bool(item["is_primary_key"]),
            is_title_key=bool(item["is_title_key"]),
        )
        for item in rows
    ]


def _relation_key_requirement(
    label: str, obj: ObjectType, prop: Property | None
) -> Requirement | None:
    if prop is None:
        return None
    return Requirement(
        label=label,
        name=prop.name,
        name_en=prop.name_en or (f"{obj.name_en}_id" if obj.name_en else None),
        datatype=prop.datatype,
        required=True,
        is_array=prop.is_array,
        is_primary_key=True,
        is_title_key=False,
    )


def _object_text(obj: ObjectType, requirements: tuple[Requirement, ...]) -> str:
    lines = [f"对象：{obj.name}"]
    if obj.name_en:
        lines.append(f"英文名：{obj.name_en}")
    if obj.description:
        lines.append(f"描述：{obj.description}")
    lines.append("字段：")
    for item in requirements:
        flags = [item.datatype]
        if item.is_primary_key:
            flags.append("主键")
        if item.is_title_key:
            flags.append("标题键")
        if item.required:
            flags.append("必填")
        english = f" ({item.name_en})" if item.name_en else ""
        lines.append(f"- {item.label}{english}: {'、'.join(flags)}")
    return "\n".join(lines)


def _relation_text(
    relation: Relation,
    source: ObjectType,
    target: ObjectType,
    requirements: tuple[Requirement, ...],
) -> str:
    lines = [
        f"关系：{source.name} {relation.name} {target.name}",
        f"源对象：{source.name}" + (f" ({source.name_en})" if source.name_en else ""),
        f"目标对象：{target.name}" + (f" ({target.name_en})" if target.name_en else ""),
        "需要的键：",
    ]
    for item in requirements:
        english = f" ({item.name_en})" if item.name_en else ""
        lines.append(f"- {item.label}{english}: {item.datatype}")
    return "\n".join(lines)
