"""运行记录与对象数据查询路由。"""

from __future__ import annotations

import uuid
from typing import Literal

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.api.ontology.deps import OperationId, get_ontology_client, operation_id
from app.api.ontology.payloads import filter_payload
from app.deps import get_current_user_id, get_session
from app.schemas.ontology import (
    IndexRun,
    ObjectRelationGroupRead,
    ObjectRelationPage,
    OntologyObjectDetail,
    OntologyObjectPage,
    RelationInstancePage,
)
from app.services import ontology
from app.services.data_service.ontology_client import OntologyDataServiceClient

router = APIRouter()


@router.get("/runs/{run_id}", response_model=IndexRun)
def get_run(
    run_id: uuid.UUID,
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> IndexRun:
    return IndexRun.model_validate(
        ontology.get_run(client, owner_id=get_current_user_id(), run_id=run_id)
    )


@router.post("/runs/{run_id}/cancel")
def cancel_run(
    run_id: uuid.UUID,
    operation: OperationId = None,
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> dict:
    return ontology.cancel_run(
        client,
        owner_id=get_current_user_id(),
        run_id=run_id,
        operation_id=operation_id(operation),
    )


@router.get("/object-types/{object_type_id}/objects", response_model=OntologyObjectPage)
def list_objects(
    object_type_id: uuid.UUID,
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=200),
    q: str | None = None,
    filters: str | None = None,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> OntologyObjectPage:
    return OntologyObjectPage.model_validate(
        ontology.list_objects(
            session,
            client,
            owner_id=get_current_user_id(),
            object_type_id=object_type_id,
            page=page,
            page_size=page_size,
            q=q,
            filters=filter_payload(filters),
        )
    )


@router.get("/object-types/{object_type_id}/object", response_model=OntologyObjectDetail)
def get_object(
    object_type_id: uuid.UUID,
    primary_key: str = Query(),
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> OntologyObjectDetail:
    return OntologyObjectDetail.model_validate(
        ontology.get_object(
            session,
            client,
            owner_id=get_current_user_id(),
            object_type_id=object_type_id,
            primary_key=primary_key,
        )
    )


@router.get(
    "/object-types/{object_type_id}/object-relations",
    response_model=list[ObjectRelationGroupRead],
)
def list_object_relation_groups(
    object_type_id: uuid.UUID,
    primary_key: str = Query(),
    direction: Literal["both", "outgoing", "incoming"] = "both",
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> list[ObjectRelationGroupRead]:
    return [
        ObjectRelationGroupRead.model_validate(item)
        for item in ontology.relation_groups(
            session,
            client,
            owner_id=get_current_user_id(),
            object_type_id=object_type_id,
            primary_key=primary_key,
            direction=direction,
        )
    ]


@router.get(
    "/object-types/{object_type_id}/object-relations/{relation_id}",
    response_model=ObjectRelationPage,
)
def list_object_relations(
    object_type_id: uuid.UUID,
    relation_id: uuid.UUID,
    primary_key: str = Query(),
    direction: Literal["outgoing", "incoming"] = "outgoing",
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=200),
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> ObjectRelationPage:
    return ObjectRelationPage.model_validate(
        ontology.related_objects(
            session,
            client,
            owner_id=get_current_user_id(),
            object_type_id=object_type_id,
            relation_id=relation_id,
            primary_key=primary_key,
            direction=direction,
            page=page,
            page_size=page_size,
        )
    )


@router.get("/relation-types/{relation_id}/instances", response_model=RelationInstancePage)
def list_relation_instances(
    relation_id: uuid.UUID,
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=200),
    from_primary_key: str | None = Query(None),
    to_primary_key: str | None = Query(None),
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> RelationInstancePage:
    return RelationInstancePage.model_validate(
        ontology.list_relation_instances(
            session,
            client,
            owner_id=get_current_user_id(),
            relation_id=relation_id,
            page=page,
            page_size=page_size,
            from_primary_key=from_primary_key,
            to_primary_key=to_primary_key,
        )
    )


__all__ = ["router"]
