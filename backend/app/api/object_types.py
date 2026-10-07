from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.deps import get_default_user_id, get_session
from app.schemas.ontology import ObjectTypeCreate, ObjectTypeRead
from app.services import ontology

router = APIRouter(tags=["object-types"])


@router.post("/object-types", response_model=ObjectTypeRead, status_code=201)
def create_object_type(
    payload: ObjectTypeCreate, session: Session = Depends(get_session)
) -> ObjectTypeRead:
    obj = ontology.create_object_type(
        session,
        owner_id=uuid.UUID(get_default_user_id()),
        name=payload.name,
        description=payload.description,
    )
    return ObjectTypeRead.model_validate(obj)


@router.post("/object-types/{object_type_id}/confirm", response_model=ObjectTypeRead)
def confirm_object_type(
    object_type_id: uuid.UUID, session: Session = Depends(get_session)
) -> ObjectTypeRead:
    obj = ontology.transition_object_type(session, object_type_id, "confirmed")
    return ObjectTypeRead.model_validate(obj)
