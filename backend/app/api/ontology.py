"""对象映射工作台的浏览器 BFF。

这里只校验 HTTP 入参、取登录态 owner、调用 service / Java client；映射规则、
本体合并、建议算法都留在服务层。
"""

from __future__ import annotations

import json
import uuid
from collections.abc import Mapping
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Header, Query, Response
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.config import Settings, get_settings
from app.deps import get_current_user_id, get_session
from app.errors import UnprocessableEntityError
from app.schemas.conversation import ConversationRead
from app.schemas.ontology import (
    BindingDraftRequest,
    BindingRead,
    DatasetColumn,
    DatasetKind,
    FilterCondition,
    GenerateMappingRequest,
    GenerateMappingResponse,
    IndexRun,
    MappingSuggestion,
    MappingValidation,
    ObjectPreview,
    ObjectRelationGroupRead,
    ObjectRelationPage,
    OntologyDatasetPage,
    OntologyObjectDetail,
    OntologyObjectPage,
    OntologyResourcesRead,
    PreviewBindingRequest,
    RelationInstancePage,
    RelationOverviewRead,
    RelationPreview,
    SuggestBindingRequest,
    ValidateBindingRequest,
)
from app.services import ontology
from app.services.data_service.ontology_client import OntologyDataServiceClient

router = APIRouter(prefix="/ontology", tags=["ontology"])
OperationId = Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")]


def get_ontology_client(settings: Settings = Depends(get_settings)) -> OntologyDataServiceClient:
    return OntologyDataServiceClient(settings)


def _operation_id(value: uuid.UUID | None) -> uuid.UUID:
    return value or uuid.uuid4()


def _mapping_payload(mapping: BaseModel | Mapping[str, object]) -> dict:
    if isinstance(mapping, BaseModel):
        return mapping.model_dump(mode="json")
    return dict(mapping)


@router.get("/resources", response_model=OntologyResourcesRead)
def list_resources(
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> OntologyResourcesRead:
    return OntologyResourcesRead.model_validate(
        ontology.list_resources(session, client, owner_id=get_current_user_id())
    )


@router.get("/conversations", response_model=ConversationRead)
def ensure_conversation(
    resource_kind: str = Query(),
    resource_id: uuid.UUID = Query(),
    session: Session = Depends(get_session),
) -> ConversationRead:
    return ConversationRead.model_validate(
        ontology.ensure_conversation(
            session,
            owner_id=get_current_user_id(),
            resource_kind=resource_kind,
            resource_id=resource_id,
        )
    )


@router.get("/bindable-datasets", response_model=OntologyDatasetPage)
def list_bindable_datasets(
    keyword: str | None = None,
    page: int = Query(1, ge=1),
    size: int = Query(20, ge=1, le=200),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> OntologyDatasetPage:
    return OntologyDatasetPage.model_validate(
        client.list_bindable_datasets(
            owner_id=get_current_user_id(), keyword=keyword, page=page, size=size
        )
    )


@router.get(
    "/bindable-datasets/{kind}/{dataset_id}/columns",
    response_model=list[DatasetColumn],
)
def list_dataset_columns(
    kind: DatasetKind,
    dataset_id: uuid.UUID,
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> list[DatasetColumn]:
    return [
        DatasetColumn.model_validate(item)
        for item in client.list_dataset_columns(
            owner_id=get_current_user_id(), dataset_kind=kind.value, dataset_id=dataset_id
        )
    ]


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
    operation_id: OperationId = None,
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
            payload=_binding_payload(payload),
            operation_id=_operation_id(operation_id),
        )
    )


@router.delete("/object-types/{object_type_id}/binding", status_code=204)
def delete_object_binding(
    object_type_id: uuid.UUID,
    operation_id: OperationId = None,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> Response:
    ontology.delete_binding(
        session,
        client,
        owner_id=get_current_user_id(),
        subject_kind=ontology.OBJECT_TYPE,
        subject_id=object_type_id,
        operation_id=_operation_id(operation_id),
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
            payload=_validation_payload(payload),
        )
    )


@router.post(
    "/object-types/{object_type_id}/binding/preview",
    response_model=ObjectPreview | RelationPreview,
)
def preview_object_binding(
    object_type_id: uuid.UUID,
    payload: PreviewBindingRequest | None = None,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> ObjectPreview | RelationPreview:
    result = ontology.preview_binding(
        session,
        client,
        owner_id=get_current_user_id(),
        subject_kind=ontology.OBJECT_TYPE,
        subject_id=object_type_id,
        payload=_preview_payload(payload),
    )
    return _preview_model(result)


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
            current_mapping=_mapping_payload(payload.mapping)
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
    operation_id: OperationId = None,
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
            operation_id=_operation_id(operation_id),
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
    return _runs(
        ontology.list_runs(
            session,
            client,
            owner_id=get_current_user_id(),
            subject_kind=ontology.OBJECT_TYPE,
            subject_id=object_type_id,
            limit=limit,
        )
    )


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
    operation_id: OperationId = None,
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
            payload=_binding_payload(payload),
            operation_id=_operation_id(operation_id),
        )
    )


@router.delete("/relation-types/{relation_id}/binding", status_code=204)
def delete_relation_binding(
    relation_id: uuid.UUID,
    operation_id: OperationId = None,
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> Response:
    ontology.delete_binding(
        session,
        client,
        owner_id=get_current_user_id(),
        subject_kind=ontology.RELATION,
        subject_id=relation_id,
        operation_id=_operation_id(operation_id),
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
        payload=_preview_payload(payload),
    )
    return _preview_model(result, relation=True)  # type: ignore[return-value]


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
            current_mapping=_mapping_payload(payload.mapping)
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
    operation_id: OperationId = None,
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
            operation_id=_operation_id(operation_id),
        )
    )


@router.get("/relation-types/{relation_id}/binding/runs", response_model=list[IndexRun])
def list_relation_runs(
    relation_id: uuid.UUID,
    limit: int = Query(20, ge=1, le=200),
    session: Session = Depends(get_session),
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> list[IndexRun]:
    return _runs(
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
    operation_id: OperationId = None,
    client: OntologyDataServiceClient = Depends(get_ontology_client),
) -> dict:
    return ontology.cancel_run(
        client,
        owner_id=get_current_user_id(),
        run_id=run_id,
        operation_id=_operation_id(operation_id),
    )


@router.get(
    "/object-types/{object_type_id}/objects",
    response_model=OntologyObjectPage,
)
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
            filters=_filter_payload(filters),
        )
    )


@router.get(
    "/object-types/{object_type_id}/object",
    response_model=OntologyObjectDetail,
)
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


@router.get(
    "/relation-types/{relation_id}/instances",
    response_model=RelationInstancePage,
)
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


def _binding_payload(payload: BindingDraftRequest) -> dict:
    return {
        "base_revision": payload.base_revision,
        "dataset_kind": payload.dataset_kind.value,
        "dataset_id": str(payload.dataset_id),
        "mapping": _mapping_payload(payload.mapping),
    }


def _validation_payload(payload: ValidateBindingRequest | None) -> dict | None:
    if payload is None:
        return None
    result: dict = {}
    if payload.dataset_kind is not None:
        result["dataset_kind"] = payload.dataset_kind.value
    if payload.dataset_id is not None:
        result["dataset_id"] = str(payload.dataset_id)
    if payload.mapping is not None:
        result["mapping"] = _mapping_payload(payload.mapping)
    return result


def _preview_payload(payload: PreviewBindingRequest | None) -> dict | None:
    result = _validation_payload(payload)
    if payload is not None:
        result = result or {}
        result["limit"] = payload.limit
    return result


def _preview_model(result: dict, *, relation: bool = False) -> ObjectPreview | RelationPreview:
    validation = result.get("validation")
    if not isinstance(validation, dict):
        validation = {
            "blocking": result.get("blocking") or [],
            "warnings": result.get("warnings") or [],
            "valid": result.get("valid", not result.get("blocking")),
        }
    body = {**result, "validation": validation}
    if relation:
        body["pairs"] = result.get("pairs") or result.get("sample") or []
        body["skipped_count"] = result.get("skipped_count", result.get("skipped", 0))
        body["skipped_reasons"] = result.get("skipped_reasons") or {}
    else:
        body["rows"] = result.get("rows") or result.get("sample") or []
    return RelationPreview.model_validate(body) if relation else ObjectPreview.model_validate(body)


def _runs(rows: list[dict]) -> list[IndexRun]:
    return [IndexRun.model_validate(row) for row in rows]


def _filter_payload(raw: str | None) -> list[dict] | None:
    if not raw:
        return None
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise UnprocessableEntityError(
            "filters must be a JSON array", code="FILTER_INVALID"
        ) from exc
    if not isinstance(value, list):
        raise UnprocessableEntityError("filters must be a JSON array", code="FILTER_INVALID")
    try:
        return [FilterCondition.model_validate(item).model_dump(mode="json") for item in value]
    except ValueError as exc:
        raise UnprocessableEntityError(
            "filters contain an invalid condition", code="FILTER_INVALID"
        ) from exc


__all__ = ["get_ontology_client", "router"]
