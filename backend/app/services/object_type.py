"""对象类型的读写与状态流转。共享池实体，跨图复用。"""

from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import ObjectType
from app.services import english_names, status


def create_object_type(
    session: Session,
    *,
    owner_id: uuid.UUID,
    name: str,
    name_en: str | None = None,
    description: str | None = None,
    home_graph_id: uuid.UUID | None = None,
    name_generator: english_names.EnglishNameGenerator | None = None,
) -> ObjectType:
    obj = ObjectType(
        owner_id=owner_id,
        name=name,
        name_en=name_en,
        description=description,
        status=status.DRAFT,
        home_graph_id=home_graph_id,
    )
    session.add(obj)
    session.flush()
    english_names.fill_object_english_names(session, object_type=obj, generator=name_generator)
    return obj


def get_object_type(session: Session, object_type_id: uuid.UUID) -> ObjectType:
    obj = session.get(ObjectType, object_type_id)
    if obj is None:
        raise NotFoundError(f"object type {object_type_id} not found")
    return obj


def list_object_types(
    session: Session,
    *,
    owner_id: uuid.UUID | None = None,
    limit: int = 50,
    offset: int = 0,
) -> list[ObjectType]:
    """按名称排序分页。加上 id 作次级排序键，保证翻页时顺序稳定。"""
    stmt = select(ObjectType)
    if owner_id is not None:
        stmt = stmt.where(ObjectType.owner_id == owner_id)
    return list(
        session.scalars(stmt.order_by(ObjectType.name, ObjectType.id).limit(limit).offset(offset))
    )


def transition_object_type(
    session: Session,
    object_type_id: uuid.UUID,
    target: str,
    *,
    owner_id: uuid.UUID | None = None,
) -> ObjectType:
    obj = get_object_type(session, object_type_id)
    if owner_id is not None and obj.owner_id != owner_id:
        raise NotFoundError(f"object type {object_type_id} not found")
    status.ensure_transition(obj.status, target, f"object type {object_type_id}")
    obj.status = target
    session.flush()
    return obj
