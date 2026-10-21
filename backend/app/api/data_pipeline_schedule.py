"""数据流程调度的浏览器路由。"""

from __future__ import annotations

import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, Header, Query

from app.deps import get_current_user_id, get_data_service_client
from app.schemas.data_pipeline import SchedulePutRequest
from app.schemas.data_pipeline_execution import ScheduleRead, ScheduleRunRead
from app.services import data_pipeline_schedule
from app.services.data_service.client import DataServiceClient

router = APIRouter(tags=["data-pipeline-schedule"])


@router.get("/pipelines/{pipeline_id}/schedule", response_model=ScheduleRead)
def get_schedule(
    pipeline_id: uuid.UUID,
    client: DataServiceClient = Depends(get_data_service_client),
) -> ScheduleRead:
    return ScheduleRead.model_validate(
        data_pipeline_schedule.get_schedule(
            client, owner_id=get_current_user_id(), pipeline_id=pipeline_id
        )
    )


@router.put("/pipelines/{pipeline_id}/schedule", response_model=ScheduleRead)
def put_schedule(
    pipeline_id: uuid.UUID,
    payload: SchedulePutRequest,
    operation_id: Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")] = None,
    client: DataServiceClient = Depends(get_data_service_client),
) -> ScheduleRead:
    return ScheduleRead.model_validate(
        data_pipeline_schedule.put_schedule(
            client,
            owner_id=get_current_user_id(),
            pipeline_id=pipeline_id,
            request=payload,
            operation_id=operation_id,
        )
    )


@router.post("/pipelines/{pipeline_id}/schedule/pause", response_model=ScheduleRead)
def pause_schedule(
    pipeline_id: uuid.UUID,
    operation_id: Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")] = None,
    client: DataServiceClient = Depends(get_data_service_client),
) -> ScheduleRead:
    return ScheduleRead.model_validate(
        data_pipeline_schedule.pause_schedule(
            client,
            owner_id=get_current_user_id(),
            pipeline_id=pipeline_id,
            operation_id=operation_id,
        )
    )


@router.post("/pipelines/{pipeline_id}/schedule/resume", response_model=ScheduleRead)
def resume_schedule(
    pipeline_id: uuid.UUID,
    operation_id: Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")] = None,
    client: DataServiceClient = Depends(get_data_service_client),
) -> ScheduleRead:
    return ScheduleRead.model_validate(
        data_pipeline_schedule.resume_schedule(
            client,
            owner_id=get_current_user_id(),
            pipeline_id=pipeline_id,
            operation_id=operation_id,
        )
    )


@router.get(
    "/pipelines/{pipeline_id}/schedule/runs",
    response_model=list[ScheduleRunRead],
)
def schedule_runs(
    pipeline_id: uuid.UUID,
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
    client: DataServiceClient = Depends(get_data_service_client),
) -> list[ScheduleRunRead]:
    return [
        ScheduleRunRead.model_validate(item)
        for item in data_pipeline_schedule.schedule_runs(
            client,
            owner_id=get_current_user_id(),
            pipeline_id=pipeline_id,
            limit=limit,
            offset=offset,
        )
    ]
