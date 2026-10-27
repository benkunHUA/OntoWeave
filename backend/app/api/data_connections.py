"""数据连接对前端暴露的 REST 边界。"""

from __future__ import annotations

import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, Header, Query
from sqlalchemy.orm import Session

from app.deps import get_current_user_id, get_data_service_client, get_session
from app.schemas.conversation import ConversationRead
from app.schemas.data_connection import (
    ConnectionTestRead,
    DataConnectionCreate,
    DataConnectionPageRead,
    DataConnectionRead,
    DataConnectionStateRead,
    DataConnectionUpdate,
    DatasetRead,
    DatasetVersionRead,
    DatasetVersionsRead,
    SyncRunRead,
    SyncRunsRead,
    SyncRunTriggerRequest,
    SyncTasksRead,
    SyncTasksSaveRequest,
    TableDetailRead,
    TableListRead,
)
from app.schemas.dataset_preview import DatasetPreviewRead
from app.services import data_connection
from app.services.data_service.client import DataServiceClient

router = APIRouter(tags=["data-connections"])


@router.post("/data-connections", response_model=DataConnectionRead, status_code=201)
def create_connection(
    payload: DataConnectionCreate,
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> DataConnectionRead:
    return DataConnectionRead.model_validate(
        data_connection.create_connection(
            session,
            owner_id=get_current_user_id(),
            client=client,
            payload=payload,
        )
    )


@router.get("/data-connections", response_model=DataConnectionPageRead)
def list_connections(
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    client: DataServiceClient = Depends(get_data_service_client),
) -> DataConnectionPageRead:
    return DataConnectionPageRead.model_validate(
        data_connection.list_connections(
            client, owner_id=get_current_user_id(), limit=limit, offset=offset
        )
    )


@router.get("/data-connections/{connection_id}", response_model=DataConnectionRead)
def get_connection(
    connection_id: uuid.UUID,
    client: DataServiceClient = Depends(get_data_service_client),
) -> DataConnectionRead:
    return DataConnectionRead.model_validate(
        data_connection.get_connection(
            client, owner_id=get_current_user_id(), connection_id=connection_id
        )
    )


@router.patch("/data-connections/{connection_id}", response_model=DataConnectionRead)
def update_connection(
    connection_id: uuid.UUID,
    payload: DataConnectionUpdate,
    operation_id: Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")] = None,
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> DataConnectionRead:
    return DataConnectionRead.model_validate(
        data_connection.update_connection(
            session,
            owner_id=get_current_user_id(),
            client=client,
            connection_id=connection_id,
            payload=payload,
            operation_id=operation_id,
        )
    )


@router.post("/data-connections/{connection_id}/test", response_model=ConnectionTestRead)
def test_connection(
    connection_id: uuid.UUID,
    operation_id: Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")] = None,
    client: DataServiceClient = Depends(get_data_service_client),
) -> ConnectionTestRead:
    return ConnectionTestRead.model_validate(
        data_connection.test_connection(
            client,
            owner_id=get_current_user_id(),
            connection_id=connection_id,
            operation_id=operation_id,
        )
    )


@router.get("/data-connections/{connection_id}/tables", response_model=TableListRead)
def list_tables(
    connection_id: uuid.UUID,
    client: DataServiceClient = Depends(get_data_service_client),
) -> TableListRead:
    return TableListRead.model_validate(
        data_connection.list_tables(
            client, owner_id=get_current_user_id(), connection_id=connection_id
        )
    )


@router.get(
    "/data-connections/{connection_id}/tables/{schema}/{table}",
    response_model=TableDetailRead,
)
def describe_table(
    connection_id: uuid.UUID,
    schema: str,
    table: str,
    client: DataServiceClient = Depends(get_data_service_client),
) -> TableDetailRead:
    return TableDetailRead.model_validate(
        data_connection.describe_table(
            client,
            owner_id=get_current_user_id(),
            connection_id=connection_id,
            schema=schema,
            table=table,
        )
    )


@router.get("/data-connections/{connection_id}/state", response_model=DataConnectionStateRead)
def get_state(
    connection_id: uuid.UUID,
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> DataConnectionStateRead:
    return DataConnectionStateRead.model_validate(
        data_connection.get_state(
            session, client, owner_id=get_current_user_id(), connection_id=connection_id
        )
    )


@router.post(
    "/data-connections/{connection_id}/conversation",
    response_model=ConversationRead,
    status_code=201,
)
def ensure_conversation(
    connection_id: uuid.UUID,
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> ConversationRead:
    return ConversationRead.model_validate(
        data_connection.ensure_conversation(
            session,
            client,
            owner_id=get_current_user_id(),
            connection_id=connection_id,
        )
    )


@router.post(
    "/data-connections/{connection_id}/sync-tasks",
    response_model=SyncTasksRead,
)
def save_sync_tasks(
    connection_id: uuid.UUID,
    payload: SyncTasksSaveRequest,
    operation_id: Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")] = None,
    client: DataServiceClient = Depends(get_data_service_client),
) -> SyncTasksRead:
    return SyncTasksRead.model_validate(
        data_connection.save_sync_tasks(
            client,
            owner_id=get_current_user_id(),
            connection_id=connection_id,
            payload=payload,
            operation_id=operation_id,
        )
    )


@router.get(
    "/data-connections/{connection_id}/sync-tasks",
    response_model=SyncTasksRead,
)
def list_sync_tasks(
    connection_id: uuid.UUID,
    client: DataServiceClient = Depends(get_data_service_client),
) -> SyncTasksRead:
    return SyncTasksRead.model_validate(
        data_connection.list_sync_tasks(
            client, owner_id=get_current_user_id(), connection_id=connection_id
        )
    )


@router.post(
    "/data-connections/{connection_id}/sync-runs",
    response_model=SyncRunsRead,
    status_code=202,
)
def trigger_sync(
    connection_id: uuid.UUID,
    payload: SyncRunTriggerRequest,
    client: DataServiceClient = Depends(get_data_service_client),
) -> SyncRunsRead:
    return SyncRunsRead.model_validate(
        data_connection.trigger_sync(
            client,
            owner_id=get_current_user_id(),
            connection_id=connection_id,
            payload=payload,
        )
    )


@router.get(
    "/data-connections/{connection_id}/sync-runs",
    response_model=SyncRunsRead,
)
def recent_sync_runs(
    connection_id: uuid.UUID,
    limit: int = Query(20, ge=1, le=20),
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> SyncRunsRead:
    return SyncRunsRead.model_validate(
        data_connection.recent_runs(
            session,
            client,
            owner_id=get_current_user_id(),
            connection_id=connection_id,
            limit=limit,
        )
    )


@router.get("/sync-runs/{run_id}", response_model=SyncRunRead)
def get_sync_run(
    run_id: uuid.UUID,
    client: DataServiceClient = Depends(get_data_service_client),
) -> SyncRunRead:
    return SyncRunRead.model_validate(
        data_connection.get_sync_run(client, owner_id=get_current_user_id(), run_id=run_id)
    )


@router.post("/sync-runs/{run_id}/stop", response_model=SyncRunRead, status_code=202)
def stop_sync_run(
    run_id: uuid.UUID,
    operation_id: Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")] = None,
    client: DataServiceClient = Depends(get_data_service_client),
) -> SyncRunRead:
    return SyncRunRead.model_validate(
        data_connection.stop_sync_run(
            client,
            owner_id=get_current_user_id(),
            run_id=run_id,
            operation_id=operation_id,
        )
    )


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
