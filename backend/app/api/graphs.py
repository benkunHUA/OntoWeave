from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Response
from sqlalchemy.orm import Session

from app.deps import get_default_user_id, get_session
from app.schemas.graph import (
    GraphCreate,
    GraphRead,
    ObjectMembership,
    RelationMembership,
)
from app.services import graph

router = APIRouter(tags=["graphs"])


@router.post("/graphs", response_model=GraphRead, status_code=201)
def create_graph(payload: GraphCreate, session: Session = Depends(get_session)) -> GraphRead:
    g = graph.create_graph(session, owner_id=uuid.UUID(get_default_user_id()), name=payload.name)
    return GraphRead.model_validate(g)


@router.post("/graphs/{graph_id}/objects", status_code=204, response_class=Response)
def add_object(
    graph_id: uuid.UUID,
    payload: ObjectMembership,
    session: Session = Depends(get_session),
) -> Response:
    graph.add_object_to_graph(session, graph_id, payload.object_type_id)
    return Response(status_code=204)


@router.post("/graphs/{graph_id}/relations", status_code=204, response_class=Response)
def add_relation(
    graph_id: uuid.UUID,
    payload: RelationMembership,
    session: Session = Depends(get_session),
) -> Response:
    graph.add_relation_to_graph(session, graph_id, payload.relation_id)
    return Response(status_code=204)
