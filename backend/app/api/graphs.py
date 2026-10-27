from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Response
from sqlalchemy.orm import Session

from app.config import get_settings
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
from app.schemas.graph_publication import GraphPublicationRead
from app.services import embedding, graph
from app.services.action import brief as action_brief
from app.services.graph import publication as graph_publication

router = APIRouter(tags=["graphs"])


@router.post("/graphs/{graph_id}/publish", response_model=GraphPublicationRead)
def publish_graph(
    graph_id: uuid.UUID, session: Session = Depends(get_session)
) -> GraphPublicationRead:
    publication = graph_publication.publish_graph(
        session,
        graph_id=graph_id,
        owner_id=get_current_user_id(),
        embedder=embedding.build_embedder(get_settings()),
    )
    return GraphPublicationRead(
        graph_id=publication.graph_id,
        graph_card=publication.graph_card,
        embedding_model=publication.embedding_model,
        published_at=publication.published_at,
    )


@router.get("/graphs/{graph_id}/publication", response_model=GraphPublicationRead)
def get_graph_publication(
    graph_id: uuid.UUID, session: Session = Depends(get_session)
) -> GraphPublicationRead:
    publication = graph_publication.get_publication(
        session, graph_id=graph_id, owner_id=get_current_user_id()
    )
    return GraphPublicationRead(
        graph_id=publication.graph_id,
        graph_card=publication.graph_card,
        embedding_model=publication.embedding_model,
        published_at=publication.published_at,
    )


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
