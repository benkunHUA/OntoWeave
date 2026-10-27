"""同步任务与同步运行接口。"""

from __future__ import annotations

import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, Header, Query
from sqlalchemy.orm import Session

from app.clients.data_service.client import DataServiceClient
from app.deps import get_current_user_id, get_data_service_client, get_session
from app.schemas.data_connection import (
    SyncRunRead,
    SyncRunsRead,
    SyncRunTriggerRequest,
    SyncTasksRead,
    SyncTasksSaveRequest,
)
from app.services import data_connection

router = APIRouter()


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
