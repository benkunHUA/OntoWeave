"""对象类型绑定路由。"""

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
    validation_payload,
)
from app.deps import get_current_user_id, get_session
from app.schemas.ontology import (
    BindingDraftRequest,
    BindingRead,
    GenerateMappingRequest,
    GenerateMappingResponse,
    IndexRun,
    MappingSuggestion,
    MappingValidation,
    ObjectPreview,
    PreviewBindingRequest,
    SuggestBindingRequest,
    ValidateBindingRequest,
)
from app.services import ontology
from app.services.data_service.ontology_client import OntologyDataServiceClient

router = APIRouter()


@router.get("/object-types/{object_type_id}/binding", response_model=BindingRead)
def get_object_binding(
    object_type_id: uuid.UUID,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> BindingRead:
    return BindingRead.model_validate(
        ontology.get_binding(
            session,
            client,
            owner_id=get_current_user_id(),
            subject_kind=ontology.OBJECT_TYPE,
            subject_id=object_type_id,
        )
    )


@router.put("/object-types/{object_type_id}/binding", response_model=BindingRead)
def put_object_binding(
    object_type_id: uuid.UUID,
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
            subject_kind=ontology.OBJECT_TYPE,
            subject_id=object_type_id,
            payload=binding_payload(payload),
            operation_id=operation_id(operation),
        )
    )


@router.delete("/object-types/{object_type_id}/binding", status_code=204)
def delete_object_binding(
    object_type_id: uuid.UUID,
    operation: OperationId = None,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> Response:
    ontology.delete_binding(
        session,
        client,
        owner_id=get_current_user_id(),
        subject_kind=ontology.OBJECT_TYPE,
        subject_id=object_type_id,
        operation_id=operation_id(operation),
    )
    return Response(status_code=204)


@router.post(
    "/object-types/{object_type_id}/binding/validate",
    response_model=MappingValidation,
)
def validate_object_binding(
    object_type_id: uuid.UUID,
    payload: ValidateBindingRequest | None = None,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> MappingValidation:
    return MappingValidation.model_validate(
        ontology.validate_binding(
            session,
            client,
            owner_id=get_current_user_id(),
            subject_kind=ontology.OBJECT_TYPE,
            subject_id=object_type_id,
            payload=validation_payload(payload),
        )
    )


@router.post(
    "/object-types/{object_type_id}/binding/preview",
    response_model=ObjectPreview,
)
def preview_object_binding(
    object_type_id: uuid.UUID,
    payload: PreviewBindingRequest | None = None,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> ObjectPreview:
    result = ontology.preview_binding(
        session,
        client,
        owner_id=get_current_user_id(),
        subject_kind=ontology.OBJECT_TYPE,
        subject_id=object_type_id,
        payload=preview_payload(payload),
    )
    preview = preview_model(result)
    assert isinstance(preview, ObjectPreview)
    return preview


@router.post(
    "/object-types/{object_type_id}/binding/suggest",
    response_model=MappingSuggestion,
)
def suggest_object_binding(
    object_type_id: uuid.UUID,
    payload: SuggestBindingRequest | None = None,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> MappingSuggestion:
    return MappingSuggestion.model_validate(
        ontology.suggest_mapping(
            session,
            client,
            owner_id=get_current_user_id(),
            subject_kind=ontology.OBJECT_TYPE,
            subject_id=object_type_id,
            dataset_kind=payload.dataset_kind.value if payload and payload.dataset_kind else None,
            dataset_id=payload.dataset_id if payload else None,
            current_mapping=mapping_payload(payload.mapping)
            if payload and payload.mapping
            else None,
        )
    )


@router.post(
    "/object-types/{object_type_id}/binding/generate",
    response_model=GenerateMappingResponse,
)
def generate_object_binding(
    object_type_id: uuid.UUID,
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
            subject_kind=ontology.OBJECT_TYPE,
            subject_id=object_type_id,
            payload={"mapping_revision": payload.mapping_revision},
            operation_id=operation_id(operation),
        )
    )


@router.get(
    "/object-types/{object_type_id}/binding/runs",
    response_model=list[IndexRun],
)
def list_object_runs(
    object_type_id: uuid.UUID,
    limit: int = Query(20, ge=1, le=200),
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> list[IndexRun]:
    return runs(
        ontology.list_runs(
            session,
            client,
            owner_id=get_current_user_id(),
            subject_kind=ontology.OBJECT_TYPE,
            subject_id=object_type_id,
            limit=limit,
        )
    )


__all__ = ["router"]
