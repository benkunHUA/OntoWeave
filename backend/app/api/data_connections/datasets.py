"""数据集、版本与预览接口。"""

from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.clients.data_service.client import DataServiceClient
from app.deps import get_current_user_id, get_data_service_client, get_session
from app.schemas.data_connection import DatasetRead, DatasetVersionRead, DatasetVersionsRead
from app.schemas.dataset_preview import DatasetPreviewRead
from app.services import data_connection

router = APIRouter()


@router.get(
    "/data-connections/{connection_id}/datasets",
    response_model=list[DatasetRead],
)
def list_datasets(
    connection_id: uuid.UUID,
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> list[DatasetRead]:
    return [
        DatasetRead.model_validate(item)
        for item in data_connection.list_datasets(
            session,
            client,
            owner_id=get_current_user_id(),
            connection_id=connection_id,
        )
    ]


@router.get("/datasets/{dataset_id}", response_model=DatasetRead)
def get_dataset(
    dataset_id: uuid.UUID,
    client: DataServiceClient = Depends(get_data_service_client),
) -> DatasetRead:
    return DatasetRead.model_validate(
        data_connection.get_dataset(client, owner_id=get_current_user_id(), dataset_id=dataset_id)
    )


@router.get("/datasets/{dataset_id}/versions", response_model=DatasetVersionsRead)
def get_dataset_versions(
    dataset_id: uuid.UUID,
    limit: int = Query(20, ge=1, le=200),
    offset: int = Query(0, ge=0),
    client: DataServiceClient = Depends(get_data_service_client),
) -> DatasetVersionsRead:
    return DatasetVersionsRead.model_validate(
        data_connection.get_dataset_versions(
            client,
            owner_id=get_current_user_id(),
            dataset_id=dataset_id,
            limit=limit,
            offset=offset,
        )
    )


@router.get("/datasets/{dataset_id}/versions/{version_id}", response_model=DatasetVersionRead)
def get_dataset_version(
    dataset_id: uuid.UUID,
    version_id: uuid.UUID,
    client: DataServiceClient = Depends(get_data_service_client),
) -> DatasetVersionRead:
    return DatasetVersionRead.model_validate(
        data_connection.get_dataset_version(
            client,
            owner_id=get_current_user_id(),
            dataset_id=dataset_id,
            version_id=version_id,
        )
    )


@router.get("/datasets/{dataset_id}/preview", response_model=DatasetPreviewRead)
def get_dataset_preview(
    dataset_id: uuid.UUID,
    version_id: uuid.UUID | None = None,
    limit: int = Query(100, ge=1, le=500),
    live: bool = False,
    client: DataServiceClient = Depends(get_data_service_client),
) -> DatasetPreviewRead:
    return DatasetPreviewRead.model_validate(
        data_connection.get_dataset_preview(
            client,
            owner_id=get_current_user_id(),
            dataset_id=dataset_id,
            version_id=version_id,
            limit=limit,
            live=live,
        )
    )
