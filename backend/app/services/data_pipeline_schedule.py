"""数据流程调度的 BFF 转发与 snake_case 映射。"""

from __future__ import annotations

import uuid

from app.schemas.data_pipeline import SchedulePutRequest
from app.services.data_service import mapping
from app.services.data_service.client import DataServiceClient


def get_schedule(client: DataServiceClient, *, owner_id: uuid.UUID, pipeline_id: uuid.UUID) -> dict:
    return mapping.schedule(client.get_schedule(pipeline_id, owner_id=owner_id))


def put_schedule(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    request: SchedulePutRequest,
    operation_id: uuid.UUID | None = None,
) -> dict:
    return mapping.schedule(
        client.put_schedule(
            pipeline_id,
            owner_id=owner_id,
            payload={
                "name": request.name,
                "cronExpression": request.cron_expression,
                "timezone": request.timezone,
            },
            operation_id=operation_id,
        )
    )


def pause_schedule(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    operation_id: uuid.UUID | None = None,
) -> dict:
    return mapping.schedule(
        client.pause_schedule(pipeline_id, owner_id=owner_id, operation_id=operation_id)
    )


def resume_schedule(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    operation_id: uuid.UUID | None = None,
) -> dict:
    return mapping.schedule(
        client.resume_schedule(pipeline_id, owner_id=owner_id, operation_id=operation_id)
    )


def schedule_runs(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    limit: int,
    offset: int,
) -> list[dict]:
    rows = client.list_schedule_runs(pipeline_id, owner_id=owner_id, limit=limit, offset=offset)
    return [mapping.schedule_run(item) for item in rows]
