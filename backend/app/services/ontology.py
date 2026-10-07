from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvalidTransitionError, NotFoundError
from app.models import ObjectType, Property, Relation

DRAFT = "draft"
CONFIRMED = "confirmed"
APPROVED = "approved"

ALLOWED_TRANSITIONS: dict[str, set[str]] = {
    DRAFT: {CONFIRMED},
    CONFIRMED: {APPROVED},
    APPROVED: set(),
}


def create_object_type(
    session: Session,
    *,
    owner_id: uuid.UUID,
    name: str,
    description: str | None = None,
    home_graph_id: uuid.UUID | None = None,
) -> ObjectType:
    obj = ObjectType(
        owner_id=owner_id,
        name=name,
        description=description,
        status=DRAFT,
        home_graph_id=home_graph_id,
    )
    session.add(obj)
    session.flush()
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
    if target not in ALLOWED_TRANSITIONS.get(obj.status, set()):
        raise InvalidTransitionError(
            f"object type {object_type_id} cannot move {obj.status} -> {target}"
        )
    obj.status = target
    session.flush()
    return obj


def create_property(
    session: Session,
    *,
    owner_type_id: uuid.UUID,
    name: str,
    datatype: str = "text",
    required: bool = False,
) -> Property:
    get_object_type(session, owner_type_id)
    prop = Property(
        owner_type_id=owner_type_id,
        name=name,
        datatype=datatype,
        required=required,
        status=DRAFT,
    )
    session.add(prop)
    session.flush()
    return prop


def list_properties(session: Session, owner_type_id: uuid.UUID) -> list[Property]:
    return list(
        session.scalars(
            select(Property).where(Property.owner_type_id == owner_type_id).order_by(Property.name)
        )
    )


def create_relation(
    session: Session,
    *,
    owner_id: uuid.UUID,
    source_type_id: uuid.UUID,
    target_type_id: uuid.UUID,
    name: str,
    direction: str = "forward",
    home_graph_id: uuid.UUID | None = None,
) -> Relation:
    """幂等：同一对类型之间同名同向的关系只建一条，重复调用返回已有的那条。"""
    get_object_type(session, source_type_id)
    get_object_type(session, target_type_id)
    existing = session.scalar(
        select(Relation).where(
            Relation.source_type_id == source_type_id,
            Relation.target_type_id == target_type_id,
            Relation.name == name,
            Relation.direction == direction,
        )
    )
    if existing is not None:
        return existing
    rel = Relation(
        owner_id=owner_id,
        source_type_id=source_type_id,
        target_type_id=target_type_id,
        name=name,
        direction=direction,
        status=DRAFT,
        home_graph_id=home_graph_id,
    )
    session.add(rel)
    session.flush()
    return rel


def get_relation(session: Session, relation_id: uuid.UUID) -> Relation:
    rel = session.get(Relation, relation_id)
    if rel is None:
        raise NotFoundError(f"relation {relation_id} not found")
    return rel


def transition_relation(
    session: Session,
    relation_id: uuid.UUID,
    target: str,
    *,
    owner_id: uuid.UUID | None = None,
) -> Relation:
    rel = get_relation(session, relation_id)
    if owner_id is not None and rel.owner_id != owner_id:
        raise NotFoundError(f"relation {relation_id} not found")
    if target not in ALLOWED_TRANSITIONS.get(rel.status, set()):
        raise InvalidTransitionError(f"relation {relation_id} cannot move {rel.status} -> {target}")
    rel.status = target
    session.flush()
    return rel


def transition_property(
    session: Session,
    property_id: uuid.UUID,
    target: str,
    *,
    owner_id: uuid.UUID | None = None,
) -> Property:
    prop = session.get(Property, property_id)
    if prop is None:
        raise NotFoundError(f"property {property_id} not found")
    if owner_id is not None:
        owner_type = get_object_type(session, prop.owner_type_id)
        if owner_type.owner_id != owner_id:
            raise NotFoundError(f"property {property_id} not found")
    if target not in ALLOWED_TRANSITIONS.get(prop.status, set()):
        raise InvalidTransitionError(
            f"property {property_id} cannot move {prop.status} -> {target}"
        )
    prop.status = target
    session.flush()
    return prop
