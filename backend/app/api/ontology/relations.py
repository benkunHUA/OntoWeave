"""多对多关系绑定与概览路由。"""

from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Query, Response
from sqlalchemy.orm import Session

from app.api.ontology.deps import (
    OperationId,
    get_ontology_client,
    mapping_payload,
    operation_id,
)
from app.api.ontology.payloads import (
    binding_payload,
    preview_model,
    preview_payload,
    runs,
)
from app.clients.data_service.ontology_client import OntologyDataServiceClient
from app.deps import get_current_user_id, get_session
from app.schemas.ontology import (
    BindingDraftRequest,
    BindingRead,
    GenerateMappingRequest,
    GenerateMappingResponse,
    IndexRun,
    MappingSuggestion,
    PreviewBindingRequest,
    RelationOverviewRead,
    RelationPreview,
    SuggestBindingRequest,
)
from app.services import ontology

router = APIRouter()


@router.get("/relation-types/{relation_id}/binding", response_model=BindingRead)
def get_relation_binding(
    relation_id: uuid.UUID,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> BindingRead:
    return BindingRead.model_validate(
        ontology.get_binding(
            session,
            client,
            owner_id=get_current_user_id(),
            subject_kind=ontology.RELATION,
            subject_id=relation_id,
        )
    )


@router.put("/relation-types/{relation_id}/binding", response_model=BindingRead)
def put_relation_binding(
    relation_id: uuid.UUID,
    payload: BindingDraftRequest,
    operation: OperationId = None,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> BindingRead:
    return BindingRead.model_validate(
        ontology.save_binding(
            session,
            client,
            owner_id=get_current_user_id(),
            subject_kind=ontology.RELATION,
            subject_id=relation_id,
            payload=binding_payload(payload),
            operation_id=operation_id(operation),
        )
    )


@router.delete("/relation-types/{relation_id}/binding", status_code=204)
def delete_relation_binding(
    relation_id: uuid.UUID,
    operation: OperationId = None,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> Response:
    ontology.delete_binding(
        session,
        client,
        owner_id=get_current_user_id(),
        subject_kind=ontology.RELATION,
        subject_id=relation_id,
        operation_id=operation_id(operation),
    )
    return Response(status_code=204)


@router.post(
    "/relation-types/{relation_id}/binding/preview",
    response_model=RelationPreview,
)
def preview_relation_binding(
    relation_id: uuid.UUID,
    payload: PreviewBindingRequest | None = None,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> RelationPreview:
    result = ontology.preview_binding(
        session,
        client,
        owner_id=get_current_user_id(),
        subject_kind=ontology.RELATION,
        subject_id=relation_id,
        payload=preview_payload(payload),
    )
    preview = preview_model(result, relation=True)
    assert isinstance(preview, RelationPreview)
    return preview


@router.post(
    "/relation-types/{relation_id}/binding/suggest",
    response_model=MappingSuggestion,
)
def suggest_relation_binding(
    relation_id: uuid.UUID,
    payload: SuggestBindingRequest | None = None,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> MappingSuggestion:
    return MappingSuggestion.model_validate(
        ontology.suggest_mapping(
            session,
            client,
            owner_id=get_current_user_id(),
            subject_kind=ontology.RELATION,
            subject_id=relation_id,
            dataset_kind=payload.dataset_kind.value if payload and payload.dataset_kind else None,
            dataset_id=payload.dataset_id if payload else None,
            current_mapping=mapping_payload(payload.mapping)
            if payload and payload.mapping
            else None,
        )
    )


@router.post(
    "/relation-types/{relation_id}/binding/generate",
    response_model=GenerateMappingResponse,
)
def generate_relation_binding(
    relation_id: uuid.UUID,
    payload: GenerateMappingRequest,
    operation: OperationId = None,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> GenerateMappingResponse:
    return GenerateMappingResponse.model_validate(
        ontology.generate_binding(
            session,
            client,
            owner_id=get_current_user_id(),
            subject_kind=ontology.RELATION,
            subject_id=relation_id,
            payload={"mapping_revision": payload.mapping_revision},
            operation_id=operation_id(operation),
        )
    )


@router.get("/relation-types/{relation_id}/binding/runs", response_model=list[IndexRun])
def list_relation_runs(
    relation_id: uuid.UUID,
    limit: int = Query(20, ge=1, le=200),
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> list[IndexRun]:
    return runs(
        ontology.list_runs(
            session,
            client,
            owner_id=get_current_user_id(),
            subject_kind=ontology.RELATION,
            subject_id=relation_id,
            limit=limit,
        )
    )


@router.get(
    "/relation-types/{relation_id}/overview",
    response_model=RelationOverviewRead,
)
def get_relation_overview(
    relation_id: uuid.UUID,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> RelationOverviewRead:
    return RelationOverviewRead.model_validate(
        ontology.get_relation_overview(
            session, client, owner_id=get_current_user_id(), relation_id=relation_id
        )
    )


__all__ = ["router"]
