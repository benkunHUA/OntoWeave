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


def list_object_types(session: Session) -> list[ObjectType]:
    return list(session.scalars(select(ObjectType).order_by(ObjectType.name)))


def transition_object_type(
    session: Session, object_type_id: uuid.UUID, target: str
) -> ObjectType:
    obj = get_object_type(session, object_type_id)
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
            select(Property)
            .where(Property.owner_type_id == owner_type_id)
            .order_by(Property.name)
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
    get_object_type(session, source_type_id)
    get_object_type(session, target_type_id)
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
    session: Session, relation_id: uuid.UUID, target: str
) -> Relation:
    rel = get_relation(session, relation_id)
    if target not in ALLOWED_TRANSITIONS.get(rel.status, set()):
        raise InvalidTransitionError(
            f"relation {relation_id} cannot move {rel.status} -> {target}"
        )
    rel.status = target
    session.flush()
    return rel
