from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Response
from sqlalchemy.orm import Session

from app.deps import get_current_user_id, get_session
from app.schemas.graph import (
    GraphActionBriefRead,
    GraphActionInvolvesRead,
    GraphCreate,
    GraphNodeRead,
    GraphPropertyRead,
    GraphRead,
    GraphRelationRead,
    GraphSnapshotRead,
    ObjectMembership,
    RelationMembership,
)
from app.services import action_brief, graph

router = APIRouter(tags=["graphs"])


@router.get("/graphs/{graph_id}/snapshot", response_model=GraphSnapshotRead)
def get_graph_snapshot(
    graph_id: uuid.UUID, session: Session = Depends(get_session)
) -> GraphSnapshotRead:
    snapshot = graph.get_graph_snapshot(session, graph_id, owner_id=get_current_user_id())
    return GraphSnapshotRead(
        graph=GraphRead.model_validate(snapshot.graph),
        nodes=[
            GraphNodeRead(
                id=node.id,
                name=node.name,
                name_en=node.name_en,
                description=node.description,
                status=node.status,
                properties=[
                    GraphPropertyRead.model_validate(prop)
                    for prop in snapshot.properties.get(node.id, [])
                ],
            )
            for node in snapshot.nodes
        ],
        relations=[GraphRelationRead.model_validate(relation) for relation in snapshot.relations],
        actions=[_action_brief_read(brief) for brief in snapshot.actions],
    )


def _action_brief_read(brief: action_brief.ActionBrief) -> GraphActionBriefRead:
    return GraphActionBriefRead(
        id=brief.action.id,
        name=brief.action.name,
        description=brief.action.description,
        status=brief.action.status,
        involves=[
            GraphActionInvolvesRead(kind=involves.kind, id=involves.id)
            for involves in brief.involves
        ],
    )


@router.post("/graphs", response_model=GraphRead, status_code=201)
def create_graph(payload: GraphCreate, session: Session = Depends(get_session)) -> GraphRead:
    g = graph.create_graph(session, owner_id=get_current_user_id(), name=payload.name)
    return GraphRead.model_validate(g)


@router.post("/graphs/{graph_id}/objects", status_code=204, response_class=Response)
def add_object(
    graph_id: uuid.UUID,
    payload: ObjectMembership,
    session: Session = Depends(get_session),
) -> Response:
    graph.add_object_to_graph(
        session, graph_id, payload.object_type_id, owner_id=get_current_user_id()
    )
    return Response(status_code=204)


@router.post("/graphs/{graph_id}/relations", status_code=204, response_class=Response)
def add_relation(
    graph_id: uuid.UUID,
    payload: RelationMembership,
    session: Session = Depends(get_session),
) -> Response:
    graph.add_relation_to_graph(
        session, graph_id, payload.relation_id, owner_id=get_current_user_id()
    )
    return Response(status_code=204)
