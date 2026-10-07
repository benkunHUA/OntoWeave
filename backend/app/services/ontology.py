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
