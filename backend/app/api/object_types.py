from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.deps import get_current_user_id, get_session
from app.schemas.ontology import ObjectTypeCreate, ObjectTypeRead
from app.services import ontology

router = APIRouter(tags=["object-types"])


@router.post("/object-types", response_model=ObjectTypeRead, status_code=201)
def create_object_type(
    payload: ObjectTypeCreate, session: Session = Depends(get_session)
) -> ObjectTypeRead:
    obj = ontology.create_object_type(
        session,
        owner_id=get_current_user_id(),
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


@router.get("/object-types", response_model=list[ObjectTypeRead])
def list_object_types(
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    session: Session = Depends(get_session),
) -> list[ObjectTypeRead]:
    objs = ontology.list_object_types(session, limit=limit, offset=offset)
    return [ObjectTypeRead.model_validate(obj) for obj in objs]
