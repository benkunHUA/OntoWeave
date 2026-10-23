"""资源汇总、工作台会话与数据集路由。"""

from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.api.ontology.deps import get_ontology_client
from app.deps import get_current_user_id, get_session
from app.schemas.conversation import ConversationRead
from app.schemas.ontology import (
    DatasetColumn,
    DatasetKind,
    OntologyDatasetPage,
    OntologyResourcesRead,
)
from app.services import ontology
from app.services.data_service.ontology_client import OntologyDataServiceClient

router = APIRouter()


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


__all__ = ["router"]
